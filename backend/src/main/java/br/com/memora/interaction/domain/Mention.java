package br.com.memora.interaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma @menção dentro do texto de um comentário. Aponta para a pessoa ({@code userId}), não para o texto do
 * @usuário: se ela trocar o @usuário, a menção passa a mostrar o novo.
 *
 * <p>{@code start}/{@code length} marcam o trecho "@usuario" no texto gravado, em unidades UTF-16 (as mesmas de
 * {@link String#substring} e de JavaScript).
 */
@Embeddable
public class Mention {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "start_index", nullable = false)
    private int start;

    @Column(name = "length", nullable = false)
    private int length;

    protected Mention() {
        // JPA
    }

    public Mention(UUID userId, int start, int length) {
        this.userId = Objects.requireNonNull(userId);
        this.start = start;
        this.length = length;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getStart() {
        return start;
    }

    public int getLength() {
        return length;
    }

    public int getEnd() {
        return start + length;
    }
}
