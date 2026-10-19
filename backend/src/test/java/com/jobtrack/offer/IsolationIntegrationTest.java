package com.jobtrack.offer;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrack.support.IntegrationTest;
import com.jobtrack.support.TestApi;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Una persona nunca puede leer ni modificar los datos de otra: el recurso ajeno responde 404. */
@IntegrationTest
class IsolationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;

    @Test
    void otherUsersCannotReadModifyOrDeleteMyOffers() throws Exception {
        TestApi alice = TestApi.register(mvc, json, "alice-" + UUID.randomUUID() + "@example.com");
        TestApi mallory = TestApi.register(mvc, json, "mallory-" + UUID.randomUUID() + "@example.com");
        String id = alice.createOffer("Secret job", "Hidden Corp", Map.of("skills", List.of("Java")));

        mallory.get("/api/v1/offers/" + id).andExpect(status().isNotFound());
        mallory.put("/api/v1/offers/" + id, Map.of("title", "Hacked", "company", "Evil")).andExpect(status().isNotFound());
        mallory.delete("/api/v1/offers/" + id).andExpect(status().isNotFound());
        mallory.patch("/api/v1/offers/" + id + "/favorite", Map.of("favorite", true)).andExpect(status().isNotFound());
        mallory.post("/api/v1/offers/" + id + "/status", Map.of("status", "REJECTED")).andExpect(status().isNotFound());
        mallory.get("/api/v1/offers/" + id + "/history").andExpect(status().isNotFound());
        mallory.get("/api/v1/offers/" + id + "/compatibility").andExpect(status().isNotFound());

        mallory.get("/api/v1/offers").andExpect(jsonPath("$.totalElements").value(0));
        mallory.get("/api/v1/offers?q=secret").andExpect(jsonPath("$.totalElements").value(0));
        mallory.get("/api/v1/stats").andExpect(jsonPath("$.totals.saved").value(0))
                .andExpect(jsonPath("$.recentActivity.length()").value(0));

        // la oferta de Alice sigue intacta
        alice.get("/api/v1/offers/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Secret job"))
                .andExpect(jsonPath("$.favorite").value(false)).andExpect(jsonPath("$.status").value("SAVED"));
        alice.get("/api/v1/offers/" + id + "/history").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void profilesAreIndependentPerUser() throws Exception {
        TestApi alice = TestApi.register(mvc, json, "alice-" + UUID.randomUUID() + "@example.com");
        TestApi bob = TestApi.register(mvc, json, "bob-" + UUID.randomUUID() + "@example.com");
        alice.put("/api/v1/profile", Map.of("skills", List.of("Rust"), "location", "Oslo")).andExpect(status().isOk());

        bob.get("/api/v1/profile").andExpect(jsonPath("$.skills").isEmpty()).andExpect(jsonPath("$.location").value((Object) null));
        bob.put("/api/v1/profile", Map.of("skills", List.of("Go"))).andExpect(status().isOk());
        alice.get("/api/v1/profile").andExpect(jsonPath("$.skills", hasItem("Rust"))).andExpect(jsonPath("$.skills", not(hasItem("Go"))));
    }

    @Test
    void recalculatingOneProfileDoesNotAffectOthersOffers() throws Exception {
        TestApi alice = TestApi.register(mvc, json, "alice-" + UUID.randomUUID() + "@example.com");
        TestApi bob = TestApi.register(mvc, json, "bob-" + UUID.randomUUID() + "@example.com");
        String bobOffer = bob.createOffer("Java Dev", "B", Map.of("skills", List.of("Java")));

        alice.put("/api/v1/profile", Map.of("skills", List.of("Java"))).andExpect(status().isOk());

        bob.get("/api/v1/offers/" + bobOffer).andExpect(jsonPath("$.compatibilityScore").value((Object) null));
    }
}
