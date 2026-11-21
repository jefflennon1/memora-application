package br.com.memora.identity.domain;

/** Estado da conta interna. */
public enum UserStatus {
    /** Conta criada no primeiro login; o usuário ainda não confirmou nome, foto e @usuário. */
    PENDING_ONBOARDING,
    /** Conta com onboarding concluído: pode usar a plataforma. */
    ACTIVE,
    /** Conta bloqueada pela moderação. */
    SUSPENDED,
    /** Conta excluída; referências passam a exibir "Usuário excluído". */
    DELETED;

    public boolean isDisabled() {
        return this == SUSPENDED || this == DELETED;
    }
}
