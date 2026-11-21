package br.com.memora.interaction.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReactionRepository extends JpaRepository<Reaction, UUID> {

    /**
     * Cria ou troca a reação da pessoa, sem corrida entre pedidos simultâneos. Reagir de novo com o mesmo emoji
     * não muda nada.
     */
    @Modifying
    @Query(value = """
            insert into reaction (id, memory_id, user_id, emoji, reacted_at)
            values (:id, :memoryId, :userId, :emoji, :now)
            on conflict (memory_id, user_id) do update
                set emoji = excluded.emoji,
                    reacted_at = case when reaction.emoji = excluded.emoji then reaction.reacted_at
                                      else excluded.reacted_at end""", nativeQuery = true)
    int upsert(@Param("id") UUID id, @Param("memoryId") UUID memoryId, @Param("userId") UUID userId,
               @Param("emoji") String emoji, @Param("now") Instant now);

    @Modifying
    @Query("delete from Reaction r where r.memoryId = :memoryId and r.userId = :userId")
    int deleteMine(@Param("memoryId") UUID memoryId, @Param("userId") UUID userId);

    /** Quantas reações de cada emoji, por memória. {@code firstAt} desempata emojis com a mesma contagem. */
    @Query("""
            select r.memoryId as memoryId, r.emoji as emoji, count(r) as total, min(r.reactedAt) as firstAt
            from Reaction r where r.memoryId in :memoryIds group by r.memoryId, r.emoji""")
    List<EmojiCount> countByEmoji(@Param("memoryIds") Collection<UUID> memoryIds);

    List<Reaction> findByUserIdAndMemoryIdIn(UUID userId, Collection<UUID> memoryIds);

    interface EmojiCount {
        UUID getMemoryId();

        String getEmoji();

        Long getTotal();

        Instant getFirstAt();
    }
}
