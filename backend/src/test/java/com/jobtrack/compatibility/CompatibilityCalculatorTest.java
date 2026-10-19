package com.jobtrack.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrack.common.WorkMode;
import com.jobtrack.compatibility.CompatibilityModels.Dimension;
import com.jobtrack.compatibility.CompatibilityModels.OfferFacts;
import com.jobtrack.compatibility.CompatibilityModels.ProfileFacts;
import com.jobtrack.compatibility.CompatibilityModels.Result;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompatibilityCalculatorTest {

    private final CompatibilityCalculator calculator = new CompatibilityCalculator();

    private static OfferFacts offer(String title, List<String> skills, String years, WorkMode mode, String location) {
        return new OfferFacts(title, skills, years == null ? null : new BigDecimal(years), mode, location);
    }

    private static ProfileFacts profile(List<String> skills, String years, List<String> roles, WorkMode mode, String location) {
        return new ProfileFacts(skills, years == null ? null : new BigDecimal(years), roles, mode, location);
    }

    private static Dimension dimension(Result r, String key) {
        return r.dimensions().stream().filter(d -> d.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void perfectMatchScores100() {
        Result r = calculator.calculate(
                offer("Java Developer", List.of("Java", "Spring Boot"), "3", WorkMode.REMOTE, "Madrid"),
                profile(List.of("java", "spring-boot", "SQL"), "5", List.of("Java Developer"), WorkMode.REMOTE, "Barcelona"));

        assertThat(r.sufficientInformation()).isTrue();
        assertThat(r.score()).isEqualTo(100);
        assertThat(r.missing()).isEmpty();
        assertThat(dimension(r, "location").available()).as("la ubicación no aplica a ofertas remotas").isFalse();
    }

    @Test
    void skillMatchingIgnoresCaseAccentsAndSeparators() {
        Result r = calculator.calculate(
                offer("Dev", List.of("Node.js", "Programación", "CI/CD"), null, null, null),
                profile(List.of("nodejs", "programacion", "ci cd"), null, List.of(), null, null));

        assertThat(dimension(r, "skills").earned()).isEqualTo(1.0);
        assertThat(r.score()).isEqualTo(100);
    }

    @Test
    void missingSkillsAreReportedOnlyWhenProfileHasSkills() {
        Result withSkills = calculator.calculate(
                offer("Dev", List.of("Java", "Kafka"), null, null, null),
                profile(List.of("Java"), null, List.of(), null, null));
        assertThat(withSkills.score()).isEqualTo(50);
        assertThat(withSkills.missing()).anyMatch(m -> m.contains("Kafka"));
        assertThat(withSkills.positives()).anyMatch(m -> m.contains("Java"));

        Result emptyProfile = calculator.calculate(
                offer("Dev", List.of("Java", "Kafka"), null, null, null), ProfileFacts.empty());
        assertThat(emptyProfile.missing()).as("sin competencias registradas no se afirma que falten").isEmpty();
        assertThat(emptyProfile.score()).isNull();
        assertThat(emptyProfile.sufficientInformation()).isFalse();
        assertThat(emptyProfile.unknown()).anyMatch(u -> u.contains("no tiene competencias registradas"));
    }

    @Test
    void insufficientInformationReturnsNoScoreInsteadOfZero() {
        Result r = calculator.calculate(offer("Backend", List.of(), null, WorkMode.HYBRID, null),
                profile(List.of(), null, List.of("Backend"), WorkMode.HYBRID, null));

        // rol (15) + modalidad (10) = 25 < 40
        assertThat(r.score()).isNull();
        assertThat(r.sufficientInformation()).isFalse();
    }

    @Test
    void experienceZeroRequiredIsSatisfiedAndDifferentFromUnknown() {
        Result zero = calculator.calculate(offer("Dev", List.of("Java"), "0", null, null),
                profile(List.of("Java"), "0", List.of(), null, null));
        assertThat(dimension(zero, "experience").available()).isTrue();
        assertThat(dimension(zero, "experience").earned()).isEqualTo(1.0);

        Result unknown = calculator.calculate(offer("Dev", List.of("Java"), null, null, null),
                profile(List.of("Java"), "0", List.of(), null, null));
        assertThat(dimension(unknown, "experience").available()).isFalse();
    }

    @Test
    void experienceShortfallIsProportionalAndReported() {
        Result r = calculator.calculate(offer("Dev", List.of("Java"), "4", null, null),
                profile(List.of("Java"), "2", List.of(), null, null));

        assertThat(dimension(r, "experience").earned()).isEqualTo(0.5);
        assertThat(r.missing()).anyMatch(m -> m.contains("4 años") && m.contains("2"));
        // (50*1 + 15*0.5) / 65 = 88.46
        assertThat(r.score()).isEqualTo(88);
    }

    @Test
    void roleMatchesFullPartialOrNone() {
        OfferFacts o = offer("Senior Java Backend Developer", List.of("Java"), null, null, null);
        assertThat(dimension(calculator.calculate(o, profile(List.of("Java"), null, List.of("Java Backend Developer"), null, null)), "role").earned())
                .isEqualTo(1.0);
        assertThat(dimension(calculator.calculate(o, profile(List.of("Java"), null, List.of("Java Architect"), null, null)), "role").earned())
                .isEqualTo(0.5);
        assertThat(dimension(calculator.calculate(o, profile(List.of("Java"), null, List.of("Diseñador UX"), null, null)), "role").earned())
                .isEqualTo(0.0);
    }

    @Test
    void workModeAdjacentIsPartial() {
        OfferFacts o = offer("Dev", List.of("Java"), null, WorkMode.HYBRID, null);
        assertThat(dimension(calculator.calculate(o, profile(List.of("Java"), null, List.of(), WorkMode.REMOTE, null)), "workMode").earned())
                .isEqualTo(0.5);
        OfferFacts onSite = offer("Dev", List.of("Java"), null, WorkMode.ON_SITE, null);
        Result r = calculator.calculate(onSite, profile(List.of("Java"), null, List.of(), WorkMode.REMOTE, null));
        assertThat(dimension(r, "workMode").earned()).isEqualTo(0.0);
        assertThat(r.missing()).anyMatch(m -> m.contains("modalidad"));
    }

    @Test
    void locationMatchesBySubstringIgnoringAccents() {
        OfferFacts o = offer("Dev", List.of("Java"), null, WorkMode.ON_SITE, "Málaga, España");
        assertThat(dimension(calculator.calculate(o, profile(List.of("Java"), null, List.of(), null, "malaga")), "location").earned())
                .isEqualTo(1.0);
        assertThat(dimension(calculator.calculate(o, profile(List.of("Java"), null, List.of(), null, "Bilbao")), "location").earned())
                .isEqualTo(0.0);
    }

    @Test
    void scoreIsAlwaysWithin0And100AndNeverPresentedAsProbability() {
        Result r = calculator.calculate(offer("X", List.of("A", "B", "C"), "10", WorkMode.ON_SITE, "Sevilla"),
                profile(List.of("Z"), "1", List.of("Y"), WorkMode.REMOTE, "Oslo"));
        assertThat(r.score()).isBetween(0, 100);
        assertThat(r.disclaimer()).contains("no es una probabilidad");
    }
}
