package br.com.memora.membership.domain;

/** Forma do convite. */
public enum InviteType {

    /** Para uma conta específica: a pessoa convidada entra direto. Uso único. */
    NAMED,
    /** Link genérico: quem entra por ele gera uma solicitação de ingresso. */
    LINK,
    /** Igual ao link genérico, apresentado como QR Code (a imagem é gerada pelo frontend). */
    QR_CODE;

    public boolean requiresApproval() {
        return this != NAMED;
    }
}
