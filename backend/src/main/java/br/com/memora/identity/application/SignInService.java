package br.com.memora.identity.application;

import br.com.memora.identity.domain.ExternalIdentity;
import br.com.memora.identity.domain.ExternalIdentityRepository;
import br.com.memora.identity.domain.User;
import br.com.memora.identity.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reconhece ou cria a conta Memora a partir de uma identidade externa autenticada.
 *
 * <ul>
 *   <li>A identidade é procurada somente por {@code (provider, sub)}.</li>
 *   <li>Se não existir, é criada uma conta nova e provisória, mesmo que outra conta tenha o mesmo e-mail:
 *       contas nunca são unidas automaticamente por e-mail.</li>
 *   <li>Contas suspensas ou excluídas não podem entrar.</li>
 * </ul>
 */
@Service
public class SignInService {

    private static final Logger log = LoggerFactory.getLogger(SignInService.class);

    private final UserRepository users;
    private final ExternalIdentityRepository identities;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SignInService(UserRepository users, ExternalIdentityRepository identities,
                         PlatformTransactionManager transactionManager, Clock clock) {
        this.users = users;
        this.identities = identities;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public SignInResult signIn(ExternalIdentityClaims claims) {
        try {
            return tx.execute(status -> doSignIn(claims));
        } catch (DataIntegrityViolationException concurrentFirstLogin) {
            // Dois primeiros logins simultâneos da mesma identidade: o outro venceu; recupera a conta dele.
            log.info("Login concorrente detectado para {}; recuperando a conta existente.", claims.provider());
            return tx.execute(status -> doSignIn(claims));
        }
    }

    private SignInResult doSignIn(ExternalIdentityClaims claims) {
        Instant now = clock.instant();
        Optional<ExternalIdentity> existing =
                identities.findByProviderSubject(claims.provider(), claims.subject());

        if (existing.isPresent()) {
            ExternalIdentity identity = existing.get();
            User user = identity.getUser();
            if (user.isDisabled()) {
                throw new AccountDisabledException();
            }
            identity.recordLogin(claims.email(), claims.emailVerified(), now);
            user.refreshProviderSuggestions(claims.name(), claims.pictureUrl());
            return new SignInResult(user.getId(), false, user.getStatus());
        }

        User user = users.save(User.newPendingAccount(claims.name(), claims.pictureUrl(), now));
        identities.saveAndFlush(ExternalIdentity.link(
                user, claims.provider(), claims.subject(), claims.email(), claims.emailVerified(), now));
        log.info("Nova conta Memora {} criada a partir de identidade {}.", user.getId(), claims.provider());
        return new SignInResult(user.getId(), true, user.getStatus());
    }
}
