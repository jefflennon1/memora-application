package br.com.memora.membership.domain;

import br.com.memora.shared.error.BusinessRuleException;
import br.com.memora.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Vínculo de uma pessoa com um evento. */
@Entity
@Table(name = "event_participant")
public class EventParticipant extends BaseEntity {

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private ParticipantRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", length = 20)
    private AttendanceStatus attendanceStatus;

    @Column(name = "attendance_updated_at")
    private Instant attendanceUpdatedAt;

    protected EventParticipant() {
        // JPA
    }

    public static EventParticipant organizer(UUID eventId, UUID userId, AttendanceStatus attendance, Instant now) {
        return of(eventId, userId, ParticipantRole.ORGANIZER, attendance, now);
    }

    public static EventParticipant participant(UUID eventId, UUID userId, AttendanceStatus attendance, Instant now) {
        return of(eventId, userId, ParticipantRole.PARTICIPANT, attendance, now);
    }

    private static EventParticipant of(UUID eventId, UUID userId, ParticipantRole role, AttendanceStatus attendance,
                                       Instant now) {
        EventParticipant participant = new EventParticipant();
        participant.eventId = Objects.requireNonNull(eventId);
        participant.userId = Objects.requireNonNull(userId);
        participant.role = role;
        participant.joinedAt = Objects.requireNonNull(now);
        participant.updateAttendance(attendance, now);
        return participant;
    }

    /** Promove a coorganizador ou rebaixa a participante. O organizador principal é permanente. */
    public void changeRole(ParticipantRole newRole) {
        if (role == ParticipantRole.ORGANIZER || newRole == ParticipantRole.ORGANIZER) {
            throw new BusinessRuleException("ORGANIZER_IS_PERMANENT",
                    "O organizador principal é permanente: o título não é transferido.");
        }
        this.role = Objects.requireNonNull(newRole);
    }

    public void updateAttendance(AttendanceStatus status, Instant now) {
        this.attendanceStatus = status;
        this.attendanceUpdatedAt = status == null ? null : Objects.requireNonNull(now);
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getUserId() {
        return userId;
    }

    public ParticipantRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public AttendanceStatus getAttendanceStatus() {
        return attendanceStatus;
    }

    public Instant getAttendanceUpdatedAt() {
        return attendanceUpdatedAt;
    }
}
