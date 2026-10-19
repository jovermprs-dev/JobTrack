package com.jobtrack.compatibility;

import com.jobtrack.common.WorkMode;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public final class CompatibilityModels {

    private CompatibilityModels() {
    }

    /** Datos de la oferta relevantes para la puntuación. Los campos {@code null} significan «desconocido». */
    public record OfferFacts(String title, Collection<String> skills, BigDecimal minYearsExperience,
            WorkMode workMode, String location) {
    }

    /** Datos del perfil relevantes para la puntuación. Colecciones vacías y {@code null} = «sin registrar». */
    public record ProfileFacts(Collection<String> skills, BigDecimal yearsExperience, Collection<String> desiredRoles,
            WorkMode preferredWorkMode, String location) {

        public static ProfileFacts empty() {
            return new ProfileFacts(List.of(), null, List.of(), null, null);
        }
    }

    /**
     * @param earned fracción 0..1 conseguida (0 si no está disponible)
     */
    public record Dimension(String key, String label, int weight, boolean available, double earned, String detail) {
    }

    /**
     * @param score {@code null} cuando no hay información suficiente
     */
    public record Result(Integer score, boolean sufficientInformation, List<Dimension> dimensions,
            List<String> positives, List<String> missing, List<String> unknown, String disclaimer) {
    }
}
