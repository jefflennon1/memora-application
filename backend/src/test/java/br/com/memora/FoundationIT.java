package br.com.memora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import jakarta.servlet.http.Cookie;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.memora.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Entrega 1 — a aplicação inicia contra PostgreSQL real, aplica as migrations Flyway, valida o schema
 * com Hibernate e expõe health, OpenAPI e a configuração base de segurança.
 */
@IntegrationTest
class FoundationIT {

    private static final String FRONTEND = "http://localhost:3000";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayAppliesVersionedMigrations() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success and version = '1'", Integer.class);
        assertThat(applied).isEqualTo(1);
        Integer latest = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success and version = '2'", Integer.class);
        assertThat(latest).isEqualTo(1);
        Integer categories = jdbc.queryForObject("select count(*) from event_category", Integer.class);
        assertThat(categories).isEqualTo(17);
    }

    @Test
    void healthIsUpAndPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void openApiDocumentsTheApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Memora API"))
                .andExpect(jsonPath("$.paths['/api/v1/event-categories']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me/onboarding']").exists());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/register']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void eventCategoriesArePublicAndOrdered() throws Exception {
        mvc.perform(get("/api/v1/event-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(17)))
                .andExpect(jsonPath("$[0].code").value("BABY_SHOWER"))
                .andExpect(jsonPath("$[16].code").value("OTHER"));
    }

    @Test
    void protectedApiReturns401ProblemDetailsInsteadOfRedirect() throws Exception {
        mvc.perform(get("/api/v1/users/me").accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void stateChangingRequestsRequireCsrfToken() throws Exception {
        mvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Fluxo real do frontend: lê o cookie XSRF-TOKEN e o devolve no cabeçalho X-XSRF-TOKEN.
        Cookie xsrf = mvc.perform(get("/api/v1/event-categories"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();

        mvc.perform(post("/api/v1/auth/logout").cookie(xsrf).header("X-XSRF-TOKEN", "valor-incorreto"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/auth/logout").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent());
    }

    @Test
    void csrfCookieIsIssuedForTheFrontend() throws Exception {
        mvc.perform(get("/api/v1/event-categories"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    void corsAllowsOnlyTheConfiguredFrontendOrigin() throws Exception {
        mvc.perform(options("/api/v1/event-categories")
                        .header(HttpHeaders.ORIGIN, FRONTEND)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mvc.perform(options("/api/v1/event-categories")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void unknownErrorsDoNotLeakInternals() throws Exception {
        mvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("password"))));
    }
}
