package br.com.memora.identity.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Inicia o Authorization Code com PKCE e memoriza na sessão o caminho do frontend para onde o usuário
 * deve voltar após o login ({@code ?redirect_to=/invites/abc}), desde que aprovado por
 * {@link LoginRedirectPolicy}. Assim o convite que originou o cadastro é preservado.
 */
public class RememberingAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    public static final String SESSION_ATTRIBUTE =
            RememberingAuthorizationRequestResolver.class.getName() + ".PATH";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public RememberingAuthorizationRequestResolver(ClientRegistrationRepository registrations) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                registrations, OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        this.delegate.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return remember(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return remember(request, delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest remember(HttpServletRequest request, OAuth2AuthorizationRequest authRequest) {
        if (authRequest == null) {
            return null;
        }
        var path = LoginRedirectPolicy.sanitizePath(request.getParameter(LoginRedirectPolicy.PARAMETER));
        if (path.isPresent()) {
            request.getSession().setAttribute(SESSION_ATTRIBUTE, path.get());
        } else {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.removeAttribute(SESSION_ATTRIBUTE);
            }
        }
        return authRequest;
    }
}
