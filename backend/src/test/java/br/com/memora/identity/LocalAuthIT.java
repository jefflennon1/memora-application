package br.com.memora.identity;

import static br.com.memora.support.WebSecuritySupport.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.memora.support.IntegrationTest;
import br.com.memora.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
class LocalAuthIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void registersActiveAccountWithPrivatePhoneAndStartsSession() throws Exception {
        String username = TestUsers.randomUsername();

        mvc.perform(get("/api/v1/auth/username-availability").param("username", "@" + username.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.available").value(true));

        MvcResult registered = mvc.perform(post("/api/v1/auth/register").with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, "senha-segura", "senha-segura", "(11) 99999-1234")))
                .andExpect(status().isCreated())
                .andReturn();

        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/v1/users/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.displayName").value(username))
                .andExpect(jsonPath("$.phone").value("11999991234"))
                .andExpect(jsonPath("$.passwordLogin").value(true))
                .andExpect(jsonPath("$.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.identities").isEmpty());

        String hash = jdbc.queryForObject("""
                select lc.password_hash from local_credential lc
                join app_user u on u.id = lc.user_id where u.username = ?
                """, String.class, username);
        assertThat(hash).startsWith("$2").doesNotContain("senha-segura");

        mvc.perform(get("/api/v1/auth/username-availability").param("username", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("USERNAME_TAKEN"));
    }

    @Test
    void logsInWithNormalizedUsernameAndKeepsCredentialErrorsGeneric() throws Exception {
        String username = TestUsers.randomUsername();
        mvc.perform(post("/api/v1/auth/register").with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, "senha-segura", "senha-segura", "11999991234")))
                .andExpect(status().isCreated());

        MvcResult loggedIn = mvc.perform(post("/api/v1/auth/login").with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("@" + username.toUpperCase(), "senha-segura")))
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loggedIn.getRequest().getSession(false);
        mvc.perform(get("/api/v1/users/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        mvc.perform(post("/api/v1/auth/login").with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, "senha-errada")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/v1/auth/login").with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("usuario.inexistente", "senha-errada")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void rejectsDifferentPasswordsAndInvalidPhone() throws Exception {
        mvc.perform(post("/api/v1/auth/register").with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(TestUsers.randomUsername(), "senha-segura", "outra-senha", "11999991234")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORDS_DO_NOT_MATCH"));

        mvc.perform(post("/api/v1/auth/register").with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(TestUsers.randomUsername(), "senha-segura", "senha-segura", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PHONE"));
    }

    private static String registerBody(String username, String password, String confirmation, String phone) {
        return """
                {"username":"%s","password":"%s","passwordConfirmation":"%s","phone":"%s"}
                """.formatted(username, password, confirmation, phone);
    }

    private static String loginBody(String username, String password) {
        return """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
    }
}
