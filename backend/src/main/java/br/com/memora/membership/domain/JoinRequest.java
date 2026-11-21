package br.com.memora.membership.domain;

import br.com.memora.shared.error.BusinessRuleException;
import br.com.memora.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Pedido de entrada num evento, feito por quem usou um convite genérico. */
@Entity
@Table(name = "join_request")
public class JoinRequest extends BaseEntity {

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "invite_id", nullable = false, updatable = false)
    private UUID inviteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private JoinRequestStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", length = 20)
    private AttendanceStatus attendanceStatus;

    @Column(name = "attendance_updated_at")
    private Instant attendanceUpdatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected JoinRequest() {
        // JPA
    }

    public static JoinRequest pending(UUID eventId, UUID userId, UUID inviteId, AttendanceStatus attendance,
                                      Instant now) {
        JoinRequest request = new JoinRequest();
        request.eventId = Objects.requireNonNull(eventId);
        request.userId = Objects.requireNonNull(userId);
        request.inviteId = Objects.requireNonNull(inviteId);
        request.status = JoinRequestStatus.PENDING;
        request.requestedAt = now;
        request.updateAttendance(attendance, now);
        return request;
    }

    public void updateAttendance(AttendanceStatus attendance, Instant now) {
        this.attendanceStatus = attendance;
        this.attendanceUpdatedAt = attendance == null ? null : Objects.requireNonNull(now);
    }

    public void approve(UUID decidedBy, Instant now) {
        decide(JoinRequestStatus.APPROVED, decidedBy, now);
    }

    public void reject(UUID decidedBy, Instant now) {
        decide(JoinRequestStatus.REJECTED, decidedBy, now);
    }

    private void decide(JoinRequestStatus decision, UUID by, Instant now) {
        if (status != JoinRequestStatus.PENDING) {
            throw new BusinessRuleException("JOIN_REQUEST_ALREADY_DECIDED", "Esta solicitação já foi respondida.");
        }
        this.status = decision;
        this.decidedBy = Objects.requireNonNull(by);
        this.decidedAt = now;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getInviteId() {
        return inviteId;
    }

    public JoinRequestStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public AttendanceStatus getAttendanceStatus() {
        return attendanceStatus;
    }

    public Instant getAttendanceUpdatedAt() {
        return attendanceUpdatedAt;
    }
}
