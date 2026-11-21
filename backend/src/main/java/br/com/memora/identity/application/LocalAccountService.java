package br.com.memora.identity.application;

import br.com.memora.identity.domain.LocalCredential;
import br.com.memora.identity.domain.LocalCredentialRepository;
import br.com.memora.identity.domain.PhoneNumber;
import br.com.memora.identity.domain.User;
import br.com.memora.identity.domain.UserRepository;
import br.com.memora.profile.domain.Username;
import br.com.memora.shared.error.ConflictException;
import br.com.memora.shared.error.ValidationException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro de uma conta local. A senha em texto existe somente durante esta chamada. */
@Service
public class LocalAccountService {

    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX_BYTES = 72;

    private final UserRepository users;
    private final LocalCredentialRepository credentials;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public LocalAccountService(UserRepository users, LocalCredentialRepository credentials,
                               PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UsernameAvailability availability(String raw) {
        String normalized = Username.normalize(raw);
        Optional<String> problem = Username.problemWith(normalized);
        if (problem.isPresent()) {
            return new UsernameAvailability(normalized, false, problem.get());
        }
        boolean taken = users.existsByUsername(normalized);
        return new UsernameAvailability(normalized, !taken, taken ? "USERNAME_TAKEN" : null);
    }

    @Transactional
    public RegisteredAccount register(RegisterCommand command) {
        String username = Username.parse(command.username());
        String phone = PhoneNumber.parse(command.phone());
        validatePassword(command.password(), command.passwordConfirmation());
        if (users.existsByUsername(username)) {
            throw usernameTaken();
        }

        Instant now = clock.instant();
        User user = users.save(User.newLocalAccount(username, phone, now));
        credentials.save(LocalCredential.create(user, passwordEncoder.encode(command.password()), now));
        try {
            users.flush();
            credentials.flush();
        } catch (DataIntegrityViolationException ex) {
            throw usernameTaken();
        }
        return new RegisteredAccount(user.getId(), username);
    }

    private static void validatePassword(String password, String confirmation) {
        int bytes = password == null ? 0 : password.getBytes(StandardCharsets.UTF_8).length;
        if (password == null || password.length() < PASSWORD_MIN || bytes > PASSWORD_MAX_BYTES) {
            throw new ValidationException("INVALID_PASSWORD",
                    "A senha deve ter pelo menos 8 caracteres e no máximo 72 bytes.");
        }
        if (!Objects.equals(password, confirmation)) {
            throw new ValidationException("PASSWORDS_DO_NOT_MATCH", "As senhas informadas não são iguais.");
        }
    }

    private static ConflictException usernameTaken() {
        return new ConflictException("USERNAME_TAKEN", "Este @usuário já está em uso. Escolha outro.");
    }

    public record RegisterCommand(String username, String password, String passwordConfirmation, String phone) {
    }

    public record RegisteredAccount(UUID userId, String username) {
    }

    public record UsernameAvailability(String username, boolean available, String reason) {
    }
}
