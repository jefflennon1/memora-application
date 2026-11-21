package br.com.memora.membership.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventParticipantRepository extends JpaRepository<EventParticipant, UUID> {

    Optional<EventParticipant> findByEventIdAndUserId(UUID eventId, UUID userId);

    List<EventParticipant> findByUserIdAndEventIdIn(UUID userId, Collection<UUID> eventIds);

    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);

    /** Participantes do evento: organizador, coorganizadores e depois os demais, por ordem de entrada. */
    @Query(value = """
            select p from EventParticipant p where p.eventId = :eventId
            order by case p.role when br.com.memora.membership.domain.ParticipantRole.ORGANIZER then 0
                              when br.com.memora.membership.domain.ParticipantRole.CO_ORGANIZER then 1 else 2 end,
                     p.joinedAt, p.id""",
            countQuery = "select count(p) from EventParticipant p where p.eventId = :eventId")
    Page<EventParticipant> findByEvent(@Param("eventId") UUID eventId, Pageable pageable);

    long countByEventId(UUID eventId);

    long countByEventIdAndAttendanceStatus(UUID eventId, AttendanceStatus status);

    long countByEventIdAndAttendanceStatusIsNull(UUID eventId);

    @Modifying
    @Query("update EventParticipant p set p.attendanceStatus = null, p.attendanceUpdatedAt = null "
            + "where p.eventId = :eventId")
    void clearAttendance(@Param("eventId") UUID eventId);

    /** Entre {@code userIds}, quem participa de pelo menos um evento em comum com {@code viewerId}. */
    @Query("""
            select distinct other.userId from EventParticipant mine, EventParticipant other
            where mine.eventId = other.eventId and mine.userId = :viewerId and other.userId in :userIds""")
    Set<UUID> findUsersSharingAnEventWith(@Param("viewerId") UUID viewerId,
                                          @Param("userIds") Collection<UUID> userIds);

    /** Entre {@code userIds}, quem participa do evento. */
    @Query("select p.userId from EventParticipant p where p.eventId = :eventId and p.userId in :userIds")
    Set<UUID> findParticipantsAmong(@Param("eventId") UUID eventId, @Param("userIds") Collection<UUID> userIds);
}
