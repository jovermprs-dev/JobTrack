package com.jobtrack.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrack.support.IntegrationTest;
import com.jobtrack.support.TestApi;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class AuthIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;

    private static String email() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void registerCreatesSessionAndNeverExposesPassword() throws Exception {
        TestApi anon = TestApi.anonymous(mvc, json);
        String email = email();

        anon.post("/api/v1/auth/register", Map.of("email", email, "password", TestApi.PASSWORD, "displayName", "Ana"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Ana"))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(cookie().exists("SESSION"))
                .andExpect(cookie().httpOnly("SESSION", true))
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(startsWith("SESSION="), containsString("HttpOnly"), containsString("SameSite=Lax")))));
    }

    @Test
    void passwordIsStoredAsBcryptHash() throws Exception {
        String email = email();
        TestApi.register(mvc, json, email);

        String hash = jdbc.queryForObject("select password_hash from users where email = ?", String.class, email);
        assertThat(hash).startsWith("$2").doesNotContain(TestApi.PASSWORD);
    }

    @Test
    void duplicateEmailIsRejectedIgnoringCase() throws Exception {
        String email = email();
        TestApi.register(mvc, json, email);

        TestApi.anonymous(mvc, json)
                .post("/api/v1/auth/register", Map.of("email", email.toUpperCase(), "password", TestApi.PASSWORD, "displayName", "Otro"))
                .andExpect(status().isConflict());
    }

    @Test
    void registrationValidatesInput() throws Exception {
        TestApi.anonymous(mvc, json)
                .post("/api/v1/auth/register", Map.of("email", "no-es-email", "password", "corta", "displayName", ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void meRequiresSessionAndReturnsCurrentUser() throws Exception {
        TestApi.anonymous(mvc, json).get("/api/v1/auth/me").andExpect(status().isUnauthorized());

        String email = email();
        TestApi user = TestApi.register(mvc, json, email);
        user.get("/api/v1/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void loginSucceedsWithCorrectCredentialsAndFailsGenericallyOtherwise() throws Exception {
        String email = email();
        TestApi.register(mvc, json, email);

        TestApi again = TestApi.anonymous(mvc, json);
        var ok = again.post("/api/v1/auth/login", Map.of("email", email.toUpperCase(), "password", TestApi.PASSWORD));
        ok.andExpect(status().isOk()).andExpect(cookie().exists("SESSION"));
        again.useSessionFrom(ok);
        again.get("/api/v1/auth/me").andExpect(status().isOk());

        // contraseña incorrecta y usuario inexistente producen la misma respuesta (sin enumeración de cuentas)
        String wrongPassword = TestApi.anonymous(mvc, json).post("/api/v1/auth/login", Map.of("email", email, "password", "x-wrong-password"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownUser = TestApi.anonymous(mvc, json).post("/api/v1/auth/login", Map.of("email", email(), "password", "x-wrong-password"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(wrongPassword).isEqualTo(unknownUser);
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        TestApi user = TestApi.register(mvc, json, email());
        user.get("/api/v1/auth/me").andExpect(status().isOk());

        user.post("/api/v1/auth/logout", Map.of()).andExpect(status().isNoContent());

        user.get("/api/v1/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void stateChangingRequestsWithoutCsrfTokenAreRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"whatever-long\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfCookieIsIssuedToAnonymousClientsAndReadableByScripts() throws Exception {
        var response = mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized()).andReturn().getResponse();

        assertThat(response.getHeaders("Set-Cookie"))
                .as("la cookie XSRF-TOKEN debe poder leerse desde JS (sin HttpOnly) para que Angular la reenvíe")
                .anySatisfy(c -> assertThat(c).startsWith("XSRF-TOKEN=").doesNotContain("HttpOnly"))
                .as("un 401 anónimo no debe crear una sesión")
                .noneMatch(c -> c.startsWith("SESSION="));
    }

    @Test
    void repeatedFailedLoginsAreThrottled() throws Exception {
        String email = email();
        TestApi.register(mvc, json, email);
        TestApi anon = TestApi.anonymous(mvc, json);
        for (int i = 0; i < 5; i++) {
            anon.post("/api/v1/auth/login", Map.of("email", email, "password", "bad-password-" + i)).andExpect(status().isUnauthorized());
        }
        anon.post("/api/v1/auth/login", Map.of("email", email, "password", TestApi.PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void changePasswordRequiresCurrentPasswordAndInvalidatesOldOne() throws Exception {
        String email = email();
        TestApi user = TestApi.register(mvc, json, email);

        user.put("/api/v1/auth/password", Map.of("currentPassword", "not-the-password", "newPassword", "brand-new-password-1"))
                .andExpect(status().isBadRequest());
        user.put("/api/v1/auth/password", Map.of("currentPassword", TestApi.PASSWORD, "newPassword", "brand-new-password-1"))
                .andExpect(status().isNoContent());

        TestApi.anonymous(mvc, json).post("/api/v1/auth/login", Map.of("email", email, "password", TestApi.PASSWORD))
                .andExpect(status().isUnauthorized());
        TestApi.anonymous(mvc, json).post("/api/v1/auth/login", Map.of("email", email, "password", "brand-new-password-1"))
                .andExpect(status().isOk());
    }

    @Test
    void privateEndpointsRequireAuthentication() throws Exception {
        TestApi anon = TestApi.anonymous(mvc, json);
        for (String url : new String[] {"/api/v1/offers", "/api/v1/profile", "/api/v1/stats"}) {
            anon.get(url).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
