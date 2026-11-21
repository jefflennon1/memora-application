-- V4 — Participantes de evento e seus papéis.
--
-- Nesta etapa só o organizador entra (na criação do evento). Convites, solicitações de ingresso e a
-- gestão de coorganizadores chegam na Entrega 4 e usam esta mesma tabela.

CREATE TABLE event_participant (
    id         UUID         NOT NULL,
    event_id   UUID         NOT NULL,
    user_id    UUID         NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    joined_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_event_participant PRIMARY KEY (id),
    CONSTRAINT fk_event_participant_event FOREIGN KEY (event_id) REFERENCES event (id),
    CONSTRAINT fk_event_participant_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_event_participant_role CHECK (role IN ('ORGANIZER', 'CO_ORGANIZER', 'PARTICIPANT')),
    -- Uma pessoa participa uma única vez de cada evento (também indexa event_id).
    CONSTRAINT uq_event_participant_event_user UNIQUE (event_id, user_id)
);

COMMENT ON TABLE event_participant IS 'Pessoas autorizadas em um evento e seu papel.';

-- Exatamente um organizador principal por evento.
CREATE UNIQUE INDEX uq_event_participant_single_organizer ON event_participant (event_id) WHERE role = 'ORGANIZER';
-- "Meus eventos".
CREATE INDEX ix_event_participant_user ON event_participant (user_id, event_id);
