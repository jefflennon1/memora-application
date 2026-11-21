package br.com.memora.identity.security;

import br.com.memora.shared.config.MemoraProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Para onde o navegador volta ao fim do login com Google.
 *
 * <ul>
 *   <li>Sucesso: caminho memorizado (validado) na origem do frontend, ou o destino padrão
 *       {@code FRONTEND_POST_LOGIN_REDIRECT}. O frontend então consulta {@code GET /api/v1/users/me}.</li>
 *   <li>Falha: {@code FRONTEND_LOGIN_ERROR_REDIRECT?error=<código>}, com {@code account_disabled}
 *       ou {@code login_failed}. Detalhes técnicos não são enviados ao navegador.</li>
 * </ul>
 */
@Component
public class LoginHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginHandlers.class);

    private final MemoraProperties.Frontend frontend;

    public LoginHandlers(MemoraProperties properties) {
        this.frontend = properties.frontend();
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String target = frontend.postLoginRedirect();
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object path = session.getAttribute(RememberingAuthorizationRequestResolver.SESSION_ATTRIBUTE);
            session.removeAttribute(RememberingAuthorizationRequestResolver.SESSION_ATTRIBUTE);
            if (path instanceof String p && LoginRedirectPolicy.sanitizePath(p).isPresent()) {
                target = frontend.normalizedOrigin() + p;
            }
        }
        response.sendRedirect(target);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String code = "login_failed";
        if (exception instanceof OAuth2AuthenticationException oauth
                && MemoraOidcUserService.ACCOUNT_DISABLED_ERROR.equals(oauth.getError().getErrorCode())) {
            code = MemoraOidcUserService.ACCOUNT_DISABLED_ERROR;
        }
        log.info("Login OIDC recusado ({}): {}", code, exception.getMessage());
        response.sendRedirect(UriComponentsBuilder.fromUriString(frontend.loginErrorRedirect())
                .queryParam("error", code)
                .build()
                .toUriString());
    }
}
