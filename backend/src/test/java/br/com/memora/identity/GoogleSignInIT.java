package br.com.memora.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.memora.identity.application.AccountDisabledException;
import br.com.memora.identity.application.ExternalIdentityClaims;
import br.com.memora.identity.application.SignInResult;
import br.com.memora.identity.application.SignInService;
import br.com.memora.identity.domain.IdentityProvider;
import br.com.memora.identity.domain.UserStatus;
import br.com.memora.identity.security.RememberingAuthorizationRequestResolver;
import br.com.memora.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Entrega 2 — reconhecimento da identidade Google e criação/recuperação da conta interna.
 * O Google é simulado: o ID Token já validado é entregue diretamente ao serviço da Memora.
 */
@IntegrationTest
class GoogleSignInIT {

    @Autowired
    SignInService signIn;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    private static ExternalIdentityClaims google(String sub, String email) {
        return new ExternalIdentityClaims(IdentityProvider.GOOGLE, sub, email, true, "Ana Souza",
                "https://lh3.googleusercontent.com/a/ana");
    }

    private static String newSub() {
        return "1" + Math.abs(UUID.randomUUID().getMostSignificantBits());
    }

    @Test
    void firstLoginCreatesPendingInternalAccountWithOwnUuid() {
        String sub = newSub();

        SignInResult result = signIn.signIn(google(sub, "ana@example.test"));

        assertThat(result.newAccount()).isTrue();
        assertThat(result.status()).isEqualTo(UserStatus.PENDING_ONBOARDING);
        Map<String, Object> user = jdbc.queryForMap("select * from app_user where id = ?", result.userId());
        assertThat(user.get("status")).isEqualTo("PENDING_ONBOARDING");
        assertThat(user.get("username")).as("@usuário não é derivado do e-mail").isNull();
        assertThat(user.get("display_name")).isEqualTo("Ana Souza");
        assertThat(user.get("photo_url")).isEqualTo("https://lh3.googleusercontent.com/a/ana");

        Map<String, Object> identity = jdbc.queryForMap(
                "select * from external_identity where provider = 'GOOGLE' and provider_subject = ?", sub);
        assertThat(identity.get("user_id")).isEqualTo(result.userId());
        assertThat(identity.get("email")).isEqualTo("ana@example.test");
        assertThat(identity.get("email_verified")).isEqualTo(true);
    }

    @Test
    void laterLoginsRecoverTheSameAccountBySubEvenIfEmailChanges() {
        String sub = newSub();
        SignInResult first = signIn.signIn(google(sub, "antigo@example.test"));

        SignInResult second = signIn.signIn(google(sub, "novo@example.test"));

        assertThat(second.newAccount()).isFalse();
        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(jdbc.queryForObject("select count(*) from external_identity where provider_subject = ?",
                Integer.class, sub)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select email from external_identity where provider_subject = ?",
                String.class, sub)).isEqualTo("novo@example.test");
    }

    @Test
    void sameEmailWithDifferentSubNeverMergesAccounts() {
        String email = "compartilhado-" + UUID.randomUUID() + "@example.test";

        SignInResult a = signIn.signIn(google(newSub(), email));
        SignInResult b = signIn.signIn(google(newSub(), email));

        assertThat(a.userId()).isNotEqualTo(b.userId());
        assertThat(b.newAccount()).isTrue();
    }

    @Test
    void disabledAccountCannotSignIn() {
        String sub = newSub();
        SignInResult result = signIn.signIn(google(sub, null));
        jdbc.update("update app_user set status = 'SUSPENDED' where id = ?", result.userId());

        assertThatThrownBy(() -> signIn.signIn(google(sub, null))).isInstanceOf(AccountDisabledException.class);
    }

    @Test
    void loginStartsAuthorizationCodeWithPkceAndMinimalScopes() throws Exception {
        MvcResult result = mvc.perform(get("/oauth2/authorization/google").param("redirect_to", "/invites/abc"))
                .andExpect(status().isFound())
                .andReturn();

        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth");
        var params = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertThat(params.getFirst("response_type")).isEqualTo("code");
        assertThat(params.getFirst("client_id")).isEqualTo("test-client-id.apps.googleusercontent.com");
        assertThat(params.getFirst("scope")).isEqualTo("openid%20profile%20email");
        assertThat(params.getFirst("redirect_uri")).isEqualTo("http://localhost/login/oauth2/code/google");
        assertThat(params.getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(params.getFirst("code_challenge")).isNotBlank();
        assertThat(params.getFirst("state")).isNotBlank();
        assertThat(params.getFirst("nonce")).isNotBlank();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session.getAttribute(RememberingAuthorizationRequestResolver.SESSION_ATTRIBUTE))
                .isEqualTo("/invites/abc");
    }

    @Test
    void unsafeRedirectTargetsAreNotRemembered() throws Exception {
        for (String evil : List.of("https://evil.example/x", "//evil.example", "/\\evil.example", "javascript:x")) {
            MvcResult result = mvc.perform(get("/oauth2/authorization/google").param("redirect_to", evil))
                    .andExpect(status().isFound())
                    .andReturn();
            MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
            assertThat(session.getAttribute(RememberingAuthorizationRequestResolver.SESSION_ATTRIBUTE))
                    .as(evil).isNull();
        }
    }
}
