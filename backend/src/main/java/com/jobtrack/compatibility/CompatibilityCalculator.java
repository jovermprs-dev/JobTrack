package com.jobtrack.compatibility;

import com.jobtrack.common.Text;
import com.jobtrack.common.WorkMode;
import com.jobtrack.compatibility.CompatibilityModels.Dimension;
import com.jobtrack.compatibility.CompatibilityModels.OfferFacts;
import com.jobtrack.compatibility.CompatibilityModels.ProfileFacts;
import com.jobtrack.compatibility.CompatibilityModels.Result;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Compatibilidad perfil-oferta determinista y transparente (ver docs/compatibility.md).
 *
 * <p>Cinco dimensiones con pesos fijos. Una dimensión solo puntúa si hay dato en AMBOS lados; las no disponibles
 * se excluyen y el resto se reescala. Si el peso disponible es menor que {@link #MIN_AVAILABLE_WEIGHT} no se
 * devuelve puntuación. No es una probabilidad de conseguir el empleo.
 */
@Component
public class CompatibilityCalculator {

    public static final int W_SKILLS = 50;
    public static final int W_EXPERIENCE = 15;
    public static final int W_ROLE = 15;
    public static final int W_WORK_MODE = 10;
    public static final int W_LOCATION = 10;
    public static final int MIN_AVAILABLE_WEIGHT = 40;

    static final String DISCLAIMER = "Indicador orientativo de encaje entre tu perfil y los datos de la oferta; "
            + "no es una probabilidad de conseguir el puesto.";

    private static final Set<String> STOP_WORDS = Set.of("de", "la", "el", "en", "y", "a", "para", "of", "the", "and", "for", "in");

    public Result calculate(OfferFacts offer, ProfileFacts profile) {
        List<String> positives = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        List<Dimension> dimensions = List.of(
                skills(offer, profile, positives, missing, unknown),
                experience(offer, profile, positives, missing, unknown),
                role(offer, profile, positives, unknown),
                workMode(offer, profile, positives, missing, unknown),
                location(offer, profile, positives, missing, unknown));

        int availableWeight = dimensions.stream().filter(Dimension::available).mapToInt(Dimension::weight).sum();
        boolean sufficient = availableWeight >= MIN_AVAILABLE_WEIGHT;
        Integer score = null;
        if (sufficient) {
            double earned = dimensions.stream().filter(Dimension::available).mapToDouble(d -> d.weight() * d.earned()).sum();
            score = (int) Math.round(earned / availableWeight * 100);
        } else {
            unknown.add("Información insuficiente para calcular una puntuación fiable.");
        }
        return new Result(score, sufficient, dimensions, positives, missing, unknown, DISCLAIMER);
    }

    private Dimension skills(OfferFacts offer, ProfileFacts profile, List<String> positives, List<String> missing,
            List<String> unknown) {
        Map<String, String> required = byKey(offer.skills());
        Map<String, String> owned = byKey(profile.skills());
        if (required.isEmpty()) {
            unknown.add("La oferta no indica competencias requeridas.");
            return unavailable("skills", "Competencias", W_SKILLS, "La oferta no indica competencias.");
        }
        if (owned.isEmpty()) {
            unknown.add("Tu perfil no tiene competencias registradas, por lo que no se puede saber si cumples las requeridas.");
            return unavailable("skills", "Competencias", W_SKILLS, "Tu perfil no tiene competencias registradas.");
        }
        List<String> matched = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        required.forEach((key, label) -> (owned.containsKey(key) ? matched : notFound).add(label));
        matched.forEach(s -> positives.add("Competencia requerida que figura en tu perfil: " + s));
        notFound.forEach(s -> missing.add("Competencia requerida no registrada en tu perfil: " + s));
        double earned = (double) matched.size() / required.size();
        return new Dimension("skills", "Competencias", W_SKILLS, true, earned,
                matched.size() + " de " + required.size() + " competencias requeridas figuran en tu perfil.");
    }

    private Dimension experience(OfferFacts offer, ProfileFacts profile, List<String> positives, List<String> missing,
            List<String> unknown) {
        BigDecimal needed = offer.minYearsExperience();
        BigDecimal has = profile.yearsExperience();
        if (needed == null) {
            unknown.add("La oferta no indica experiencia mínima.");
            return unavailable("experience", "Experiencia", W_EXPERIENCE, "La oferta no indica experiencia mínima.");
        }
        if (has == null) {
            unknown.add("Tu perfil no indica los años de experiencia.");
            return unavailable("experience", "Experiencia", W_EXPERIENCE, "Tu perfil no indica los años de experiencia.");
        }
        if (needed.signum() == 0 || has.compareTo(needed) >= 0) {
            positives.add("Cumples la experiencia mínima (" + plain(needed) + " años; tu perfil indica " + plain(has) + ").");
            return new Dimension("experience", "Experiencia", W_EXPERIENCE, true, 1.0, "Cumples la experiencia mínima.");
        }
        missing.add("Se piden " + plain(needed) + " años de experiencia; tu perfil indica " + plain(has) + ".");
        double earned = has.doubleValue() / needed.doubleValue();
        return new Dimension("experience", "Experiencia", W_EXPERIENCE, true, earned,
                "Tienes " + plain(has) + " de " + plain(needed) + " años requeridos.");
    }

    private Dimension role(OfferFacts offer, ProfileFacts profile, List<String> positives, List<String> unknown) {
        Set<String> titleTokens = tokens(offer.title());
        List<String> roles = profile.desiredRoles() == null ? List.of() : profile.desiredRoles().stream()
                .filter(r -> r != null && !r.isBlank()).toList();
        if (roles.isEmpty()) {
            unknown.add("Tu perfil no indica puestos deseados.");
            return unavailable("role", "Puesto deseado", W_ROLE, "Tu perfil no indica puestos deseados.");
        }
        if (titleTokens.isEmpty()) {
            return unavailable("role", "Puesto deseado", W_ROLE, "La oferta no tiene título.");
        }
        String foldedTitle = " " + String.join(" ", orderedTokens(offer.title())) + " ";
        double best = 0;
        String bestRole = null;
        for (String role : roles) {
            List<String> roleTokens = orderedTokens(role);
            if (roleTokens.isEmpty()) {
                continue;
            }
            double value;
            if (foldedTitle.contains(" " + String.join(" ", roleTokens) + " ")) {
                value = 1.0;
            } else {
                long shared = roleTokens.stream().filter(titleTokens::contains).count();
                value = (double) shared / roleTokens.size() >= 0.5 ? 0.5 : 0.0;
            }
            if (value > best) {
                best = value;
                bestRole = role;
            }
        }
        if (best > 0) {
            positives.add((best == 1.0 ? "El título de la oferta coincide con un puesto que buscas: " : "El título de la oferta se parece a un puesto que buscas: ") + bestRole);
        }
        return new Dimension("role", "Puesto deseado", W_ROLE, true, best,
                best == 1.0 ? "Coincide con un puesto deseado." : best > 0 ? "Coincidencia parcial con un puesto deseado." : "No coincide con tus puestos deseados.");
    }

    private Dimension workMode(OfferFacts offer, ProfileFacts profile, List<String> positives, List<String> missing,
            List<String> unknown) {
        WorkMode wanted = profile.preferredWorkMode();
        WorkMode actual = offer.workMode();
        if (actual == null) {
            unknown.add("La oferta no indica la modalidad de trabajo.");
            return unavailable("workMode", "Modalidad", W_WORK_MODE, "La oferta no indica la modalidad.");
        }
        if (wanted == null) {
            unknown.add("Tu perfil no indica modalidad preferida.");
            return unavailable("workMode", "Modalidad", W_WORK_MODE, "Tu perfil no indica modalidad preferida.");
        }
        if (wanted == actual) {
            positives.add("La modalidad coincide con tu preferencia.");
            return new Dimension("workMode", "Modalidad", W_WORK_MODE, true, 1.0, "Modalidad coincidente.");
        }
        if (wanted == WorkMode.HYBRID || actual == WorkMode.HYBRID) {
            return new Dimension("workMode", "Modalidad", W_WORK_MODE, true, 0.5, "Modalidad parcialmente compatible.");
        }
        missing.add("La modalidad de la oferta es incompatible con tu preferencia.");
        return new Dimension("workMode", "Modalidad", W_WORK_MODE, true, 0.0, "Modalidad no compatible.");
    }

    private Dimension location(OfferFacts offer, ProfileFacts profile, List<String> positives, List<String> missing,
            List<String> unknown) {
        if (offer.workMode() == WorkMode.REMOTE) {
            return unavailable("location", "Ubicación", W_LOCATION, "No aplica: oferta remota.");
        }
        String offerLoc = Text.fold(offer.location());
        String profileLoc = Text.fold(profile.location());
        if (offerLoc.isEmpty()) {
            unknown.add("La oferta no indica ubicación.");
            return unavailable("location", "Ubicación", W_LOCATION, "La oferta no indica ubicación.");
        }
        if (profileLoc.isEmpty()) {
            unknown.add("Tu perfil no indica ubicación.");
            return unavailable("location", "Ubicación", W_LOCATION, "Tu perfil no indica ubicación.");
        }
        if (offerLoc.contains(profileLoc) || profileLoc.contains(offerLoc)) {
            positives.add("La ubicación coincide con la de tu perfil.");
            return new Dimension("location", "Ubicación", W_LOCATION, true, 1.0, "Ubicación coincidente.");
        }
        missing.add("La ubicación de la oferta (" + offer.location() + ") no coincide con la de tu perfil.");
        return new Dimension("location", "Ubicación", W_LOCATION, true, 0.0, "Ubicación distinta.");
    }

    private static Dimension unavailable(String key, String label, int weight, String detail) {
        return new Dimension(key, label, weight, false, 0.0, detail);
    }

    /** Clave de comparación de una competencia: minúsculas, sin acentos y sin espacios ni separadores ("Node.js" = "nodejs"). */
    static String skillKey(String skill) {
        return Text.fold(skill).replaceAll("[\\s.\\-_/]+", "");
    }

    private static Map<String, String> byKey(Collection<String> skills) {
        Map<String, String> map = new LinkedHashMap<>();
        if (skills != null) {
            for (String s : skills) {
                if (s != null && !s.isBlank()) {
                    map.putIfAbsent(skillKey(s), s.strip());
                }
            }
        }
        return map;
    }

    private static List<String> orderedTokens(String text) {
        List<String> tokens = new ArrayList<>();
        for (String t : Text.fold(text).split("[^\\p{L}\\p{N}+#]+")) {
            if (!t.isEmpty() && !STOP_WORDS.contains(t)) {
                tokens.add(t);
            }
        }
        return tokens;
    }

    private static Set<String> tokens(String text) {
        return new LinkedHashSet<>(orderedTokens(text));
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
