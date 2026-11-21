package br.com.memora.identity.application;

import br.com.memora.identity.domain.User;
import br.com.memora.identity.domain.UserRepository;
import br.com.memora.identity.domain.UserStatus;
import br.com.memora.shared.error.ForbiddenException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta o estado da conta autenticada. Usado pelo bloqueio de onboarding e pelos demais módulos
 * para exigir uma conta ativa antes de qualquer ação normal na plataforma.
 */
@Service
public class CurrentAccountService {

    private final UserRepository users;

    public CurrentAccountService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserStatus statusOf(UUID userId) {
        return users.findById(userId).map(User::getStatus).orElse(UserStatus.DELETED);
    }

    /** Garante que a conta existe, está ativa e concluiu o onboarding. */
    @Transactional(readOnly = true)
    public void requireActive(UUID userId) {
        UserStatus status = statusOf(userId);
        if (status.isDisabled()) {
            throw new AccountDisabledException();
        }
        if (status == UserStatus.PENDING_ONBOARDING) {
            throw new ForbiddenException("ONBOARDING_REQUIRED",
                    "Conclua seu cadastro inicial (nome, foto e @usuário) para continuar.");
        }
    }
}
