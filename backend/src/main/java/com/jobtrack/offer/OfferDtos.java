package com.jobtrack.offer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jobtrack.common.WorkMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class OfferDtos {

    private OfferDtos() {
    }

    public record OfferRequest(
            @NotBlank(message = "El puesto es obligatorio") @Size(max = 200, message = "Máximo 200 caracteres") String title,
            @NotBlank(message = "La empresa es obligatoria") @Size(max = 200, message = "Máximo 200 caracteres") String company,
            @Size(max = 20000, message = "Máximo 20000 caracteres") String description,
            @Size(max = 2000, message = "Máximo 2000 caracteres")
            @Pattern(regexp = "^(?i)https?://\\S+$", message = "La URL debe empezar por http:// o https://") String url,
            @Size(max = 200, message = "Máximo 200 caracteres") String location,
            WorkMode workMode,
            LocalDate publishedDate,
            LocalDate detectedDate,
            LocalDate appliedDate,
            @DecimalMin(value = "0", message = "No puede ser negativo") @DecimalMax(value = "100000000", message = "Valor demasiado alto") BigDecimal salaryMin,
            @DecimalMin(value = "0", message = "No puede ser negativo") @DecimalMax(value = "100000000", message = "Valor demasiado alto") BigDecimal salaryMax,
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "Usa un código de 3 letras (EUR, USD…)") String currency,
            @Size(max = 80, message = "Máximo 80 caracteres") String contractType,
            @Size(max = 80, message = "Máximo 80 caracteres") String workingHours,
            @DecimalMin(value = "0", message = "No puede ser negativo") @DecimalMax(value = "60", message = "Valor demasiado alto") BigDecimal minYearsExperience,
            @Size(max = 60, message = "Máximo 60 competencias") List<@NotBlank(message = "Competencia vacía") @Size(max = 80, message = "Competencia demasiado larga") String> skills,
            @Size(max = 20000, message = "Máximo 20000 caracteres") String requirements,
            @Size(max = 20000, message = "Máximo 20000 caracteres") String notes,
            OfferStatus status,
            Boolean favorite) {

        @JsonIgnore
        @AssertTrue(message = "El salario mínimo no puede superar al máximo")
        public boolean isSalaryRangeValid() {
            return salaryMin == null || salaryMax == null || salaryMin.compareTo(salaryMax) <= 0;
        }
    }

    public record OfferResponse(UUID id, String title, String company, String description, String url, String location,
            WorkMode workMode, LocalDate publishedDate, LocalDate detectedDate, LocalDate appliedDate,
            BigDecimal salaryMin, BigDecimal salaryMax, String currency, String contractType, String workingHours,
            BigDecimal minYearsExperience, List<String> skills, String requirements, String notes, OfferStatus status,
            boolean favorite, Integer compatibilityScore, Instant createdAt, Instant updatedAt) {

        public static OfferResponse from(JobOffer o) {
            return new OfferResponse(o.getId(), o.getTitle(), o.getCompany(), o.getDescription(), o.getUrl(), o.getLocation(),
                    o.getWorkMode(), o.getPublishedDate(), o.getDetectedDate(), o.getAppliedDate(), o.getSalaryMin(),
                    o.getSalaryMax(), o.getCurrency(), o.getContractType(), o.getWorkingHours(), o.getMinYearsExperience(),
                    o.getSkills().stream().sorted(Comparator.comparing(String::toLowerCase)).toList(),
                    o.getRequirements(), o.getNotes(), o.getStatus(), o.isFavorite(), o.getCompatibilityScore(),
                    o.getCreatedAt(), o.getUpdatedAt());
        }
    }

    public record ChangeStatusRequest(
            @NotNull(message = "El estado es obligatorio") OfferStatus status,
            @Size(max = 1000, message = "Máximo 1000 caracteres") String note) {
    }

    public record FavoriteRequest(@NotNull(message = "Indica true o false") Boolean favorite) {
    }

    public record HistoryEntry(UUID id, OfferStatus fromStatus, OfferStatus toStatus, Instant changedAt, String note) {

        static HistoryEntry from(StatusHistory h) {
            return new HistoryEntry(h.getId(), h.getFromStatus(), h.getToStatus(), h.getChangedAt(), h.getNote());
        }
    }
}
