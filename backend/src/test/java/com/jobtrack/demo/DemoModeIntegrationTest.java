package com.jobtrack.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.jobtrack.support.IntegrationTest;
import com.jobtrack.support.TestApi;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** Instancia pública de solo lectura: sin login, con datos de ejemplo y con toda escritura bloqueada en el servidor. */
@IntegrationTest
@TestPropertySource(properties = "jobtrack.demo.enabled=true")
class DemoModeIntegrationTest {

    private static final int SEEDED_OFFERS = 12;

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;

    private String firstOfferId() throws Exception {
        String page = mvc.perform(get("/api/v1/offers")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(page, "$.content[0].id");
    }

    private static ResultActions assertReadOnlyRejection(ResultActions result) throws Exception {
        return result.andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Demostración de solo lectura"));
    }

    @Test
    void everyoneIsTheDemoAccountWithoutLoggingInAndNoSessionIsCreated() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.email").value("demo@jobtrack.local"))
                .andExpect(jsonPath("$.displayName").value("Visitante"))
                .andExpect(cookie().doesNotExist("SESSION"));
    }

    @Test
    void servesSampleOffersProfileAndStatsThatAddUp() throws Exception {
        mvc.perform(get("/api/v1/offers?size=50")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(SEEDED_OFFERS))
                .andExpect(jsonPath("$.content[*].company").value(hasItem("Nubix Cloud")));

        mvc.perform(get("/api/v1/profile")).andExpect(status().isOk())
                .andExpect(jsonPath("$.skills").value(hasItem("Java")))
                .andExpect(jsonPath("$.languages[*].language").value(hasItem("Inglés")));

        mvc.perform(get("/api/v1/stats")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.saved").value(SEEDED_OFFERS))
                .andExpect(jsonPath("$.totals.applied").value(9))
                .andExpect(jsonPath("$.totals.interviews").value(2))
                .andExpect(jsonPath("$.totals.offersReceived").value(1))
                .andExpect(jsonPath("$.responseRate.sufficientData").value(true))
                .andExpect(jsonPath("$.averageCompatibility.offersScored").value(SEEDED_OFFERS))
                .andExpect(jsonPath("$.recentActivity.length()").value(10))
                .andExpect(jsonPath("$.applicationsOverTime.length()").value(12));
    }

    @Test
    void offerDetailHistoryAndCompatibilityAreReadable() throws Exception {
        String id = firstOfferId();
        mvc.perform(get("/api/v1/offers/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/offers/" + id + "/history")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
        mvc.perform(get("/api/v1/offers/" + id + "/compatibility")).andExpect(status().isOk())
                .andExpect(jsonPath("$.dimensions").isNotEmpty());
        mvc.perform(get("/api/v1/offers/export")).andExpect(status().isOk());
    }

    @Test
    void everyWriteIsRejectedEvenWithAValidCsrfTokenAndNothingChanges() throws Exception {
        TestApi api = TestApi.anonymous(mvc, json);
        String id = firstOfferId();
        String before = mvc.perform(get("/api/v1/offers?size=50")).andReturn().getResponse().getContentAsString();
        Map<String, Object> offer = Map.of("title", "Hackeada", "company", "Intrusa");

        assertReadOnlyRejection(api.post("/api/v1/offers", offer));
        assertReadOnlyRejection(api.put("/api/v1/offers/" + id, offer));
        assertReadOnlyRejection(api.patch("/api/v1/offers/" + id + "/favorite", Map.of("favorite", true)));
        assertReadOnlyRejection(api.post("/api/v1/offers/" + id + "/status", Map.of("status", "REJECTED")));
        assertReadOnlyRejection(api.delete("/api/v1/offers/" + id));
        assertReadOnlyRejection(api.put("/api/v1/profile", Map.of("degree", "Hackeado")));
        assertReadOnlyRejection(api.post("/api/v1/auth/register",
                Map.of("email", "intruso@example.com", "password", TestApi.PASSWORD, "displayName", "Intruso")));
        assertReadOnlyRejection(api.post("/api/v1/auth/login", Map.of("email", "demo@jobtrack.local", "password", "x")));
        assertReadOnlyRejection(api.post("/api/v1/auth/logout", Map.of()));
        assertReadOnlyRejection(api.put("/api/v1/auth/password", Map.of("currentPassword", "x", "newPassword", "y")));
        assertReadOnlyRejection(api.perform(multipart("/api/v1/offers/import").file(
                new MockMultipartFile("file", "ofertas.csv", "text/csv", "title,company\nA,B\n".getBytes(StandardCharsets.UTF_8)))
                .param("dryRun", "false")));

        String after = mvc.perform(get("/api/v1/offers?size=50")).andReturn().getResponse().getContentAsString();
        assertThat(after).isEqualTo(before);
        mvc.perform(get("/api/v1/offers/" + id)).andExpect(status().isOk());
    }

    @Test
    void aWriteWithoutCsrfTokenIsAlsoRejectedAsReadOnlyNotAsUnauthenticated() throws Exception {
        assertReadOnlyRejection(mvc.perform(post("/api/v1/offers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"company\":\"y\"}")));
    }
}
