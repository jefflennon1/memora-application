package br.com.memora.membership.domain;

import br.com.memora.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/** Convite para um evento. O token é a chave do link e nunca é derivado de dados do evento ou da pessoa. */
@Entity
@Table(name = "event_invite")
public class EventInvite extends BaseEntity {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "token", length = 64, nullable = false, updatable = false)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false, updatable = false)
    private InviteType type;

    @Column(name = "invitee_user_id", updatable = false)
    private UUID inviteeUserId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "page_title", length = 120)
    private String pageTitle;

    @Column(name = "page_body", length = 3000)
    private String pageBody;

    @Column(name = "page_footer", length = 500)
    private String pageFooter;

    @Column(name = "page_location", length = 300)
    private String pageLocation;

    @Column(name = "background_color", length = 7)
    private String backgroundColor;

    @Column(name = "background_gradient_color", length = 7)
    private String backgroundGradientColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "background_image_layout", length = 10)
    private InviteBackgroundLayout backgroundImageLayout;

    @Column(name = "background_storage_key", length = 200)
    private String backgroundStorageKey;

    @Column(name = "background_content_type", length = 50)
    private String backgroundContentType;

    @Column(name = "background_updated_at")
    private Instant backgroundUpdatedAt;

    protected EventInvite() {
        // JPA
    }

    /**
     * @param inviteeUserId obrigatório para {@link InviteType#NAMED} e proibido para os demais
     */
    public static EventInvite create(UUID eventId, InviteType type, UUID inviteeUserId, UUID createdBy,
                                     Duration validity, Instant now) {
        if ((type == InviteType.NAMED) != (inviteeUserId != null)) {
            throw new IllegalArgumentException("Destinatário só existe (e é obrigatório) em convite nominal");
        }
        EventInvite invite = new EventInvite();
        invite.eventId = Objects.requireNonNull(eventId);
        invite.type = type;
        invite.inviteeUserId = inviteeUserId;
        invite.createdBy = Objects.requireNonNull(createdBy);
        invite.createdAt = now;
        invite.expiresAt = now.plus(validity);
        invite.token = newToken();
        return invite;
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public InviteState stateAt(Instant now) {
        if (revokedAt != null) {
            return InviteState.REVOKED;
        }
        if (usedAt != null) {
            return InviteState.USED;
        }
        if (!now.isBefore(expiresAt)) {
            return InviteState.EXPIRED;
        }
        return InviteState.ACTIVE;
    }

    /** Convite nominal destinado a outra pessoa. */
    public boolean isNamedFor(UUID userId) {
        return type == InviteType.NAMED && inviteeUserId.equals(userId);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public void markUsed(Instant now) {
        if (type == InviteType.NAMED && usedAt == null) {
            usedAt = now;
        }
    }

    public void customizePage(String title, String body, String footer, String location, String color,
                              String gradientColor, InviteBackgroundLayout imageLayout) {
        this.pageTitle = title;
        this.pageBody = body;
        this.pageFooter = footer;
        this.pageLocation = location;
        this.backgroundColor = color;
        this.backgroundGradientColor = gradientColor;
        this.backgroundImageLayout = imageLayout;
    }

    public void replaceBackground(String storageKey, String contentType, Instant now) {
        this.backgroundStorageKey = Objects.requireNonNull(storageKey);
        this.backgroundContentType = Objects.requireNonNull(contentType);
        this.backgroundUpdatedAt = Objects.requireNonNull(now);
    }

    public void removeBackground() {
        this.backgroundStorageKey = null;
        this.backgroundContentType = null;
        this.backgroundUpdatedAt = null;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getToken() {
        return token;
    }

    public InviteType getType() {
        return type;
    }

    public UUID getInviteeUserId() {
        return inviteeUserId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public String getPageTitle() { return pageTitle; }
    public String getPageBody() { return pageBody; }
    public String getPageFooter() { return pageFooter; }
    public String getPageLocation() { return pageLocation; }
    public String getBackgroundColor() { return backgroundColor; }
    public String getBackgroundGradientColor() { return backgroundGradientColor; }
    public InviteBackgroundLayout getBackgroundImageLayout() { return backgroundImageLayout; }
    public String getBackgroundStorageKey() { return backgroundStorageKey; }
    public String getBackgroundContentType() { return backgroundContentType; }
    public Instant getBackgroundUpdatedAt() { return backgroundUpdatedAt; }
}
