package br.com.memora.interaction.application;

import br.com.memora.interaction.domain.Emoji;
import br.com.memora.interaction.domain.ReactionRepository;
import br.com.memora.memory.application.MemoryAccess;
import br.com.memora.memory.application.MemoryEngagement.ReactionSummary;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reações: um emoji por pessoa em cada memória.
 *
 * <ul>
 *   <li>Reagir: participante, com o mural aberto. Reagir de novo troca o emoji.</li>
 *   <li>Remover a própria reação: também com o mural fechado.</li>
 *   <li>Resumo: quem vê a memória vê quantas reações há de cada emoji e qual é a sua. Nunca quem reagiu.</li>
 * </ul>
 */
@Service
public class ReactionService {

    private final ReactionRepository reactions;
    private final MemoryAccess memories;
    private final EngagementService engagement;
    private final Clock clock;

    public ReactionService(ReactionRepository reactions, MemoryAccess memories, EngagementService engagement,
                           Clock clock) {
        this.reactions = reactions;
        this.memories = memories;
        this.engagement = engagement;
        this.clock = clock;
    }

    @Transactional
    public ReactionSummary react(UUID memoryId, UUID userId, String emoji) {
        MemoryAccess.Visible visible = memories.requireVisible(memoryId, userId);
        visible.event().requireParticipant();
        visible.event().requireMuralOpen();
        reactions.upsert(UUID.randomUUID(), memoryId, userId, Emoji.normalize(emoji), clock.instant());
        return engagement.reactions(memoryId, userId);
    }

    @Transactional
    public ReactionSummary remove(UUID memoryId, UUID userId) {
        memories.requireVisible(memoryId, userId);
        reactions.deleteMine(memoryId, userId);
        return engagement.reactions(memoryId, userId);
    }

    @Transactional(readOnly = true)
    public ReactionSummary summary(UUID memoryId, UUID viewerId) {
        memories.requireVisible(memoryId, viewerId);
        return engagement.reactions(memoryId, viewerId);
    }
}
