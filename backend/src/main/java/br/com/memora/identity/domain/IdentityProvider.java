package br.com.memora.identity.domain;

import java.util.Locale;

/** Provedores de identidade OpenID Connect suportados (ou previstos) pela Memora. */
public enum IdentityProvider {
    GOOGLE,
    /** Previsto: vinculação futura. */
    APPLE,
    /** Previsto: vinculação futura. */
    FACEBOOK;

    /** Converte o {@code registrationId} do Spring Security (ex.: {@code google}) no provedor. */
    public static IdentityProvider fromRegistrationId(String registrationId) {
        return IdentityProvider.valueOf(registrationId.toUpperCase(Locale.ROOT));
    }
}
