package com.jobtrack.profile;

import com.jobtrack.common.WorkMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record LanguageDto(
            @NotBlank(message = "El idioma es obligatorio") @Size(max = 60, message = "Idioma demasiado largo") String language,
            @NotBlank(message = "El nivel es obligatorio") @Size(max = 40, message = "Nivel demasiado largo") String level) {
    }

    public record ProfileRequest(
            @Size(max = 200, message = "Máximo 200 caracteres") String degree,
            @Size(max = 5000, message = "Máximo 5000 caracteres") String experienceSummary,
            @DecimalMin(value = "0", message = "No puede ser negativo") @DecimalMax(value = "60", message = "Valor demasiado alto") @Digits(integer = 2, fraction = 1, message = "Máximo 1 decimal") BigDecimal yearsExperience,
            @Size(max = 60, message = "Máximo 60 competencias") List<@NotBlank(message = "Competencia vacía") @Size(max = 80, message = "Competencia demasiado larga") String> skills,
            @Size(max = 20, message = "Máximo 20 idiomas") List<@Valid LanguageDto> languages,
            @Size(max = 200, message = "Máximo 200 caracteres") String location,
            WorkMode preferredWorkMode,
            @Size(max = 20, message = "Máximo 20 puestos") List<@NotBlank(message = "Puesto vacío") @Size(max = 120, message = "Puesto demasiado largo") String> desiredRoles,
            @DecimalMin(value = "0", message = "No puede ser negativo") @DecimalMax(value = "100000000", message = "Valor demasiado alto") @Digits(integer = 9, fraction = 2, message = "Máximo 2 decimales") BigDecimal expectedSalary,
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "Usa un código de 3 letras (EUR, USD…)") String salaryCurrency,
            @Size(max = 5000, message = "Máximo 5000 caracteres") String preferences) {
    }

    public record ProfileResponse(String degree, String experienceSummary, BigDecimal yearsExperience, List<String> skills,
            List<LanguageDto> languages, String location, WorkMode preferredWorkMode, List<String> desiredRoles,
            BigDecimal expectedSalary, String salaryCurrency, String preferences, Instant updatedAt) {

        static ProfileResponse empty() {
            return new ProfileResponse(null, null, null, List.of(), List.of(), null, null, List.of(), null, null, null, null);
        }

        static ProfileResponse from(Profile p) {
            return new ProfileResponse(p.getDegree(), p.getExperienceSummary(), p.getYearsExperience(),
                    p.getSkills().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                    p.getLanguages().stream().map(l -> new LanguageDto(l.language(), l.level()))
                            .sorted(Comparator.comparing(LanguageDto::language, String.CASE_INSENSITIVE_ORDER)).toList(),
                    p.getLocation(), p.getPreferredWorkMode(),
                    p.getDesiredRoles().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                    p.getExpectedSalary(), p.getSalaryCurrency(), p.getPreferences(), p.getUpdatedAt());
        }
    }
}
