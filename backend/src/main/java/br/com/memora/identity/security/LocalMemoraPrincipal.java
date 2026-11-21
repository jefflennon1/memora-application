package br.com.memora.identity.security;

import java.io.Serializable;
import java.util.UUID;

/** Principal mínimo e serializável mantido na sessão de uma conta local. */
public record LocalMemoraPrincipal(UUID userId) implements MemoraPrincipal, Serializable {

    private static final long serialVersionUID = 1L;
}
