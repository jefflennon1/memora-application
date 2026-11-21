package br.com.memora.memory.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Interações de uma memória (comentários e reações), vistas por quem consulta. Implementada pelo módulo
 * {@code interaction}; o módulo de memórias só conhece este contrato.
 */
public interface MemoryEngagement {

    /**
     * Contagens das memórias informadas, todas do mesmo evento.
     *
     * @param viewerId             quem consulta; {@code null} para visitante anônimo
     * @param viewerIsParticipant  se quem consulta participa do evento (vê também os comentários restritos)
     */
    Map<UUID, Engagement> of(Collection<UUID> memoryIds, UUID viewerId, boolean viewerIsParticipant);

    /**
     * A memória deixou de ser pública: os comentários e respostas dela também passam a ser só para participantes.
     * Assim, se a memória voltar a ser pública, nada do que foi dito volta a ficar exposto sem decisão de quem
     * escreveu.
     */
    void memoryRestricted(UUID memoryId);

    /**
     * @param commentCount comentários e respostas que quem consulta pode ver
     */
    record Engagement(long commentCount, ReactionSummary reactions) {

        public static final Engagement NONE = new Engagement(0, ReactionSummary.NONE);
    }

    /**
     * Reações de uma memória: quantas de cada emoji (mais usados primeiro) e a de quem consulta. Nunca quem reagiu.
     */
    record ReactionSummary(long total, List<ReactionCount> items, String mine) {

        public static final ReactionSummary NONE = new ReactionSummary(0, List.of(), null);
    }

    record ReactionCount(String emoji, long count) {
    }
}
