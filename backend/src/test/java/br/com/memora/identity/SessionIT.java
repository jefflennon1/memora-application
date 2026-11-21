package br.com.memora.identity;

import static br.com.memora.support.WebSecuritySupport.sessionOf;
import static br.com.memora.support.WebSecuritySupport.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.memora.support.IntegrationTest;
import br.com.memora.support.TestUsers;
import br.com.memora.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Entrega 2 — proteção dos endpoints sem sessão, uso da sessão e logout.
 */
@IntegrationTest
class SessionIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Test
    void endpointsWithoutSessionReturn401WithoutRedirect() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(patch("/api/v1/users/me/onboarding").with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/username-availability").param("username", "ana"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void springDefaultLoginPageAndFormEndpointStayDisabled() throws Exception {
        mvc.perform(post("/login").with(xsrf()).param("username", "x").param("password", "y"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/login")).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionAuthenticatesRequestsUntilLogout() throws Exception {
        TestUser user = users.active();
        MockHttpSession session = sessionOf(user);

        mvc.perform(get("/api/v1/users/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.id().toString()));

        mvc.perform(post("/api/v1/auth/logout").session(session).with(xsrf()))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRequiresCsrfToken() throws Exception {
        TestUser user = users.active();
        MockHttpSession session = sessionOf(user);

        mvc.perform(post("/api/v1/auth/logout").session(session))
                .andExpect(status().isForbidden());
        assertThat(session.isInvalid()).isFalse();
    }
}
