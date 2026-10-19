package com.jobtrack.offer;

import com.jobtrack.common.BadRequestException;
import com.jobtrack.common.WorkMode;
import com.jobtrack.compatibility.CompatibilityModels.ProfileFacts;
import com.jobtrack.compatibility.CompatibilityService;
import com.jobtrack.offer.OfferDtos.OfferRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exportación e importación CSV de ofertas.
 *
 * <p>Seguridad: al exportar, las celdas de texto que empiezan por {@code = + - @} se prefijan con una comilla simple para
 * evitar inyección de fórmulas en hojas de cálculo; al importar se deshace ese prefijo. La importación es todo-o-nada:
 * si alguna fila es inválida no se guarda ninguna.
 */
@Service
public class OfferCsvService {

    static final int MAX_ROWS = 1000;
    static final List<String> HEADERS = List.of("title", "company", "url", "location", "work_mode", "published_date",
            "detected_date", "applied_date", "salary_min", "salary_max", "currency", "contract_type", "working_hours",
            "min_years_experience", "skills", "requirements", "notes", "description", "status", "favorite",
            "compatibility_score");
    private static final Set<String> NON_TEXT = Set.of("work_mode", "published_date", "detected_date", "applied_date",
            "salary_min", "salary_max", "min_years_experience", "status", "favorite", "compatibility_score", "currency");

    public record ImportError(int row, String field, String message) {
    }

    public record PreviewRow(int row, String title, String company, OfferStatus status) {
    }

    public record ImportResult(boolean dryRun, int totalRows, int validRows, int imported, List<ImportError> errors,
            List<PreviewRow> preview) {
    }

    private final JobOfferRepository offers;
    private final OfferService offerService;
    private final CompatibilityService compatibility;
    private final Validator validator;

    public OfferCsvService(JobOfferRepository offers, OfferService offerService, CompatibilityService compatibility,
            Validator validator) {
        this.offers = offers;
        this.offerService = offerService;
        this.compatibility = compatibility;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public String export(UUID userId) {
        StringWriter out = new StringWriter();
        out.write('﻿'); // BOM para que Excel detecte UTF-8
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(HEADERS.toArray(String[]::new)).get())) {
            for (JobOffer o : offers.findAllByUserIdOrderByDetectedDateDescCreatedAtDesc(userId)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("title", o.getTitle());
                row.put("company", o.getCompany());
                row.put("url", o.getUrl());
                row.put("location", o.getLocation());
                row.put("work_mode", o.getWorkMode());
                row.put("published_date", o.getPublishedDate());
                row.put("detected_date", o.getDetectedDate());
                row.put("applied_date", o.getAppliedDate());
                row.put("salary_min", plain(o.getSalaryMin()));
                row.put("salary_max", plain(o.getSalaryMax()));
                row.put("currency", o.getCurrency());
                row.put("contract_type", o.getContractType());
                row.put("working_hours", o.getWorkingHours());
                row.put("min_years_experience", plain(o.getMinYearsExperience()));
                row.put("skills", String.join(";", o.getSkills().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()));
                row.put("requirements", o.getRequirements());
                row.put("notes", o.getNotes());
                row.put("description", o.getDescription());
                row.put("status", o.getStatus());
                row.put("favorite", o.isFavorite());
                row.put("compatibility_score", o.getCompatibilityScore());
                List<Object> cells = new ArrayList<>();
                for (String h : HEADERS) {
                    Object value = row.get(h);
                    cells.add(value == null ? "" : NON_TEXT.contains(h) ? value.toString() : defuse(value.toString()));
                }
                printer.printRecord(cells);
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el CSV", e);
        }
        return out.toString();
    }

    @Transactional
    public ImportResult importCsv(UUID userId, byte[] content, boolean dryRun) {
        String text = decode(content);
        List<ImportError> errors = new ArrayList<>();
        List<OfferRequest> valid = new ArrayList<>();
        List<PreviewRow> preview = new ArrayList<>();
        int total = 0;

        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true).setIgnoreSurroundingSpaces(true).setAllowMissingColumnNames(false).get();
        try (CSVParser parser = format.parse(new StringReader(text))) {
            Map<String, String> headerLookup = new LinkedHashMap<>();
            for (String h : parser.getHeaderNames()) {
                headerLookup.put(h.strip().toLowerCase(Locale.ROOT), h);
            }
            for (String required : List.of("title", "company")) {
                if (!headerLookup.containsKey(required)) {
                    throw new BadRequestException("Falta la columna obligatoria «" + required + "». Cabeceras esperadas: "
                            + String.join(", ", HEADERS));
                }
            }
            for (CSVRecord record : parser) {
                total++;
                if (total > MAX_ROWS) {
                    throw new BadRequestException("El archivo supera el máximo de " + MAX_ROWS + " filas.");
                }
                int row = (int) record.getRecordNumber() + 1; // la cabecera es la fila 1
                List<ImportError> rowErrors = new ArrayList<>();
                OfferRequest request = toRequest(record, headerLookup, row, rowErrors);
                if (request != null) {
                    for (ConstraintViolation<OfferRequest> v : validator.validate(request)) {
                        rowErrors.add(new ImportError(row, toCsvField(v.getPropertyPath().toString()), v.getMessage()));
                    }
                }
                if (rowErrors.isEmpty()) {
                    valid.add(request);
                    if (preview.size() < 5) {
                        preview.add(new PreviewRow(row, request.title(), request.company(),
                                request.status() == null ? OfferStatus.SAVED : request.status()));
                    }
                } else {
                    errors.addAll(rowErrors);
                }
            }
        } catch (IOException | java.io.UncheckedIOException | IllegalArgumentException e) {
            throw new BadRequestException("El archivo CSV está mal formado: " + e.getMessage());
        }
        if (total == 0) {
            throw new BadRequestException("El archivo no contiene filas de datos.");
        }

