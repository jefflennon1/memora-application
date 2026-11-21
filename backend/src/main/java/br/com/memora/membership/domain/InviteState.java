package br.com.memora.membership.domain;

/** Situação de um convite num dado momento. */
public enum InviteState {
    ACTIVE,
    EXPIRED,
    REVOKED,
    /** Convite nominal já aceito. */
    USED
}
