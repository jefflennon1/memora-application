package br.com.memora.membership.application;

import br.com.memora.event.application.EventAccess;
import br.com.memora.event.application.EventAccess.EventContext;
import br.com.memora.membership.application.PeopleDirectory.PersonView;
import br.com.memora.membership.domain.EventParticipant;
import br.com.memora.membership.domain.EventParticipantRepository;
import br.com.memora.membership.domain.AttendanceStatus;
import br.com.memora.membership.domain.ParticipantRole;
import br.com.memora.shared.error.BusinessRuleException;
import br.com.memora.shared.error.NotFoundException;
import br.com.memora.shared.web.PageResponse;
import java.time.Instant;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lista de participantes e gestão de papéis.
 *
 * <ul>
 *   <li>A lista só é visível a participantes do evento.</li>
 *   <li>Promover/rebaixar coorganizadores: só o organizador.</li>
 *   <li>Remover: o organizador remove qualquer pessoa (menos ele mesmo); o coorganizador remove só participantes
 *       comuns. Qualquer pessoa pode sair por conta própria, exceto o organizador, que é permanente.</li>
 *   <li>Remover alguém <b>não apaga</b> as memórias que essa pessoa já publicou.</li>
 * </ul>
 */
@Service
public class ParticipantService {

    private final EventParticipantRepository participants;
    private final EventAccess access;
    private final PeopleDirectory people;
    private final Clock clock;

    public ParticipantService(EventParticipantRepository participants, EventAccess access, PeopleDirectory people,
                              Clock clock) {
        this.participants = participants;
        this.access = access;
        this.people = people;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<ParticipantView> list(UUID eventId, UUID viewerId, int page, int size) {
        EventContext context = access.requireVisible(eventId, viewerId);
        context.requireParticipant();
        Page<EventParticipant> result = participants.findByEvent(eventId, PageResponse.request(page, size));
        Map<UUID, PersonView> persons = people.summaries(viewerId,
                result.getContent().stream().map(EventParticipant::getUserId).toList());
        return result.map(p -> toView(p, persons.get(p.getUserId()), context.isInPerson()));
    }

    /** Promove a coorganizador ou rebaixa a participante. Somente o organizador. */
    @Transactional
    public ParticipantView changeRole(UUID eventId, UUID actorId, UUID targetUserId, ParticipantRole role) {
        EventContext context = access.requireVisible(eventId, actorId);
        context.requireOrganizer();
        EventParticipant target = load(eventId, targetUserId);
        target.changeRole(role);
        return toView(target, people.summary(actorId, targetUserId), context.isInPerson());
    }

    /** Cada pessoa responde apenas pela própria presença física. */
    @Transactional
    public ParticipantView updateAttendance(UUID eventId, UUID userId, AttendanceStatus status) {
        EventContext context = access.requireVisible(eventId, userId);
        context.requireParticipant();
        requireInPerson(context);
        EventParticipant participant = load(eventId, userId);
        participant.updateAttendance(status, clock.instant());
        return toView(participant, people.summary(userId, userId), true);
    }

    @Transactional(readOnly = true)
    public AttendanceSummary attendanceSummary(UUID eventId, UUID viewerId) {
        EventContext context = access.requireVisible(eventId, viewerId);
        context.requireParticipant();
        requireInPerson(context);
        long attending = participants.countByEventIdAndAttendanceStatus(eventId, AttendanceStatus.ATTENDING);
        long notAttending = participants.countByEventIdAndAttendanceStatus(eventId, AttendanceStatus.NOT_ATTENDING);
        long unanswered = participants.countByEventIdAndAttendanceStatusIsNull(eventId);
        EventParticipant viewer = load(eventId, viewerId);
        return new AttendanceSummary(attending, notAttending, unanswered, attending + notAttending + unanswered,
                viewer.getAttendanceStatus(), viewer.getAttendanceUpdatedAt());
    }

    /** Remove alguém do evento, ou sai dele quando {@code targetUserId} é a própria pessoa. */
    @Transactional
    public void remove(UUID eventId, UUID actorId, UUID targetUserId) {
        EventContext context = access.requireVisible(eventId, actorId);
        ParticipantRole actorRole = context.requireParticipant();
        EventParticipant target = load(eventId, targetUserId);

        if (target.getRole() == ParticipantRole.ORGANIZER) {
            throw new BusinessRuleException("ORGANIZER_IS_PERMANENT",
                    "O organizador principal é permanente: não pode sair nem ser removido do evento.");
        }
        boolean leaving = targetUserId.equals(actorId);
        if (!leaving) {
            if (!actorRole.canManageEvent()) {
                throw EventContext.organizationOnly();
            }
            if (target.getRole() == ParticipantRole.CO_ORGANIZER && !actorRole.isOrganizer()) {
                throw EventContext.organizerOnly();
            }
        }
        participants.delete(target);
    }

    private EventParticipant load(UUID eventId, UUID userId) {
        return participants.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("PARTICIPANT_NOT_FOUND",
                        "Esta pessoa não participa do evento."));
    }

    private static void requireInPerson(EventContext context) {
        if (!context.isInPerson()) {
            throw new BusinessRuleException("ATTENDANCE_NOT_APPLICABLE",
                    "Confirmação de presença física só se aplica a eventos presenciais.");
        }
    }

    private static ParticipantView toView(EventParticipant participant, PersonView person, boolean inPerson) {
        return new ParticipantView(person, participant.getRole(), participant.getJoinedAt(),
                inPerson ? participant.getAttendanceStatus() : null,
                inPerson ? participant.getAttendanceUpdatedAt() : null);
    }

    public record ParticipantView(PersonView person, ParticipantRole role, Instant joinedAt,
                                  AttendanceStatus attendanceStatus, Instant attendanceUpdatedAt) {
    }

    public record AttendanceSummary(long attending, long notAttending, long unanswered, long total,
                                    AttendanceStatus myStatus, Instant myUpdatedAt) {
    }
}