        int imported = 0;
        if (!dryRun && errors.isEmpty()) {
            ProfileFacts profile = compatibility.profileFacts(userId);
            for (OfferRequest request : valid) {
                offerService.createEntity(userId, request, profile);
                imported++;
            }
        }
        return new ImportResult(dryRun, total, valid.size(), imported, errors, preview);
    }

    private OfferRequest toRequest(CSVRecord r, Map<String, String> headers, int row, List<ImportError> errors) {
        Parser p = new Parser(r, headers, row, errors);
        String skills = p.text("skills");
        List<String> skillList = skills == null ? List.of()
                : Arrays.stream(skills.split("[;|]")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        OfferRequest request = new OfferRequest(
                p.text("title"), p.text("company"), p.text("description"), p.text("url"), p.text("location"),
                p.enumValue("work_mode", WorkMode.class), p.date("published_date"), p.date("detected_date"),
                p.date("applied_date"), p.decimal("salary_min"), p.decimal("salary_max"), p.text("currency"),
                p.text("contract_type"), p.text("working_hours"), p.decimal("min_years_experience"), skillList,
                p.text("requirements"), p.text("notes"), p.enumValue("status", OfferStatus.class), p.bool("favorite"));
        return errors.stream().anyMatch(e -> e.row() == row) ? null : request;
    }

    /** Conversión tolerante de celdas con error por campo. */
    private static final class Parser {
        private final CSVRecord record;
        private final Map<String, String> headers;
        private final int row;
        private final List<ImportError> errors;

        Parser(CSVRecord record, Map<String, String> headers, int row, List<ImportError> errors) {
            this.record = record;
            this.headers = headers;
            this.row = row;
            this.errors = errors;
        }

        String raw(String column) {
            String header = headers.get(column);
            if (header == null || !record.isSet(header)) {
                return null;
            }
            String value = record.get(header).strip();
            return value.isEmpty() ? null : value;
        }

        String text(String column) {
            String value = raw(column);
            if (value != null && value.length() > 1 && value.charAt(0) == '\'' && "=+-@\t\r".indexOf(value.charAt(1)) >= 0) {
                value = value.substring(1);
            }
            return value;
        }

        LocalDate date(String column) {
            String value = raw(column);
            if (value == null) {
                return null;
            }
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException e) {
                errors.add(new ImportError(row, column, "Fecha no válida (usa AAAA-MM-DD)"));
                return null;
            }
        }

        BigDecimal decimal(String column) {
            String value = raw(column);
            if (value == null) {
                return null;
            }
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                errors.add(new ImportError(row, column, "Número no válido (usa punto decimal)"));
                return null;
            }
        }

        <E extends Enum<E>> E enumValue(String column, Class<E> type) {
            String value = raw(column);
            if (value == null) {
                return null;
            }
            try {
                return Enum.valueOf(type, value.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
            } catch (IllegalArgumentException e) {
                errors.add(new ImportError(row, column, "Valor no válido. Permitidos: "
                        + String.join(", ", Arrays.stream(type.getEnumConstants()).map(Enum::name).toList())));
                return null;
            }
        }

        Boolean bool(String column) {
            String value = raw(column);
            if (value == null) {
                return null;
            }
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "true", "1", "yes", "si", "sí" -> true;
                case "false", "0", "no" -> false;
                default -> {
                    errors.add(new ImportError(row, column, "Valor booleano no válido (true/false)"));
                    yield null;
                }
            };
        }
    }

    private static String toCsvField(String property) {
        return switch (property.replaceAll("\\[.*", "")) {
            case "workMode" -> "work_mode";
            case "publishedDate" -> "published_date";
            case "detectedDate" -> "detected_date";
            case "appliedDate" -> "applied_date";
            case "salaryMin" -> "salary_min";
            case "salaryMax" -> "salary_max";
            case "contractType" -> "contract_type";
            case "workingHours" -> "working_hours";
            case "minYearsExperience" -> "min_years_experience";
            case "salaryRangeValid" -> "salary_min";
            default -> property.replaceAll("\\[.*", "");
        };
    }

    private static String decode(byte[] content) {
        if (content == null || content.length == 0) {
            throw new BadRequestException("El archivo está vacío.");
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            throw new BadRequestException("El archivo debe estar codificado en UTF-8.");
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String defuse(String value) {
        return !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }
}
