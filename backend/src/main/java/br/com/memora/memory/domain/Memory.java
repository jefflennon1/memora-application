package br.com.memora.memory.domain;

import br.com.memora.shared.error.BusinessRuleException;
import br.com.memora.shared.error.ValidationException;
import br.com.memora.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma publicação no mural de um evento: texto, fotos, áudio ou uma combinação deles.
 *
 * <p>A data de publicação ({@code createdAt}) nunca muda: editar não faz a memória "subir" no mural.
 */
@Entity
@Table(name = "memory")
public class Memory extends BaseEntity {

    /** Limite de texto, contado em caracteres (code points: um emoji conta como um). */
    public static final int TEXT_MAX = 500;
    public static final int MEDIA_MAX = 10;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "text", length = TEXT_MAX)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 20, nullable = false)
    private MemoryVisibility visibility;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Memory() {
        // JPA
    }

    /**
     * @param mediaCount quantidade de mídias que serão vinculadas
     */
    public static Memory publish(UUID eventId, UUID authorId, String text, int mediaCount,
                                 MemoryVisibility visibility, Instant now) {
        Memory memory = new Memory();
        memory.eventId = Objects.requireNonNull(eventId);
        memory.authorId = Objects.requireNonNull(authorId);
        memory.text = normalizeText(text);
        requireContent(memory.text, mediaCount);
        memory.visibility = Objects.requireNonNull(visibility);
        memory.createdAt = now;
        memory.updatedAt = now;
        return memory;
    }

    /** Edição pelo autor. Só altera a data de edição, nunca a de publicação. */
    public void edit(String newText, int mediaCount, MemoryVisibility newVisibility, Instant now) {
        String normalized = normalizeText(newText);
        requireContent(normalized, mediaCount);
        this.text = normalized;
        this.visibility = Objects.requireNonNull(newVisibility);
        this.editedAt = now;
    }

    public boolean isAuthoredBy(UUID userId) {
        return authorId.equals(userId);
    }

    @PreUpdate
    void touch() {
        // Precisão do banco (microssegundos).
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    static String normalizeText(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String stripped = raw.strip();
        if (stripped.codePointCount(0, stripped.length()) > TEXT_MAX) {
            throw new ValidationException("MEMORY_TEXT_TOO_LONG",
                    "O texto da memória pode ter no máximo " + TEXT_MAX + " caracteres.");
        }
        return stripped;
    }

    private static void requireContent(String text, int mediaCount) {
        if (mediaCount > MEDIA_MAX) {
            throw new BusinessRuleException("TOO_MANY_MEDIA",
                    "Uma memória pode ter no máximo " + MEDIA_MAX + " mídias.");
        }
        if (text == null && mediaCount == 0) {
            throw new BusinessRuleException("MEMORY_EMPTY", "Escreva um texto ou adicione uma foto ou áudio.");
        }
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getText() {
        return text;
    }

    public MemoryVisibility getVisibility() {
        return visibility;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getEditedAt() {
        return editedAt;
    }
}
