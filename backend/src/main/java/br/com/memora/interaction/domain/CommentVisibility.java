package br.com.memora.interaction.domain;

/**
 * Quem vê um comentário, dentro de quem já vê a memória. Um comentário nunca é mais público que a memória, e uma
 * resposta nunca é mais pública que o comentário a que responde.
 */
public enum CommentVisibility {
    /** Qualquer pessoa que vê a memória (só aceito em memória pública de evento público). */
    PUBLIC,
    /** Só participantes do evento (padrão). */
    PARTICIPANTS_ONLY
}
