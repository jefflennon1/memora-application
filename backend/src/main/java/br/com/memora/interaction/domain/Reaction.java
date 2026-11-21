package br.com.memora.interaction.domain;

import br.com.memora.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A reação (um emoji) de uma pessoa a uma memória. No máximo uma por pessoa e memória. Gravada por
 * {@link ReactionRepository#upsert}; quem reagiu nunca sai da API.
 */
@Entity
@Table(name = "reaction")
public class Reaction extends BaseEntity {

    @Column(name = "memory_id", nullable = false, updatable = false)
    private UUID memoryId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "emoji", length = 32, nullable = false)
    private String emoji;

    @Column(name = "reacted_at", nullable = false)
    private Instant reactedAt;

    protected Reaction() {
        // JPA
    }

    public UUID getMemoryId() {
        return memoryId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmoji() {
        return emoji;
    }

    public Instant getReactedAt() {
        return reactedAt;
    }
}
