package com.jobtrack.common;

import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class Text {

    private Text() {
    }

    /** Recorta; devuelve {@code null} si queda vacío (desconocido ≠ cadena vacía). */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Recorta, elimina vacíos y duplicados (sin distinguir mayúsculas) conservando el orden. */
    public static Set<String> cleanList(Collection<String> values) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> keys = new LinkedHashSet<>();
        if (values == null) {
            return seen;
        }
        for (String value : values) {
            String cleaned = clean(value);
            if (cleaned != null && keys.add(cleaned.toLowerCase(Locale.ROOT))) {
                seen.add(cleaned);
            }
        }
        return seen;
    }

    /** Minúsculas y sin acentos, para comparar texto libre. */
    public static String fold(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
    }

    /** Escapa los comodines de LIKE para búsquedas literales. */
    public static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
