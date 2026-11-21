package br.com.memora.profile;

import static br.com.memora.support.WebSecuritySupport.asUser;
import static br.com.memora.support.WebSecuritySupport.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.memora.support.IntegrationTest;
import br.com.memora.support.TestUsers;
import br.com.memora.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Entrega 2 — cadastro inicial obrigatório, unicidade do @usuário e edição do perfil.
 */
@IntegrationTest
class OnboardingIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions completeOnboarding(TestUser user, String json) throws Exception {
        return mvc.perform(patch("/api/v1/users/me/onboarding").with(asUser(user)).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void newAccountSeesProviderSuggestionsAndPendingOnboarding() throws Exception {
        TestUser user = users.pending();

        mvc.perform(get("/api/v1/users/me").with(asUser(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.id().toString()))
                .andExpect(jsonPath("$.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.status").value("PENDING_ONBOARDING"))
                .andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.displayName").value("Pessoa Teste"))
                .andExpect(jsonPath("$.photoUrl").value("https://lh3.googleusercontent.com/a/test"))
                .andExpect(jsonPath("$.identities[0].provider").value("GOOGLE"));
    }

    @Test
    void normalActionsAreBlockedUntilOnboardingIsCompleted() throws Exception {
        TestUser user = users.pending();

        mvc.perform(patch("/api/v1/users/me").with(asUser(user)).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"X\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ONBOARDING_REQUIRED"));
    }

    @Test
    void completingOnboardingActivatesTheAccount() throws Exception {
        TestUser user = users.pending();
        String username = TestUsers.randomUsername();

        completeOnboarding(user, """
                {"username":"@%s","displayName":"  Ana Souza  ","keepSuggestedPhoto":false,"profilePrivacy":"PRIVATE"}
                """.formatted(username.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.displayName").value("Ana Souza"))
                .andExpect(jsonPath("$.photoUrl").doesNotExist())
                .andExpect(jsonPath("$.profilePrivacy").value("PRIVATE"));

        // Ações normais passam a ser permitidas.
        mvc.perform(patch("/api/v1/users/me").with(asUser(user)).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Ana S.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ana S."));
    }

    @Test
    void keepingSuggestedPhotoIsTheDefault() throws Exception {
        TestUser user = users.pending();

        completeOnboarding(user, """
                {"username":"%s","displayName":"Ana"}
                """.formatted(TestUsers.randomUsername()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value("https://lh3.googleusercontent.com/a/test"))
                .andExpect(jsonPath("$.profilePrivacy").value("PUBLIC"));
    }

    @Test
    void usernameIsUniqueCaseInsensitively() throws Exception {
        TestUser owner = users.active();
        TestUser other = users.pending();

        completeOnboarding(other, """
                {"username":"%s","displayName":"Outra Pessoa"}
                """.formatted(owner.username().toUpperCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));

        mvc.perform(get("/api/v1/users/username-availability").param("username", "@" + owner.username())
                        .with(asUser(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("USERNAME_TAKEN"));

        // O próprio @usuário conta como disponível para o dono.
        mvc.perform(get("/api/v1/users/username-availability").param("username", owner.username())
                        .with(asUser(owner)))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void invalidAndReservedUsernamesAreRejected() throws Exception {
        TestUser user = users.pending();

        completeOnboarding(user, "{\"username\":\"a\",\"displayName\":\"Ana\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_USERNAME"));
        completeOnboarding(user, "{\"username\":\"joão\",\"displayName\":\"Ana\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_USERNAME"));
        completeOnboarding(user, "{\"username\":\"admin\",\"displayName\":\"Ana\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USERNAME_RESERVED"));
        completeOnboarding(user, "{\"username\":\"valido1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("displayName"));

        mvc.perform(get("/api/v1/users/me").with(asUser(user)))
                .andExpect(jsonPath("$.onboardingCompleted").value(false));
    }

    @Test
    void onboardingCanOnlyBeCompletedOnce() throws Exception {
        TestUser user = users.active();

        completeOnboarding(user, """
                {"username":"%s","displayName":"De Novo"}
                """.formatted(TestUsers.randomUsername()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ONBOARDING_ALREADY_COMPLETED"));
    }

    @Test
    void changingUsernameRespectsUniqueness() throws Exception {
        TestUser a = users.active();
        TestUser b = users.active();
        String fresh = TestUsers.randomUsername();

        mvc.perform(patch("/api/v1/users/me").with(asUser(a)).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + b.username() + "\"}"))
                .andExpect(status().isConflict());

        mvc.perform(patch("/api/v1/users/me").with(asUser(a)).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + fresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(fresh));
    }

    @Test
    void suspendedAccountIsBlockedEverywhere() throws Exception {
        TestUser user = users.active();
        jdbc.update("update app_user set status = 'SUSPENDED' where id = ?", user.id());

        mvc.perform(get("/api/v1/users/me").with(asUser(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void onboardingRequiresCsrfToken() throws Exception {
        TestUser user = users.pending();

        mvc.perform(patch("/api/v1/users/me/onboarding").with(asUser(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + TestUsers.randomUsername() + "\",\"displayName\":\"Ana\"}"))
                .andExpect(status().isForbidden());
    }
}
