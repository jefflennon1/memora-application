package br.com.memora.memory.application;

import br.com.memora.event.application.EventAccess;
import br.com.memora.event.application.EventAccess.EventContext;
import br.com.memora.memory.domain.Memory;
import br.com.memora.memory.domain.MemoryRepository;
import br.com.memora.memory.domain.MemoryVisibility;
import br.com.memora.shared.error.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quem vê uma memória. A privacidade do evento vale acima da memória:
 *
 * <ul>
 *   <li>Participante do evento: vê todas as memórias.</li>
 *   <li>Quem não participa: só memórias {@code PUBLIC} de evento público.</li>
 * </ul>
 *
 * Uma memória que a pessoa não pode ver responde como inexistente ({@code 404 MEMORY_NOT_FOUND}), inclusive
 * quando o motivo é o evento privado: nada revela que ela existe.
 */
@Service
public class MemoryAccess {

    private final MemoryRepository memories;
    private final EventAccess events;

    public MemoryAccess(MemoryRepository memories, EventAccess events) {
        this.memories = memories;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Visible requireVisible(UUID memoryId, UUID viewerId) {
        return findVisible(memoryId, viewerId).orElseThrow(MemoryAccess::notFound);
    }

    /** Sem lançar exceção (ver {@link EventAccess#findVisible}). */
    @Transactional(readOnly = true)
    public Optional<Visible> findVisible(UUID memoryId, UUID viewerId) {
        Optional<Memory> memory = memories.findById(memoryId);
        if (memory.isEmpty()) {
            return Optional.empty();
        }
        return events.findVisible(memory.get().getEventId(), viewerId)
                .filter(context -> canSee(memory.get().getVisibility(), context))
                .map(context -> new Visible(memory.get(), context));
    }

    public static boolean canSee(MemoryVisibility visibility, EventContext context) {
        return context.isParticipant() || (context.isPublic() && visibility == MemoryVisibility.PUBLIC);
    }

    static NotFoundException notFound() {
        return new NotFoundException("MEMORY_NOT_FOUND", "Memória não encontrada.");
    }

    public record Visible(Memory memory, EventContext event) {
    }
}
