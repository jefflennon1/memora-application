package br.com.memora.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import br.com.memora.identity.security.MemoraOidcUser;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Simula o usuário autenticado sem depender do Google: produz exatamente o tipo de autenticação que o
 * login OIDC real produz ({@link OAuth2AuthenticationToken} com {@link MemoraOidcUser}).
 */
public final class WebSecuritySupport {

    private WebSecuritySupport() {
    }

    public static OAuth2AuthenticationToken oidcAuthentication(UUID userId, String subject) {
        Instant now = Instant.now();
        OidcIdToken idToken = OidcIdToken.withTokenValue("test-id-token")
                .issuer("https://accounts.google.com")
                .subject(subject)
                .audience(List.of("test-client-id.apps.googleusercontent.com"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("OIDC_USER"));
        MemoraOidcUser principal = new MemoraOidcUser(userId, authorities, idToken, null);
        return new OAuth2AuthenticationToken(principal, authorities, "google");
    }

    /** Requisição autenticada como a conta indicada. */
    public static RequestPostProcessor asUser(TestUsers.TestUser user) {
        return authentication(oidcAuthentication(user.id(), user.subject()));
    }

    /** Sessão HTTP contendo a autenticação, como após um login real. */
    public static MockHttpSession sessionOf(TestUsers.TestUser user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(oidcAuthentication(user.id(), user.subject())));
        return session;
    }

    /**
     * Token CSRF pelo fluxo real do frontend: o mesmo valor no cookie {@code XSRF-TOKEN} e no cabeçalho
     * {@code X-XSRF-TOKEN}. Não altera o repositório de CSRF da aplicação.
     */
    public static RequestPostProcessor xsrf() {
        String token = UUID.randomUUID().toString();
        return request -> {
            Cookie[] existing = request.getCookies();
            List<Cookie> cookies = new java.util.ArrayList<>(existing == null ? List.of() : List.of(existing));
            cookies.add(new Cookie("XSRF-TOKEN", token));
            request.setCookies(cookies.toArray(Cookie[]::new));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }
}
