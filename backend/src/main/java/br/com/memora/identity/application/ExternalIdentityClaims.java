package br.com.memora.identity.application;

import br.com.memora.identity.domain.IdentityProvider;
import java.util.Objects;

/**
 * Dados de uma identidade já autenticada e validada pelo provedor (ID Token OIDC verificado).
 *
 * @param subject       {@code sub} do OpenID Connect: referência estável da identidade
 * @param email         e-mail informado pelo provedor (informativo, pode ser nulo)
 * @param emailVerified indicador de verificação do e-mail, quando o provedor informa
 * @param name          nome sugerido
 * @param pictureUrl    foto sugerida
 */
public record ExternalIdentityClaims(IdentityProvider provider, String subject, String email,
                                     Boolean emailVerified, String name, String pictureUrl) {

    public ExternalIdentityClaims {
        Objects.requireNonNull(provider, "provider");
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("O identificador (sub) do provedor é obrigatório.");
        }
    }
}
