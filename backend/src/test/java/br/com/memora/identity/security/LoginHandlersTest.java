package br.com.memora.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.memora.shared.config.MemoraProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class LoginHandlersTest {

    private final LoginHandlers handlers = new LoginHandlers(new MemoraProperties(
            new MemoraProperties.Frontend("http://localhost:3000/", "http://localhost:3000/inicio",
                    "http://localhost:3000/login"), null));

    @Test
    void successGoesToRememberedPathOnFrontendOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(RememberingAuthorizationRequestResolver.SESSION_ATTRIBUTE, "/invites/abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.onAuthenticationSuccess(request, response, null);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/invites/abc");
        assertThat(request.getSession().getAttribute(RememberingAuthorizationRequestResolver.SESSION_ATTRIBUTE))
                .as("o destino é consumido uma única vez").isNull();
    }

    @Test
    void successWithoutRememberedPathGoesToDefault() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.onAuthenticationSuccess(new MockHttpServletRequest(), response, null);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/inicio");
    }

    @Test
    void tamperedSessionValueIsIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(RememberingAuthorizationRequestResolver.SESSION_ATTRIBUTE, "//evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.onAuthenticationSuccess(request, response, null);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/inicio");
    }

    @Test
    void failureRedirectsWithGenericOrDisabledCode() throws Exception {
        MockHttpServletResponse generic = new MockHttpServletResponse();
        handlers.onAuthenticationFailure(new MockHttpServletRequest(), generic, new BadCredentialsException("x"));
        assertThat(generic.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=login_failed");

        MockHttpServletResponse disabled = new MockHttpServletResponse();
        handlers.onAuthenticationFailure(new MockHttpServletRequest(), disabled,
                new OAuth2AuthenticationException(new OAuth2Error(MemoraOidcUserService.ACCOUNT_DISABLED_ERROR)));
        assertThat(disabled.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=account_disabled");
    }
}
