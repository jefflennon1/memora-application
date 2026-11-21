package br.com.memora.identity.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Registro do Google como provedor OpenID Connect.
 *
 * <p>Callback: {@code {baseUrl}/login/oauth2/code/google}. Escopos: {@code openid profile email}.
 * Sem credenciais configuradas a aplicação inicia normalmente (útil para testes e desenvolvimento
 * sem Google), mas o login responde HTTP 503 explicando o que falta.
 */
@Configuration(proxyBeanMethods = false)
public class OAuthClientConfig {

    public static final String GOOGLE = "google";

    private static final Logger log = LoggerFactory.getLogger(OAuthClientConfig.class);

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(GoogleOAuthProperties google) {
        if (!google.isConfigured()) {
            log.warn("Login com Google DESABILITADO: defina GOOGLE_CLIENT_ID e GOOGLE_CLIENT_SECRET "
                    + "(veja o README). /oauth2/authorization/google responderá 503.");
            return registrationId -> null;
        }
        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder(GOOGLE)
                .clientId(google.clientId())
                .clientSecret(google.clientSecret())
                .scope("openid", "profile", "email")
                .build());
    }
}
