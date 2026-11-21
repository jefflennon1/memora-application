package br.com.memora.membership.application;

import br.com.memora.membership.domain.EventParticipant;
import br.com.memora.membership.domain.EventParticipantRepository;
import br.com.memora.membership.domain.AttendanceStatus;
import br.com.memora.membership.domain.ParticipantRole;
import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta e registro de participação em eventos. É a fonte única de "quem pode o quê" num evento:
 * os demais módulos perguntam o papel aqui em vez de ler a tabela diretamente.
 */
@Service
public class ParticipationService {

    private final EventParticipantRepository participants;
    private final Clock clock;

    public ParticipationService(EventParticipantRepository participants, Clock clock) {
        this.participants = participants;
        this.clock = clock;
    }

    /** Registra o criador do evento como organizador principal (e participante). */
    @Transactional
    public void registerOrganizer(UUID eventId, UUID userId, AttendanceStatus attendance) {
        participants.save(EventParticipant.organizer(eventId, userId, attendance, clock.instant()));
    }

    /**
     * Inclui a pessoa como participante comum.
     *
     * @return {@code false} se ela já participava (com qualquer papel)
     */
    @Transactional
    public boolean addParticipant(UUID eventId, UUID userId) {
        return addParticipant(eventId, userId, null);
    }

    @Transactional
    public boolean addParticipant(UUID eventId, UUID userId, AttendanceStatus attendance) {
        if (participants.existsByEventIdAndUserId(eventId, userId)) {
            return false;
        }
        participants.save(EventParticipant.participant(eventId, userId, attendance, clock.instant()));
        return true;
    }

    @Transactional
    public void clearAttendance(UUID eventId) {
        participants.clearAttendance(eventId);
    }

    /** Papel da pessoa no evento; vazio se ela não participa (ou se é anônima). */
    @Transactional(readOnly = true)
    public Optional<ParticipantRole> roleOf(UUID eventId, UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return participants.findByEventIdAndUserId(eventId, userId).map(EventParticipant::getRole);
    }

    /** Papéis da pessoa em vários eventos de uma vez (eventos sem vínculo ficam fora do mapa). */
    @Transactional(readOnly = true)
    public Map<UUID, ParticipantRole> rolesOf(UUID userId, Collection<UUID> eventIds) {
        if (userId == null || eventIds.isEmpty()) {
            return Map.of();
        }
        return participants.findByUserIdAndEventIdIn(userId, eventIds).stream()
                .collect(Collectors.toMap(EventParticipant::getEventId, EventParticipant::getRole));
    }
}
