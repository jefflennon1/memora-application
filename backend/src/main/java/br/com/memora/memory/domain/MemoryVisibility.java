package br.com.memora.memory.domain;

/**
 * Quem vê uma memória. A privacidade do evento vale acima desta: em evento privado, só participantes veem qualquer
 * memória.
 */
public enum MemoryVisibility {

    /** Qualquer pessoa que veja o evento (só possível em evento público). */
    PUBLIC,
    /** Só participantes do evento. Padrão. */
    PARTICIPANTS_ONLY
}
