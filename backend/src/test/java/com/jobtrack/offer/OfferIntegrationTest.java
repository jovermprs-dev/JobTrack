package com.jobtrack.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.jobtrack.support.IntegrationTest;
import com.jobtrack.support.TestApi;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class OfferIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;

    private TestApi newUser() throws Exception {
        return TestApi.register(mvc, json, "offers-" + UUID.randomUUID() + "@example.com");
    }

    @Test
    void fullCrudLifecycle() throws Exception {
        TestApi user = newUser();

        String body = user.post("/api/v1/offers", Map.of(
                        "title", "  Java Developer  ", "company", "Acme", "description", "Buen equipo",
                        "url", "https://acme.example/jobs/1", "location", "Madrid", "workMode", "HYBRID",
                        "salaryMin", 30000, "salaryMax", 40000, "currency", "eur",
                        "skills", List.of("Java", "java", "Spring Boot", "")
                        .subList(0, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Java Developer"))
                .andExpect(jsonPath("$.status").value("SAVED"))
                .andExpect(jsonPath("$.favorite").value(false))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.skills", contains("Java", "Spring Boot")))
                .andExpect(jsonPath("$.compatibilityScore").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        user.get("/api/v1/offers/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.company").value("Acme"));

        user.put("/api/v1/offers/" + id, Map.of("title", "Senior Java Developer", "company", "Acme", "location", "Barcelona"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Senior Java Developer"))
                .andExpect(jsonPath("$.location").value("Barcelona"))
                .andExpect(jsonPath("$.salaryMin").doesNotExist())
                .andExpect(jsonPath("$.status").value("SAVED"));

        user.delete("/api/v1/offers/" + id).andExpect(status().isNoContent());
        user.get("/api/v1/offers/" + id).andExpect(status().isNotFound());
    }

    @Test
    void validationErrorsAreReportedPerField() throws Exception {
        TestApi user = newUser();
        user.post("/api/v1/offers", Map.of("title", " ", "company", "Acme", "url", "javascript:alert(1)",
                        "salaryMin", 50000, "salaryMax", 10000, "currency", "EURO", "minYearsExperience", -1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("title")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("url")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("currency")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("minYearsExperience")))
                .andExpect(jsonPath("$.errors[*].message", hasItem("El salario mínimo no puede superar al máximo")));
    }

    @Test
    void numbersWithAbsurdScaleOrPrecisionAreRejectedInsteadOfExhaustingMemory() throws Exception {
        TestApi user = newUser();
        user.put("/api/v1/profile", Map.of("yearsExperience", 5)).andExpect(status().isOk());
        user.post("/api/v1/offers", Map.of("title", "Dev", "company", "Acme", "minYearsExperience", new java.math.BigDecimal("1E-1500000000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("minYearsExperience")));
        user.post("/api/v1/offers", Map.of("title", "Dev", "company", "Acme", "salaryMin", new java.math.BigDecimal("1.123")))
                .andExpect(status().isBadRequest());
        user.put("/api/v1/profile", Map.of("yearsExperience", new java.math.BigDecimal("1E-1500000000")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void zeroIsDistinctFromUnknown() throws Exception {
        TestApi user = newUser();
        user.post("/api/v1/offers", Map.of("title", "Becario", "company", "Acme", "salaryMin", 0, "minYearsExperience", 0))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.salaryMin").value(0))
                .andExpect(jsonPath("$.minYearsExperience").value(0))
                .andExpect(jsonPath("$.salaryMax").value((Object) null))
                .andExpect(jsonPath("$.workMode").value((Object) null));
    }

    @Test
    void filtersAreCombinableAndPaginatedInBackend() throws Exception {
        TestApi user = newUser();
        user.createOffer("Java Developer", "Alpha", Map.of("location", "Madrid", "workMode", "REMOTE", "skills", List.of("Java")));
        user.createOffer("Frontend Developer", "Beta", Map.of("location", "Madrid", "workMode", "HYBRID", "skills", List.of("Angular")));
        String fav = user.createOffer("Data Engineer", "Alpha", Map.of("location", "Valencia", "workMode", "REMOTE"));
        user.createOffer("100% Remote_Role", "Gamma", Map.of());
        user.patch("/api/v1/offers/" + fav + "/favorite", Map.of("favorite", true)).andExpect(status().isOk());

        user.get("/api/v1/offers?size=2&sort=company,asc").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].company").value("Alpha"));
        user.get("/api/v1/offers?q=angular").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].company").value("Beta"));
        user.get("/api/v1/offers?company=alp&workMode=REMOTE").andExpect(jsonPath("$.totalElements").value(2));
        user.get("/api/v1/offers?company=alp&location=valencia").andExpect(jsonPath("$.totalElements").value(1));
        user.get("/api/v1/offers?favorite=true").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Data Engineer"));
        user.get("/api/v1/offers?status=APPLIED").andExpect(jsonPath("$.totalElements").value(0));
        // los comodines de LIKE se tratan como texto literal
        user.perform(MockMvcRequestBuilders.get("/api/v1/offers").param("q", "100%")).andExpect(jsonPath("$.totalElements").value(1));
        user.perform(MockMvcRequestBuilders.get("/api/v1/offers").param("q", "%")).andExpect(jsonPath("$.totalElements").value(1));
        user.perform(MockMvcRequestBuilders.get("/api/v1/offers").param("q", "_")).andExpect(jsonPath("$.totalElements").value(1));
        user.get("/api/v1/offers?sort=password,asc").andExpect(status().isBadRequest());
        user.get("/api/v1/offers?status=NOPE").andExpect(status().isBadRequest());
    }

    @Test
    void sortingByCompatibilityPutsUnscoredOffersLast() throws Exception {
        TestApi user = newUser();
        user.put("/api/v1/profile", Map.of("skills", List.of("Java"))).andExpect(status().isOk());
        user.createOffer("Low", "A", Map.of("skills", List.of("Java", "Kafka", "Go", "Rust")));
        user.createOffer("High", "B", Map.of("skills", List.of("Java")));
        user.createOffer("Unknown", "C", Map.of());

        user.get("/api/v1/offers?sort=compatibilityScore,desc")
                .andExpect(jsonPath("$.content[*].title", contains("High", "Low", "Unknown")));
        user.get("/api/v1/offers?sort=compatibilityScore,asc")
                .andExpect(jsonPath("$.content[*].title", contains("Low", "High", "Unknown")));
    }

    @Test
    void statusChangesAreRecordedServerSideInHistory() throws Exception {
        TestApi user = newUser();
        String id = user.createOffer("Dev", "Acme", Map.of());

        user.post("/api/v1/offers/" + id + "/status", Map.of("status", "APPLIED", "note", "Enviado por LinkedIn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.appliedDate").isNotEmpty());
        user.post("/api/v1/offers/" + id + "/status", Map.of("status", "INTERVIEW")).andExpect(status().isOk());

        user.get("/api/v1/offers/" + id + "/history").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].fromStatus").value("APPLIED"))
                .andExpect(jsonPath("$[0].toStatus").value("INTERVIEW"))
                .andExpect(jsonPath("$[1].fromStatus").value("SAVED"))
                .andExpect(jsonPath("$[1].toStatus").value("APPLIED"))
                .andExpect(jsonPath("$[1].note").value("Enviado por LinkedIn"))
                .andExpect(jsonPath("$[2].fromStatus").value((Object) null))
                .andExpect(jsonPath("$[2].toStatus").value("SAVED"))
                .andExpect(jsonPath("$[0].changedAt").isNotEmpty());
    }

    @Test
    void statusChangeToSameStatusIsRejectedAndClientCannotForgeHistory() throws Exception {
        TestApi user = newUser();
        String id = user.createOffer("Dev", "Acme", Map.of());

        user.post("/api/v1/offers/" + id + "/status", Map.of("status", "SAVED")).andExpect(status().isBadRequest());
        // campos de historial enviados por el cliente se ignoran
        user.post("/api/v1/offers/" + id + "/status",
                        Map.of("status", "REJECTED", "fromStatus", "OFFER_RECEIVED", "changedAt", "2001-01-01T00:00:00Z"))
                .andExpect(status().isOk());
        user.get("/api/v1/offers/" + id + "/history")
                .andExpect(jsonPath("$[0].fromStatus").value("SAVED"))
                .andExpect(jsonPath("$[0].changedAt").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.startsWith("2001"))));
        // PUT no cambia el estado
        user.put("/api/v1/offers/" + id, Map.of("title", "Dev", "company", "Acme", "status", "OFFER_RECEIVED"))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void profileDrivesCompatibilityAndRecalculatesExistingOffers() throws Exception {
        TestApi user = newUser();
        String id = user.createOffer("Java Developer", "Acme",
                Map.of("skills", List.of("Java", "Kafka"), "minYearsExperience", 3));
        user.get("/api/v1/offers/" + id).andExpect(jsonPath("$.compatibilityScore").value((Object) null));
        user.get("/api/v1/offers/" + id + "/compatibility")
                .andExpect(jsonPath("$.sufficientInformation").value(false))
                .andExpect(jsonPath("$.missing").isEmpty());

        user.put("/api/v1/profile", Map.of("skills", List.of("java"), "yearsExperience", 5,
                        "desiredRoles", List.of("Java Developer")))
                .andExpect(status().isOk());

        // skills 1/2 (50*0.5) + experiencia ok (15) + puesto ok (15) = 55 / 80
        user.get("/api/v1/offers/" + id).andExpect(jsonPath("$.compatibilityScore").value(69));
        user.get("/api/v1/offers/" + id + "/compatibility")
                .andExpect(jsonPath("$.score").value(69))
                .andExpect(jsonPath("$.missing[0]").value("Competencia requerida no registrada en tu perfil: Kafka"));
    }

    @Test
    void profileRoundTripAndValidation() throws Exception {
        TestApi user = newUser();
        user.get("/api/v1/profile").andExpect(status().isOk()).andExpect(jsonPath("$.skills").isEmpty())
                .andExpect(jsonPath("$.yearsExperience").value((Object) null));

        user.put("/api/v1/profile", Map.of("degree", "Ingeniería Informática", "yearsExperience", 4.5,
                        "skills", List.of("Java", "SQL"), "languages", List.of(Map.of("language", "Inglés", "level", "B2")),
                        "location", "Sevilla", "preferredWorkMode", "REMOTE", "desiredRoles", List.of("Backend"),
                        "expectedSalary", 45000, "salaryCurrency", "eur"))
                .andExpect(status().isOk());
        user.get("/api/v1/profile")
                .andExpect(jsonPath("$.degree").value("Ingeniería Informática"))
                .andExpect(jsonPath("$.yearsExperience").value(4.5))
                .andExpect(jsonPath("$.skills", contains("Java", "SQL")))
                .andExpect(jsonPath("$.languages[0].level").value("B2"))
                .andExpect(jsonPath("$.salaryCurrency").value("EUR"))
                .andExpect(jsonPath("$.preferredWorkMode").value("REMOTE"));

        user.put("/api/v1/profile", Map.of("yearsExperience", -2, "expectedSalary", -1))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dashboardStatsComeFromPersistedData() throws Exception {
        TestApi user = newUser();
        String empty = user.get("/api/v1/stats").andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.saved").value(0))
                .andExpect(jsonPath("$.responseRate.value").value((Object) null))
                .andExpect(jsonPath("$.averageCompatibility.value").value((Object) null))
                .andExpect(jsonPath("$.applicationsOverTime.length()").value(12))
                .andReturn().getResponse().getContentAsString();
        assertThat((List<?>) JsonPath.read(empty, "$.recentActivity")).isEmpty();

        String a = user.createOffer("A", "Acme", Map.of());
        String b = user.createOffer("B", "Acme", Map.of());
        String c = user.createOffer("C", "Beta", Map.of());
        user.patch("/api/v1/offers/" + c + "/favorite", Map.of("favorite", true));
        for (String id : List.of(a, b, c)) {
            user.post("/api/v1/offers/" + id + "/status", Map.of("status", "APPLIED"));
        }
        user.post("/api/v1/offers/" + a + "/status", Map.of("status", "INTERVIEW"));
        user.post("/api/v1/offers/" + a + "/status", Map.of("status", "OFFER_RECEIVED"));
        user.post("/api/v1/offers/" + b + "/status", Map.of("status", "REJECTED"));

        user.get("/api/v1/stats").andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.saved").value(3))
                .andExpect(jsonPath("$.totals.applied").value(3))
                .andExpect(jsonPath("$.totals.active").value(1))
                .andExpect(jsonPath("$.totals.interviews").value(1))
                .andExpect(jsonPath("$.totals.offersReceived").value(1))
                .andExpect(jsonPath("$.totals.favorites").value(1))
                .andExpect(jsonPath("$.responseRate.applied").value(3))
                .andExpect(jsonPath("$.responseRate.responded").value(2))
                .andExpect(jsonPath("$.responseRate.sufficientData").value(false))
                .andExpect(jsonPath("$.topCompanies[0].company").value("Acme"))
                .andExpect(jsonPath("$.topCompanies[0].count").value(2))
                .andExpect(jsonPath("$.applicationsOverTime[11].count").value(3))
                .andExpect(jsonPath("$.byStatus[?(@.status=='OFFER_RECEIVED')].count", contains(1)))
                .andExpect(jsonPath("$.recentActivity.length()").value(9));
    }
}
