-- V12 — Vídeos curtos apenas em memórias/publicações.
-- O conteúdo é validado pela aplicação e limitado a 60 segundos.

ALTER TABLE media_file DROP CONSTRAINT ck_media_file_kind;
ALTER TABLE media_file ADD CONSTRAINT ck_media_file_kind CHECK (kind IN ('IMAGE', 'AUDIO', 'VIDEO'));

ALTER TABLE media_file DROP CONSTRAINT ck_media_file_metadata;
ALTER TABLE media_file ADD CONSTRAINT ck_media_file_metadata CHECK (
    (kind = 'IMAGE' AND width IS NOT NULL AND width > 0 AND height IS NOT NULL AND height > 0
        AND duration_ms IS NULL)
    OR
    (kind = 'AUDIO' AND width IS NULL AND height IS NULL AND duration_ms IS NOT NULL
        AND duration_ms > 0 AND duration_ms <= 30000)
    OR
    (kind = 'VIDEO' AND width IS NOT NULL AND width > 0 AND height IS NOT NULL AND height > 0
        AND duration_ms IS NOT NULL AND duration_ms > 0 AND duration_ms <= 60000)
);
