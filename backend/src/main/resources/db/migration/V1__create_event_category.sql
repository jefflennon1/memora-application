-- V1 — Categorias de evento (dado de referência obrigatório em todos os ambientes).
-- A categoria é opcional no evento; esta tabela define o catálogo permitido.

CREATE TABLE event_category (
    code          VARCHAR(40)  NOT NULL,
    name          VARCHAR(80)  NOT NULL,
    display_order SMALLINT     NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_event_category PRIMARY KEY (code),
    CONSTRAINT uq_event_category_name UNIQUE (name),
    CONSTRAINT ck_event_category_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]*$')
);

COMMENT ON TABLE event_category IS 'Catálogo de categorias de evento da Memora.';

INSERT INTO event_category (code, name, display_order) VALUES
    ('BABY_SHOWER',      'Chá de bebê / Chá de fraldas', 10),
    ('BIRTHDAY',         'Aniversário',                  20),
    ('WEDDING',          'Casamento',                    30),
    ('ENGAGEMENT',       'Noivado',                      40),
    ('WEDDING_ANNIV',    'Bodas',                        50),
    ('BAPTISM',          'Batizado',                     60),
    ('GRADUATION',       'Formatura',                    70),
    ('FAMILY_GATHERING', 'Encontro de família',          80),
    ('REUNION',          'Reencontro',                   90),
    ('TRIP',             'Viagem',                      100),
    ('PARTY',            'Festa',                       110),
    ('TRIBUTE',          'Homenagem',                   120),
    ('RELIGIOUS',        'Religioso',                   130),
    ('CULTURAL',         'Cultural',                    140),
    ('SPORTS',           'Esportivo',                   150),
    ('CORPORATE',        'Corporativo',                 160),
    ('OTHER',            'Outro',                       999);
