-- V3 — Eventos: o acontecimento que reúne as memórias.
--
-- Datas e horários do evento são "de parede" (sem fuso): o dia e a hora em que as pessoas se encontram.
-- As colunas *_search guardam nome e cidade normalizados (minúsculas, sem acentos) pela aplicação,
-- para a busca pública sem depender de extensões do PostgreSQL.

CREATE TABLE event (
    id                      UUID          NOT NULL,
    name                    VARCHAR(120)  NOT NULL,
    name_search             VARCHAR(120)  NOT NULL,
    description             VARCHAR(2000),
    category_code           VARCHAR(40),
    start_date              DATE          NOT NULL,
    start_time              TIME,
    end_date                DATE,
    end_time                TIME,
    modality                VARCHAR(20)   NOT NULL,
    city                    VARCHAR(120),
    city_search             VARCHAR(120),
    address                 VARCHAR(300),
    privacy                 VARCHAR(20)   NOT NULL,
    mural_status            VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    mural_status_changed_at TIMESTAMPTZ,
    organizer_id            UUID          NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_event PRIMARY KEY (id),
    CONSTRAINT fk_event_category FOREIGN KEY (category_code) REFERENCES event_category (code),
    -- O organizador principal é permanente: o título não é transferido.
    CONSTRAINT fk_event_organizer FOREIGN KEY (organizer_id) REFERENCES app_user (id),
    CONSTRAINT ck_event_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_event_modality CHECK (modality IN ('IN_PERSON', 'ONLINE', 'NO_LOCATION')),
    CONSTRAINT ck_event_privacy CHECK (privacy IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT ck_event_mural_status CHECK (mural_status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_event_end_after_start CHECK (end_date IS NULL OR end_date >= start_date),
    -- Cidade e endereço só fazem sentido em eventos presenciais.
    CONSTRAINT ck_event_location_in_person
        CHECK (modality = 'IN_PERSON' OR (city IS NULL AND address IS NULL))
);

COMMENT ON TABLE event IS 'Eventos da Memora. A privacidade do evento prevalece sobre a das publicações.';
COMMENT ON COLUMN event.mural_status IS 'OPEN aceita novas memórias; CLOSED é somente leitura. Nunca fecha sozinho.';

-- "Meus eventos como organizador" e a FK do organizador.
CREATE INDEX ix_event_organizer ON event (organizer_id);
-- Busca pública: apenas eventos públicos, ordenados pela data.
CREATE INDEX ix_event_public_start ON event (start_date DESC, id) WHERE privacy = 'PUBLIC';
CREATE INDEX ix_event_category ON event (category_code) WHERE category_code IS NOT NULL;
