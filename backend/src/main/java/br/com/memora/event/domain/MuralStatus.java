package br.com.memora.event.domain;

/** Estado do mural. Muda só por decisão do organizador: o mural nunca fecha sozinho. */
public enum MuralStatus {

    /** Aceita novas memórias. */
    OPEN,
    /** Somente leitura. */
    CLOSED
}
