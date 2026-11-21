package br.com.memora.interaction.domain;

import br.com.memora.shared.error.ValidationException;
import br.com.memora.shared.persistence.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

/**
 * Comentário numa memória ({@code parentId} nulo) ou resposta a um comentário principal. Só há dois níveis: não
 * existe resposta de resposta.
 */
@Entity
@Table(name = "comment")
public class Comment extends BaseEntity {

    /** Limite de texto, contado em caracteres (code points: um emoji conta como um). */
    public static final int TEXT_MAX = 200;

    @Column(name = "memory_id", nullable = false, updatable = false)
    private UUID memoryId;

    @Column(name = "parent_id", updatable = false)
    private UUID parentId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "text", length = TEXT_MAX)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 20, nullable = false)
    private CommentVisibility visibility;

    @ElementCollection
    @CollectionTable(name = "comment_mention", joinColumns = @JoinColumn(name = "comment_id"))
    @OrderColumn(name = "position")
    @BatchSize(size = 50)
    private List<Mention> mentions = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Comment() {
        // JPA
    }

    /**
     * @param parentId comentário principal a que esta resposta se refere; {@code null} para um comentário principal
     * @param text     já normalizado por {@link #normalizeText}
     */
    public static Comment write(UUID memoryId, UUID parentId, UUID authorId, String text, List<Mention> mentions,
                                CommentVisibility visibility, Instant now) {
        Comment comment = new Comment();
        comment.memoryId = Objects.requireNonNull(memoryId);
        comment.parentId = parentId;
        comment.authorId = Objects.requireNonNull(authorId);
        comment.text = text;
        comment.mentions.addAll(mentions);
        comment.visibility = Objects.requireNonNull(visibility);
        comment.createdAt = now;
        comment.updatedAt = now;
        return comment;
    }

    /**
     * Edição pelo autor. {@code newText} {@code null} mantém o texto (e as menções). A data de publicação não muda.
     */
    public void edit(String newText, boolean updateText, List<Mention> newMentions,
                     CommentVisibility newVisibility, Instant now) {
        if (updateText) {
            this.text = newText;
            this.mentions.clear();
            this.mentions.addAll(newMentions);
        }
        this.visibility = Objects.requireNonNull(newVisibility);
        this.editedAt = now;
    }

    /** Passa a ser só para participantes (a memória ou o comentário principal deixou de ser público). */
    public void restrict() {
        this.visibility = CommentVisibility.PARTICIPANTS_ONLY;
    }

    public boolean isReply() {
        return parentId != null;
    }

    public boolean isPublic() {
        return visibility == CommentVisibility.PUBLIC;
    }

    public boolean isAuthoredBy(UUID userId) {
        return authorId.equals(userId);
    }

    @PreUpdate
    void touch() {
        // Precisão do banco (microssegundos).
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** Remove espaços nas pontas e valida. Texto pode faltar quando o comentário contém áudio. */
    public static String normalizeText(String raw, boolean hasAudio) {
        if (raw == null || raw.isBlank()) {
            if (hasAudio) return null;
            throw new ValidationException("COMMENT_EMPTY", "Escreva alguma coisa ou adicione um áudio.");
        }
        String stripped = raw.strip();
        if (stripped.codePointCount(0, stripped.length()) > TEXT_MAX) {
            throw new ValidationException("COMMENT_TEXT_TOO_LONG",
                    "O comentário pode ter no máximo " + TEXT_MAX + " caracteres.");
        }
        return stripped;
    }

    public UUID getMemoryId() {
        return memoryId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getText() {
        return text;
    }

    public CommentVisibility getVisibility() {
        return visibility;
    }

    public List<Mention> getMentions() {
        return List.copyOf(mentions);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getEditedAt() {
        return editedAt;
    }
}
