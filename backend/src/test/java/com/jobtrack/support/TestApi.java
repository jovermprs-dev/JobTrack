package com.jobtrack.support;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.json.JsonMapper;

/** Cliente de pruebas: mantiene la cookie de sesión de una persona y añade el token CSRF a las escrituras. */
public final class TestApi {

    public static final String PASSWORD = "correct-horse-battery";

    private final MockMvc mvc;
    private final JsonMapper json;
    private Cookie session;
    private String csrfToken;

    private TestApi(MockMvc mvc, JsonMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    /** Persona anónima (sin sesión). */
    public static TestApi anonymous(MockMvc mvc, JsonMapper json) {
        return new TestApi(mvc, json);
    }

    /** Registra una cuenta nueva y devuelve el cliente ya autenticado. */
    public static TestApi register(MockMvc mvc, JsonMapper json, String email) throws Exception {
        TestApi api = new TestApi(mvc, json);
        var result = api.post("/api/v1/auth/register", Map.of("email", email, "password", PASSWORD, "displayName", "Test User"))
                .andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("Registro fallido: " + result.getResponse().getContentAsString());
        }
        api.session = result.getResponse().getCookie("SESSION");
        return api;
    }

    public Cookie sessionCookie() {
        return session;
    }

    public void useSessionFrom(ResultActions login) {
        Cookie cookie = login.andReturn().getResponse().getCookie("SESSION");
        if (cookie != null) {
            this.session = cookie;
        }
    }

    public ResultActions get(String url) throws Exception {
        return perform(MockMvcRequestBuilders.get(url));
    }

    public ResultActions post(String url, Object body) throws Exception {
        return perform(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    public ResultActions put(String url, Object body) throws Exception {
        return perform(MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    public ResultActions patch(String url, Object body) throws Exception {
        return perform(MockMvcRequestBuilders.patch(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    public ResultActions delete(String url) throws Exception {
        return perform(MockMvcRequestBuilders.delete(url));
    }

    public ResultActions perform(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        if (session != null) {
            request.cookie(session);
        }
        // Flujo real de la SPA: se obtiene la cookie XSRF-TOKEN con una petición GET y se reenvía en la cabecera.
        request.cookie(new Cookie("XSRF-TOKEN", csrfToken())).header("X-XSRF-TOKEN", csrfToken());
        return mvc.perform(request);
    }

    private String csrfToken() throws Exception {
        if (csrfToken == null) {
            Cookie cookie = mvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
            if (cookie == null) {
                throw new IllegalStateException("El servidor no emitió la cookie XSRF-TOKEN");
            }
            csrfToken = cookie.getValue();
        }
        return csrfToken;
    }

    /** Crea una oferta con los campos mínimos más los extra indicados y devuelve su id. */
    public String createOffer(String title, String company, Map<String, Object> extra) throws Exception {
        var body = new java.util.HashMap<String, Object>(extra);
        body.put("title", title);
        body.put("company", company);
        String response = post("/api/v1/offers", body).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }
}
