-- V5 — Convites e solicitações de ingresso.
--
-- Convite NAMED: destinado a uma conta específica; quem foi convidado entra direto (uso único).
-- Convite LINK / QR_CODE: genérico; quem entra por ele gera uma solicitação que a organização aprova ou rejeita.
-- O token é a "chave" do link: aleatório (256 bits), nunca derivado de dados do evento ou da pessoa.

CREATE TABLE event_invite (
    id              UUID         NOT NULL,
    event_id        UUID         NOT NULL,
    token           VARCHAR(64)  NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    invitee_user_id UUID,
    created_by      UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ,
    used_at         TIMESTAMPTZ,
    CONSTRAINT pk_event_invite PRIMARY KEY (id),
    CONSTRAINT uq_event_invite_token UNIQUE (token),
    CONSTRAINT fk_event_invite_event FOREIGN KEY (event_id) REFERENCES event (id),
    CONSTRAINT fk_event_invite_invitee FOREIGN KEY (invitee_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_event_invite_created_by FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT ck_event_invite_type CHECK (type IN ('NAMED', 'LINK', 'QR_CODE')),
    -- Só o convite nominal tem destinatário, e ele é obrigatório.
    CONSTRAINT ck_event_invite_named_invitee CHECK ((type = 'NAMED') = (invitee_user_id IS NOT NULL)),
    CONSTRAINT ck_event_invite_used_only_named CHECK (used_at IS NULL OR type = 'NAMED'),
    CONSTRAINT ck_event_invite_expiry CHECK (expires_at > created_at)
);

COMMENT ON TABLE event_invite IS 'Convites para eventos (nominal, link genérico ou QR Code).';

CREATE INDEX ix_event_invite_event ON event_invite (event_id, created_at DESC);
CREATE INDEX ix_event_invite_invitee ON event_invite (invitee_user_id) WHERE invitee_user_id IS NOT NULL;

CREATE TABLE join_request (
    id           UUID         NOT NULL,
    event_id     UUID         NOT NULL,
    user_id      UUID         NOT NULL,
    invite_id    UUID         NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    requested_at TIMESTAMPTZ  NOT NULL,
    decided_at   TIMESTAMPTZ,
    decided_by   UUID,
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_join_request PRIMARY KEY (id),
    CONSTRAINT fk_join_request_event FOREIGN KEY (event_id) REFERENCES event (id),
    CONSTRAINT fk_join_request_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_join_request_invite FOREIGN KEY (invite_id) REFERENCES event_invite (id),
    CONSTRAINT fk_join_request_decided_by FOREIGN KEY (decided_by) REFERENCES app_user (id),
    CONSTRAINT ck_join_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_join_request_decision
        CHECK ((status = 'PENDING') = (decided_at IS NULL AND decided_by IS NULL))
);

COMMENT ON TABLE join_request IS 'Pedidos de entrada feitos por link genérico; a organização aprova ou rejeita.';

-- No máximo um pedido pendente por pessoa e evento.
CREATE UNIQUE INDEX uq_join_request_pending ON join_request (event_id, user_id) WHERE status = 'PENDING';
CREATE INDEX ix_join_request_event_status ON join_request (event_id, status, requested_at);
CREATE INDEX ix_join_request_user ON join_request (user_id, event_id);
