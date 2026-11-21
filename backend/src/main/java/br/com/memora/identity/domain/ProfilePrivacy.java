package br.com.memora.identity.domain;

/** Privacidade do perfil do usuário. */
public enum ProfilePrivacy {
    /** Nome, foto e @usuário visíveis a qualquer pessoa autenticada. */
    PUBLIC,
    /** Perfil visível apenas a quem participa de algum evento em comum. */
    PRIVATE
}
