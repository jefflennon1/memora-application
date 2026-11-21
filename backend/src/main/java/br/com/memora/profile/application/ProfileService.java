package br.com.memora.profile.application;

import br.com.memora.identity.domain.ExternalIdentityRepository;
import br.com.memora.identity.domain.IdentityProvider;
import br.com.memora.identity.domain.LocalCredentialRepository;
import br.com.memora.identity.domain.ProfilePrivacy;
import br.com.memora.identity.domain.User;
import br.com.memora.identity.domain.UserRepository;
import br.com.memora.identity.domain.UserStatus;
import br.com.memora.membership.application.PeopleDirectory;
import br.com.memora.memory.application.MediaProperties;
import br.com.memora.memory.domain.ImageInspector;
import br.com.memora.profile.domain.Username;
import br.com.memora.shared.error.ConflictException;
import br.com.memora.shared.error.NotFoundException;
import br.com.memora.shared.error.PayloadTooLargeException;
import br.com.memora.shared.error.ValidationException;
import br.com.memora.shared.storage.AfterCommitStorageCleanup;
import br.com.memora.shared.storage.ObjectStorage;
import br.com.memora.shared.storage.StoredImage;
import br.com.memora.shared.storage.StoredObjectNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro inicial (onboarding) e manutenção do próprio perfil.
 */
@Service
public class ProfileService {

    private final UserRepository users;
    private final ExternalIdentityRepository identities;
    private final LocalCredentialRepository localCredentials;
    private final PeopleDirectory people;
    private final ObjectStorage storage;
    private final AfterCommitStorageCleanup cleanup;
    private final MediaProperties mediaProperties;
    private final Clock clock;

