package br.com.memora.identity.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Credenciais OAuth 2.0 do Google ({@code memora.auth.google.*}), vindas de
 * {@code GOOGLE_CLIENT_ID} e {@code GOOGLE_CLIENT_SECRET}.
 */
@ConfigurationProperties(prefix = "memora.auth.google")
public record GoogleOAuthProperties(String clientId, String clientSecret) {

    public boolean isConfigured() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }
}
