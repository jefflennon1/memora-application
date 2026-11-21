-- V8 — Áudios curtos em memórias, comentários e respostas.
--
-- Um arquivo nasce pendente e depois pertence a uma memória OU a um comentário.
-- Áudios têm duração máxima de 30 segundos; a aplicação valida o conteúdo antes de persistir.

ALTER TABLE media_file
    ALTER COLUMN width DROP NOT NULL,
    ALTER COLUMN height DROP NOT NULL,
    ADD COLUMN duration_ms INTEGER,
    ADD COLUMN comment_id UUID,
    ADD CONSTRAINT fk_media_file_comment
        FOREIGN KEY (comment_id) REFERENCES comment (id) ON DELETE CASCADE;

ALTER TABLE media_file DROP CONSTRAINT ck_media_file_kind;
ALTER TABLE media_file ADD CONSTRAINT ck_media_file_kind CHECK (kind IN ('IMAGE', 'AUDIO'));

ALTER TABLE media_file DROP CONSTRAINT ck_media_file_attachment;
ALTER TABLE media_file ADD CONSTRAINT ck_media_file_attachment CHECK (
    (memory_id IS NULL AND comment_id IS NULL AND position IS NULL AND attached_at IS NULL)
    OR
    (memory_id IS NOT NULL AND comment_id IS NULL AND position IS NOT NULL AND attached_at IS NOT NULL)
    OR
    (memory_id IS NULL AND comment_id IS NOT NULL AND position IS NULL AND attached_at IS NOT NULL)
);

ALTER TABLE media_file DROP CONSTRAINT ck_media_file_dimensions;
ALTER TABLE media_file ADD CONSTRAINT ck_media_file_metadata CHECK (
    (kind = 'IMAGE' AND width IS NOT NULL AND width > 0 AND height IS NOT NULL AND height > 0
        AND duration_ms IS NULL)
    OR
    (kind = 'AUDIO' AND width IS NULL AND height IS NULL AND duration_ms IS NOT NULL
        AND duration_ms > 0 AND duration_ms <= 30000)
);

CREATE UNIQUE INDEX uq_media_file_comment ON media_file (comment_id) WHERE comment_id IS NOT NULL;
CREATE INDEX ix_media_file_comment_lookup ON media_file (comment_id) WHERE comment_id IS NOT NULL;

ALTER TABLE comment ALTER COLUMN text DROP NOT NULL;
ALTER TABLE comment DROP CONSTRAINT ck_comment_text_not_blank;
ALTER TABLE comment ADD CONSTRAINT ck_comment_text_not_blank CHECK (text IS NULL OR btrim(text) <> '');