    public ProfileService(UserRepository users, ExternalIdentityRepository identities,
                          LocalCredentialRepository localCredentials, PeopleDirectory people,
                          ObjectStorage storage, AfterCommitStorageCleanup cleanup,
                          MediaProperties mediaProperties, Clock clock) {
        this.users = users;
        this.identities = identities;
        this.localCredentials = localCredentials;
        this.people = people;
        this.storage = storage;
        this.cleanup = cleanup;
        this.mediaProperties = mediaProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MyProfile me(UUID userId) {
        return toView(load(userId));
    }

    @Transactional
    public MyProfile completeOnboarding(UUID userId, OnboardingCommand command) {
        User user = load(userId);
        String username = Username.parse(command.username());
        String displayName = requireDisplayName(command.displayName());
        ensureUsernameFree(username, userId);
        user.completeOnboarding(username, displayName, command.keepSuggestedPhoto(), command.profilePrivacy(),
                clock.instant());
        flushTranslatingUsernameConflict();
        return toView(user);
    }

    @Transactional
    public MyProfile update(UUID userId, ProfileUpdateCommand command) {
        User user = load(userId);
        if (command.username() != null) {
            String username = Username.parse(command.username());
            if (!username.equals(user.getUsername())) {
                ensureUsernameFree(username, userId);
                user.changeUsername(username);
            }
        }
        if (command.displayName() != null) {
            user.changeDisplayName(requireDisplayName(command.displayName()));
        }
        if (command.removePhoto()) {
            String oldKey = user.getPhotoStorageKey();
            user.removePhoto();
            if (oldKey != null) cleanup.deleteAfterCommit(List.of(oldKey));
        }
        if (command.profilePrivacy() != null) {
            user.changeProfilePrivacy(command.profilePrivacy());
        }
        flushTranslatingUsernameConflict();
        return toView(user);
    }

    /** Insere ou substitui a foto própria. O nome e o Content-Type declarados pelo cliente não são confiados. */
    @Transactional
    public MyProfile uploadPhoto(UUID userId, byte[] content) {
        ImageInspector.ImageInfo info = inspectImage(content);
        User user = load(userId);
        String oldKey = user.getPhotoStorageKey();
        String key = "users/" + userId + "/profile/" + UUID.randomUUID();
        user.replacePhoto(key, info.contentType(), content.length, info.width(), info.height(), clock.instant());
        users.flush();
        storage.put(key, content, info.contentType());
        if (oldKey != null) cleanup.deleteAfterCommit(List.of(oldKey));
        return toView(user);
    }

    @Transactional
    public MyProfile removePhoto(UUID userId) {
        User user = load(userId);
        String oldKey = user.getPhotoStorageKey();
        user.removePhoto();
        users.flush();
        if (oldKey != null) cleanup.deleteAfterCommit(List.of(oldKey));
        return toView(user);
    }

    /** A foto segue exatamente a mesma regra de privacidade usada nos resumos de pessoas. */
    @Transactional(readOnly = true)
    public StoredImage openPhoto(UUID userId, UUID viewerId) {
        if (people.summary(viewerId, userId).profileHidden()) throw photoNotFound();
        User user = load(userId);
        if (user.getPhotoStorageKey() == null) throw photoNotFound();
        try {
            return new StoredImage(user.getPhotoStorageKey(), user.getPhotoContentType(),
                    storage.get(user.getPhotoStorageKey()));
        } catch (StoredObjectNotFoundException ex) {
            throw photoNotFound();
        }
    }

    /** Disponibilidade de um @usuário para o usuário atual (o próprio @usuário conta como disponível). */
    @Transactional(readOnly = true)
    public UsernameAvailability availability(UUID userId, String raw) {
        String normalized = Username.normalize(raw);
        Optional<String> problem = Username.problemWith(normalized);
        if (problem.isPresent()) {
            return new UsernameAvailability(normalized, false, problem.get());
        }
        boolean taken = users.existsByUsernameAndIdNot(normalized, userId);
        return new UsernameAvailability(normalized, !taken, taken ? "USERNAME_TAKEN" : null);
    }

    private User load(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Conta não encontrada."));
    }

    private void ensureUsernameFree(String username, UUID userId) {
        if (users.existsByUsernameAndIdNot(username, userId)) {
            throw usernameTaken();
        }
    }

    /** Fecha a janela de corrida entre a verificação e a gravação: a constraint única decide. */
    private void flushTranslatingUsernameConflict() {
        try {
            users.flush();
        } catch (DataIntegrityViolationException e) {
            throw usernameTaken();
        }
    }

    private static ConflictException usernameTaken() {
        return new ConflictException("USERNAME_TAKEN", "Este @usuário já está em uso. Escolha outro.");
    }

    private ImageInspector.ImageInfo inspectImage(byte[] content) {
        if (content == null || content.length == 0) {
            throw new ValidationException("EMPTY_FILE", "O arquivo enviado está vazio.");
        }
        if (content.length > mediaProperties.maxImageBytes()) {
            throw new PayloadTooLargeException("MEDIA_TOO_LARGE", "A imagem pode ter no máximo "
                    + mediaProperties.maxImageBytes() / (1024 * 1024) + " MB.");
        }
        return ImageInspector.inspect(content);
    }

    private static NotFoundException photoNotFound() {
        return new NotFoundException("PROFILE_PHOTO_NOT_FOUND", "Foto de perfil não encontrada.");
    }

    private static String requireDisplayName(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty() || value.length() > User.DISPLAY_NAME_MAX) {
            throw new ValidationException("INVALID_DISPLAY_NAME",
                    "Informe um nome com 1 a " + User.DISPLAY_NAME_MAX + " caracteres.");
        }
        return value;
    }

    private MyProfile toView(User user) {
        List<LinkedIdentity> linked = identities.findByUserIdOrderByLinkedAtAsc(user.getId()).stream()
                .map(i -> new LinkedIdentity(i.getProvider(), i.getEmail(), i.getEmailVerified(), i.getLinkedAt()))
                .toList();
        return new MyProfile(user.getId(), user.getUsername(), user.getDisplayName(), user.getPhotoUrl(), user.getPhone(),
                user.getProfilePrivacy(), user.getStatus(), user.getStatus() != UserStatus.PENDING_ONBOARDING,
                user.getCreatedAt(), user.getOnboardingCompletedAt(), localCredentials.existsByUserId(user.getId()), linked);
    }

    public record OnboardingCommand(String username, String displayName, boolean keepSuggestedPhoto,
                                    ProfilePrivacy profilePrivacy) {
    }

    public record ProfileUpdateCommand(String username, String displayName, boolean removePhoto,
                                       ProfilePrivacy profilePrivacy) {
    }

    public record MyProfile(UUID id, String username, String displayName, String photoUrl, String phone,
                            ProfilePrivacy profilePrivacy, UserStatus status, boolean onboardingCompleted,
                            Instant createdAt, Instant onboardingCompletedAt, boolean passwordLogin,
                            List<LinkedIdentity> identities) {
    }

    public record LinkedIdentity(IdentityProvider provider, String email, Boolean emailVerified, Instant linkedAt) {
    }

    public record UsernameAvailability(String username, boolean available, String reason) {
    }
}
