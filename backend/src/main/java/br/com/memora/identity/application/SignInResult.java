package br.com.memora.identity.application;

import br.com.memora.identity.domain.UserStatus;
import java.util.UUID;

/**
 * @param userId     conta Memora reconhecida ou criada
 * @param newAccount {@code true} quando a conta foi criada neste login
 */
public record SignInResult(UUID userId, boolean newAccount, UserStatus status) {
}
