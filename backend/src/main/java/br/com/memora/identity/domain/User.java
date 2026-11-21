package br.com.memora.identity.domain;

import br.com.memora.shared.error.BusinessRuleException;
import br.com.memora.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Conta interna da Memora.
 *
 * <p>Criada provisoriamente no primeiro login ({@link UserStatus#PENDING_ONBOARDING}), com nome e foto
 * apenas <em>sugeridos</em> pelo provedor. Só se torna {@link UserStatus#ACTIVE} quando o próprio usuário
 * confirma o nome, a foto e escolhe o @usuário.
 */
@Entity
@Table(name = "app_user")
public class User extends BaseEntity {

    public static final int DISPLAY_NAME_MAX = 80;
    public static final int PHOTO_URL_MAX = 2048;

    @Column(name = "username", length = 30)
    private String username;

    @Column(name = "display_name", length = DISPLAY_NAME_MAX)
    private String displayName;

    @Column(name = "photo_url", length = PHOTO_URL_MAX)
    private String photoUrl;

    @Column(name = "photo_storage_key", length = 200)
    private String photoStorageKey;

    @Column(name = "photo_content_type", length = 50)
    private String photoContentType;

    @Column(name = "photo_size_bytes")
    private Long photoSizeBytes;

    @Column(name = "photo_width")
    private Integer photoWidth;

    @Column(name = "photo_height")
    private Integer photoHeight;

    @Column(name = "photo_updated_at")
    private Instant photoUpdatedAt;

    @Column(name = "phone", length = 15)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_privacy", length = 20, nullable = false)
    private ProfilePrivacy profilePrivacy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private UserStatus status;

    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected User() {
        // JPA
    }

    /** Nova conta provisória, criada no primeiro login por um provedor de identidade. */
    public static User newPendingAccount(String suggestedName, String suggestedPhotoUrl, Instant now) {
        User user = new User();
        user.status = UserStatus.PENDING_ONBOARDING;
        user.profilePrivacy = ProfilePrivacy.PUBLIC;
        user.displayName = truncate(suggestedName, DISPLAY_NAME_MAX);
        user.photoUrl = fitsOrNull(suggestedPhotoUrl, PHOTO_URL_MAX);
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    /** Conta criada com @usuário, senha e telefone; já nasce com o cadastro mínimo concluído. */
    public static User newLocalAccount(String normalizedUsername, String phone, Instant now) {
        User user = new User();
        user.username = Objects.requireNonNull(normalizedUsername);
        user.displayName = normalizedUsername;
        user.phone = Objects.requireNonNull(phone);
        user.profilePrivacy = ProfilePrivacy.PUBLIC;
        user.status = UserStatus.ACTIVE;
        user.onboardingCompletedAt = Objects.requireNonNull(now);
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    /** Enquanto o onboarding não é concluído, as sugestões do provedor podem ser atualizadas. */
    public void refreshProviderSuggestions(String suggestedName, String suggestedPhotoUrl) {
        if (status != UserStatus.PENDING_ONBOARDING) {
            return;
        }
        if (suggestedName != null && !suggestedName.isBlank()) {
            this.displayName = truncate(suggestedName, DISPLAY_NAME_MAX);
        }
        if (suggestedPhotoUrl != null) {
            this.photoUrl = fitsOrNull(suggestedPhotoUrl, PHOTO_URL_MAX);
        }
    }

    /**
     * Conclui o onboarding com os dados confirmados pelo usuário.
     *
     * @param normalizedUsername @usuário já validado e normalizado
     * @param keepSuggestedPhoto {@code true} mantém a foto sugerida pelo provedor; {@code false} remove
     */
    public void completeOnboarding(String normalizedUsername, String displayName, boolean keepSuggestedPhoto,
                                   ProfilePrivacy privacy, Instant now) {
        if (status != UserStatus.PENDING_ONBOARDING) {
            throw new BusinessRuleException("ONBOARDING_ALREADY_COMPLETED", "O cadastro inicial já foi concluído.");
        }
        this.username = Objects.requireNonNull(normalizedUsername);
        this.displayName = Objects.requireNonNull(displayName);
        if (!keepSuggestedPhoto) {
            this.photoUrl = null;
        }
        if (privacy != null) {
            this.profilePrivacy = privacy;
        }
        this.status = UserStatus.ACTIVE;
        this.onboardingCompletedAt = now;
    }

    public void changeUsername(String normalizedUsername) {
        this.username = Objects.requireNonNull(normalizedUsername);
    }

    public void changeDisplayName(String displayName) {
        this.displayName = Objects.requireNonNull(displayName);
    }

    public void removePhoto() {
        this.photoUrl = null;
        this.photoStorageKey = null;
        this.photoContentType = null;
        this.photoSizeBytes = null;
        this.photoWidth = null;
        this.photoHeight = null;
        this.photoUpdatedAt = null;
    }

    public void replacePhoto(String storageKey, String contentType, long sizeBytes, int width, int height,
                             Instant now) {
        this.photoUrl = null;
        this.photoStorageKey = Objects.requireNonNull(storageKey);
        this.photoContentType = Objects.requireNonNull(contentType);
        this.photoSizeBytes = sizeBytes;
        this.photoWidth = width;
        this.photoHeight = height;
        this.photoUpdatedAt = Objects.requireNonNull(now);
    }

    public void changeProfilePrivacy(ProfilePrivacy privacy) {
        this.profilePrivacy = Objects.requireNonNull(privacy);
    }

    @PreUpdate
    void touch() {
        // Precisão do banco (microssegundos).
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isDisabled() {
        return status.isDisabled();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String fitsOrNull(String value, int max) {
        return value == null || value.isBlank() || value.length() > max ? null : value;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPhotoUrl() {
        return photoStorageKey == null ? photoUrl
                : "/api/v1/users/" + getId() + "/photo?v=" + photoUpdatedAt.toEpochMilli();
    }

    public String getPhotoStorageKey() {
        return photoStorageKey;
    }

    public String getPhotoContentType() {
        return photoContentType;
    }

    public String getPhone() {
        return phone;
    }

    public ProfilePrivacy getProfilePrivacy() {
        return profilePrivacy;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getOnboardingCompletedAt() {
        return onboardingCompletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
