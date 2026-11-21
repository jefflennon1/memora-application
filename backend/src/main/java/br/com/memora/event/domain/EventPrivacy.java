package br.com.memora.event.domain;

/** Privacidade do evento. Prevalece sobre a privacidade de cada publicação. */
public enum EventPrivacy {

    /** Qualquer pessoa vê os dados do evento, e ele aparece na busca pública. */
    PUBLIC,
    /** Só participantes sabem que o evento existe. */
    PRIVATE
}
