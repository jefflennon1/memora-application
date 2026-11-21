package br.com.memora.event.application;

import br.com.memora.event.domain.Event;
import br.com.memora.event.domain.EventPrivacy;
import br.com.memora.event.domain.EventModality;
import br.com.memora.event.domain.EventRepository;
import br.com.memora.event.domain.MuralStatus;
import br.com.memora.membership.application.ParticipationService;
import br.com.memora.membership.domain.ParticipantRole;
import br.com.memora.shared.error.BusinessRuleException;
import br.com.memora.shared.error.ForbiddenException;
import br.com.memora.shared.error.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Porta de entrada única para "esta pessoa pode ver / agir neste evento?", usada pelos módulos de eventos,
 * participantes e memórias.
 *
 * <p>Ordem das verificações: primeiro a visibilidade (evento privado sem vínculo = {@code 404 EVENT_NOT_FOUND},
 * igual a um evento inexistente), depois o papel ({@code 403}). Assim um {@code 403} nunca revela um evento
 * privado.
 */
@Service
public class EventAccess {

    private final EventRepository events;
    private final ParticipationService participations;

    public EventAccess(EventRepository events, ParticipationService participations) {
        this.events = events;
        this.participations = participations;
    }

    /** @param viewerId quem consulta; {@code null} para visitante anônimo */
    @Transactional(readOnly = true)
    public EventContext requireVisible(UUID eventId, UUID viewerId) {
        return findVisible(eventId, viewerId).orElseThrow(EventAccess::notFound);
    }

    /**
     * Como {@link #requireVisible}, mas sem lançar exceção. Use esta variante quando o "não encontrado" vai ser
     * traduzido para outro erro: capturar uma exceção vinda de um método transacional marcaria a transação inteira
     * para rollback.
     */
    @Transactional(readOnly = true)
    public Optional<EventContext> findVisible(UUID eventId, UUID viewerId) {
        return findVisibleEntity(eventId, viewerId).map(visible -> {
            Event e = visible.event();
            return new EventContext(e.getId(), e.getName(), e.getPrivacy(), e.getModality(), e.getMuralStatus(),
                    e.getOrganizerId(), visible.role());
        });
    }

    /** Variante interna que devolve a entidade, para quem vai alterá-la no mesmo pacote. */
    Visible loadVisible(UUID eventId, UUID viewerId) {
        return findVisibleEntity(eventId, viewerId).orElseThrow(EventAccess::notFound);
    }

    private Optional<Visible> findVisibleEntity(UUID eventId, UUID viewerId) {
        Optional<Event> event = events.findById(eventId);
        if (event.isEmpty()) {
            return Optional.empty();
        }
        Optional<ParticipantRole> role = participations.roleOf(eventId, viewerId);
        if (event.get().isPrivate() && role.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Visible(event.get(), role));
    }

    static NotFoundException notFound() {
        return new NotFoundException("EVENT_NOT_FOUND", "Evento não encontrado.");
    }

    record Visible(Event event, Optional<ParticipantRole> role) {
    }

    /** O evento como a pessoa o enxerga: dados mínimos e o papel dela (vazio se não participa). */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public record EventContext(UUID eventId, String name, EventPrivacy privacy, EventModality modality,
                               MuralStatus muralStatus,
                               UUID organizerId, Optional<ParticipantRole> role) {

        public boolean isParticipant() {
            return role.isPresent();
        }

        public boolean isPublic() {
            return privacy == EventPrivacy.PUBLIC;
        }

        public boolean isInPerson() {
            return modality == EventModality.IN_PERSON;
        }

        /** Qualquer papel. Quem não participa de um evento público recebe {@code 403 EVENT_PARTICIPANTS_ONLY}. */
        public ParticipantRole requireParticipant() {
            return role.orElseThrow(() -> new ForbiddenException("EVENT_PARTICIPANTS_ONLY",
                    "Só participantes do evento podem fazer isso."));
        }

        /** Organizador ou coorganizador. */
        public ParticipantRole requireOrganization() {
            return role.filter(ParticipantRole::canManageEvent).orElseThrow(EventContext::organizationOnly);
        }

        /** Somente o organizador principal. */
        public ParticipantRole requireOrganizer() {
            return role.filter(ParticipantRole::isOrganizer).orElseThrow(EventContext::organizerOnly);
        }

        /** Mural aberto, ou {@code 422 MURAL_CLOSED}. */
        public void requireMuralOpen() {
            if (muralStatus != MuralStatus.OPEN) {
                throw new BusinessRuleException("MURAL_CLOSED",
                        "O mural deste evento está fechado: ele é somente leitura.");
            }
        }

        public static ForbiddenException organizationOnly() {
            return new ForbiddenException("EVENT_ORGANIZATION_ONLY",
                    "Só o organizador ou um coorganizador do evento pode fazer isso.");
        }

        public static ForbiddenException organizerOnly() {
            return new ForbiddenException("EVENT_ORGANIZER_ONLY", "Só o organizador do evento pode fazer isso.");
        }
    }
}
