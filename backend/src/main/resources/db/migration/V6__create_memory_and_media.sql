-- V6 — Memórias (publicações do mural) e seus arquivos de mídia.
--
-- O conteúdo binário fica no armazenamento de objetos (S3/MinIO, bucket privado); aqui ficam só os metadados.
-- Fluxo: a imagem é enviada primeiro (media_file sem memória = pendente) e vinculada ao publicar a memória.
-- Imagens pendentes esquecidas são apagadas por uma rotina periódica.

CREATE TABLE memory (
    id          UUID          NOT NULL,
    event_id    UUID          NOT NULL,
    author_id   UUID          NOT NULL,
    text        VARCHAR(500),
    visibility  VARCHAR(20)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    edited_at   TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ   NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_memory PRIMARY KEY (id),
    CONSTRAINT fk_memory_event FOREIGN KEY (event_id) REFERENCES event (id),
    CONSTRAINT fk_memory_author FOREIGN KEY (author_id) REFERENCES app_user (id),
    CONSTRAINT ck_memory_visibility CHECK (visibility IN ('PUBLIC', 'PARTICIPANTS_ONLY')),
    CONSTRAINT ck_memory_text_not_blank CHECK (text IS NULL OR btrim(text) <> '')
);

COMMENT ON TABLE memory IS 'Publicações no mural de um evento. A data de publicação nunca muda, nem na edição.';
COMMENT ON COLUMN memory.visibility IS 'PUBLIC só tem efeito em evento público; em evento privado vale PARTICIPANTS_ONLY.';

-- Mural: memórias do evento em ordem de publicação.
CREATE INDEX ix_memory_event_created ON memory (event_id, created_at, id);
CREATE INDEX ix_memory_author ON memory (author_id);

CREATE TABLE media_file (
    id            UUID          NOT NULL,
    event_id      UUID          NOT NULL,
    uploader_id   UUID          NOT NULL,
    memory_id     UUID,
    position      SMALLINT,
    kind          VARCHAR(20)   NOT NULL,
    content_type  VARCHAR(50)   NOT NULL,
    size_bytes    BIGINT        NOT NULL,
    width         INTEGER       NOT NULL,
    height        INTEGER       NOT NULL,
    storage_key   VARCHAR(200)  NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    attached_at   TIMESTAMPTZ,
    CONSTRAINT pk_media_file PRIMARY KEY (id),
    CONSTRAINT fk_media_file_event FOREIGN KEY (event_id) REFERENCES event (id),
    CONSTRAINT fk_media_file_uploader FOREIGN KEY (uploader_id) REFERENCES app_user (id),
    CONSTRAINT fk_media_file_memory FOREIGN KEY (memory_id) REFERENCES memory (id),
    CONSTRAINT uq_media_file_storage_key UNIQUE (storage_key),
    -- Ordem das imagens dentro da memória. Adiável: a edição reordena as posições numa única transação.
    CONSTRAINT uq_media_file_memory_position UNIQUE (memory_id, position) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_media_file_kind CHECK (kind IN ('IMAGE')),
    CONSTRAINT ck_media_file_attachment CHECK ((memory_id IS NULL) = (position IS NULL)
                                               AND (memory_id IS NULL) = (attached_at IS NULL)),
    CONSTRAINT ck_media_file_position CHECK (position IS NULL OR position BETWEEN 0 AND 9),
    CONSTRAINT ck_media_file_size CHECK (size_bytes > 0),
    CONSTRAINT ck_media_file_dimensions CHECK (width > 0 AND height > 0)
);

COMMENT ON TABLE media_file IS 'Metadados de arquivos de mídia. O conteúdo fica no armazenamento de objetos.';

-- Limpeza de imagens enviadas e nunca publicadas.
CREATE INDEX ix_media_file_pending ON media_file (created_at) WHERE memory_id IS NULL;
CREATE INDEX ix_media_file_uploader_pending ON media_file (uploader_id, event_id) WHERE memory_id IS NULL;
