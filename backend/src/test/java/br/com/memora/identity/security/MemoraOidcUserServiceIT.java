package br.com.memora.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.memora.identity.application.SignInService;
import br.com.memora.support.IntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Etapa do login em que o ID Token (já validado pelo Spring Security) é associado à conta Memora.
 * O delegate simula a resposta do Google; nenhuma chamada de rede é feita.
 */
@IntegrationTest
class MemoraOidcUserServiceIT {

    @Autowired
    SignInService signIn;

    @Autowired
    JdbcTemplate jdbc;

    private static OidcIdToken googleIdToken(String sub) {
        return OidcIdToken.withTokenValue("id-token")
                .issuer("https://accounts.google.com").subject(sub)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("email", "bia@example.test").claim("email_verified", true)
                .claim("name", "Bia Lima").claim("picture", "https://lh3.googleusercontent.com/a/bia")
                .build();
    }

    private MemoraOidcUserService serviceReturning(OidcIdToken idToken) {
        OidcUser googleUser = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        return new MemoraOidcUserService(request -> googleUser, signIn);
    }

    private static OidcUserRequest request(OidcIdToken idToken) {
        return new OidcUserRequest(
                CommonOAuth2Provider.GOOGLE.getBuilder("google").clientId("c").clientSecret("s").build(),
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "t", Instant.now(),
                        Instant.now().plusSeconds(60)),
                idToken);
    }

    @Test
    void bindsGoogleIdentityToInternalAccountAndExposesOnlyTheInternalId() {
        OidcIdToken idToken = googleIdToken("sub-" + UUID.randomUUID());
        MemoraOidcUserService service = serviceReturning(idToken);

        OidcUser first = service.loadUser(request(idToken));
        OidcUser second = service.loadUser(request(idToken));

        assertThat(first).isInstanceOf(MemoraOidcUser.class);
        UUID userId = ((MemoraOidcUser) first).userId();
        assertThat(first.getName()).isEqualTo(userId.toString()).doesNotContain("bia@");
        assertThat(((MemoraOidcUser) second).userId()).as("mesmo sub → mesma conta").isEqualTo(userId);
        assertThat(jdbc.queryForObject("select display_name from app_user where id = ?", String.class, userId))
                .isEqualTo("Bia Lima");
    }

    @Test
    void disabledAccountBecomesOAuth2ErrorInsteadOfSession() {
        OidcIdToken idToken = googleIdToken("sub-" + UUID.randomUUID());
        MemoraOidcUserService service = serviceReturning(idToken);
        UUID userId = ((MemoraOidcUser) service.loadUser(request(idToken))).userId();
        jdbc.update("update app_user set status = 'DELETED' where id = ?", userId);

        assertThatThrownBy(() -> service.loadUser(request(idToken)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                        .isEqualTo(MemoraOidcUserService.ACCOUNT_DISABLED_ERROR));
    }
}
