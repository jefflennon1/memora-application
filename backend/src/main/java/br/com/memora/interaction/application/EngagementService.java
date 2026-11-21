package br.com.memora.interaction.application;

import br.com.memora.interaction.domain.CommentRepository;
import br.com.memora.interaction.domain.CommentRepository.IdCount;
import br.com.memora.interaction.domain.CommentVisibility;
import br.com.memora.interaction.domain.Reaction;
import br.com.memora.interaction.domain.ReactionRepository;
import br.com.memora.interaction.domain.ReactionRepository.EmojiCount;
import br.com.memora.memory.application.MemoryEngagement;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contagens de comentários e reações das memórias, em poucas consultas por página do mural.
 *
 * <p>Quem não participa do evento só conta os comentários públicos: comentários restritos não aparecem nem na
 * contagem.
 */
@Service
public class EngagementService implements MemoryEngagement {

    private static final Comparator<EmojiCount> MOST_USED_FIRST = Comparator
            .comparing(EmojiCount::getTotal, Comparator.reverseOrder())
            .thenComparing(EmojiCount::getFirstAt)
            .thenComparing(EmojiCount::getEmoji);

    private final CommentRepository comments;
    private final ReactionRepository reactions;

    public EngagementService(CommentRepository comments, ReactionRepository reactions) {
        this.comments = comments;
        this.reactions = reactions;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Engagement> of(Collection<UUID> memoryIds, UUID viewerId, boolean viewerIsParticipant) {
        Set<UUID> ids = Set.copyOf(memoryIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<IdCount> counts = viewerIsParticipant
                ? comments.countByMemory(ids)
                : comments.countByMemory(ids, CommentVisibility.PUBLIC);
        Map<UUID, Long> commentCounts = counts.stream().collect(Collectors.toMap(IdCount::getId, IdCount::getTotal));
        Map<UUID, ReactionSummary> summaries = reactionSummaries(ids, viewerId);

        Map<UUID, Engagement> result = new HashMap<>();
        for (UUID id : ids) {
            result.put(id, new Engagement(commentCounts.getOrDefault(id, 0L),
                    summaries.getOrDefault(id, ReactionSummary.NONE)));
        }
        return result;
    }

    @Override
    @Transactional
    public void memoryRestricted(UUID memoryId) {
        comments.restrictAllOfMemory(memoryId);
    }

    @Transactional(readOnly = true)
    public ReactionSummary reactions(UUID memoryId, UUID viewerId) {
        return reactionSummaries(Set.of(memoryId), viewerId).getOrDefault(memoryId, ReactionSummary.NONE);
    }

    private Map<UUID, ReactionSummary> reactionSummaries(Set<UUID> memoryIds, UUID viewerId) {
        Map<UUID, List<EmojiCount>> byMemory = reactions.countByEmoji(memoryIds).stream()
                .collect(Collectors.groupingBy(EmojiCount::getMemoryId));
        Map<UUID, String> mine = viewerId == null ? Map.of()
                : reactions.findByUserIdAndMemoryIdIn(viewerId, memoryIds).stream()
                        .collect(Collectors.toMap(Reaction::getMemoryId, Reaction::getEmoji));

        Map<UUID, ReactionSummary> result = new HashMap<>();
        byMemory.forEach((memoryId, emojis) -> {
            List<ReactionCount> items = emojis.stream().sorted(MOST_USED_FIRST)
                    .map(e -> new ReactionCount(e.getEmoji(), e.getTotal()))
                    .toList();
            long total = items.stream().mapToLong(ReactionCount::count).sum();
            result.put(memoryId, new ReactionSummary(total, items, mine.get(memoryId)));
        });
        return result;
    }
}
