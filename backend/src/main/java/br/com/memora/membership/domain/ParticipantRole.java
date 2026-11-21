package br.com.memora.membership.domain;

/**
 * Papel de uma pessoa em um evento.
 *
 * <p>Regras de coorganização: o coorganizador ajuda a administrar (edita os dados do evento, cria e revoga
 * convites, aprova ou rejeita solicitações e remove participantes comuns). Fica com o organizador: privacidade
 * do evento, abrir/fechar o mural e promover, rebaixar ou remover coorganizadores.
 */
public enum ParticipantRole {

    /** Criador do evento. Permanente: o título não é transferido e ele não pode sair nem ser removido. */
    ORGANIZER,
    /** Ajuda a administrar o evento. */
    CO_ORGANIZER,
    /** Participante autorizado: vê o evento privado e publica no mural aberto. */
    PARTICIPANT;

    /** Organizador ou coorganizador. */
    public boolean canManageEvent() {
        return this == ORGANIZER || this == CO_ORGANIZER;
    }

    public boolean isOrganizer() {
        return this == ORGANIZER;
    }
}
