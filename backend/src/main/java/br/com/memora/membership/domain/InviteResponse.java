package br.com.memora.membership.domain;

import br.com.memora.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Resposta explícita de quem recusou um convite sem entrar no evento. */
@Entity
@Table(name = "invite_response")
public class InviteResponse extends BaseEntity {

    @Column(name = "invite_id", nullable = false, updatable = false)
    private UUID inviteId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private InviteResponseStatus status;

    @Column(name = "responded_at", nullable = false)
    private Instant respondedAt;

    protected InviteResponse() {
        // JPA
    }

    public static InviteResponse declined(UUID inviteId, UUID userId, Instant now) {
        InviteResponse response = new InviteResponse();
        response.inviteId = Objects.requireNonNull(inviteId);
        response.userId = Objects.requireNonNull(userId);
        response.status = InviteResponseStatus.DECLINED;
        response.respondedAt = Objects.requireNonNull(now);
        return response;
    }
}
