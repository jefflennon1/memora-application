-- V11 — Imagens gerenciadas de perfil e capa de evento.
-- O conteúdo permanece no armazenamento privado; o banco guarda somente metadados e a chave opaca.

ALTER TABLE app_user
    ADD COLUMN photo_storage_key VARCHAR(200),
    ADD COLUMN photo_content_type VARCHAR(50),
    ADD COLUMN photo_size_bytes BIGINT,
    ADD COLUMN photo_width INTEGER,
    ADD COLUMN photo_height INTEGER,
    ADD COLUMN photo_updated_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_app_user_uploaded_photo_complete CHECK (
        (photo_storage_key IS NULL AND photo_content_type IS NULL AND photo_size_bytes IS NULL
            AND photo_width IS NULL AND photo_height IS NULL AND photo_updated_at IS NULL)
        OR
        (photo_storage_key IS NOT NULL AND photo_content_type IS NOT NULL AND photo_size_bytes > 0
            AND photo_width > 0 AND photo_height > 0 AND photo_updated_at IS NOT NULL)
    );

ALTER TABLE event
    ADD COLUMN cover_storage_key VARCHAR(200),
    ADD COLUMN cover_content_type VARCHAR(50),
    ADD COLUMN cover_size_bytes BIGINT,
    ADD COLUMN cover_width INTEGER,
    ADD COLUMN cover_height INTEGER,
    ADD COLUMN cover_updated_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_event_cover_complete CHECK (
        (cover_storage_key IS NULL AND cover_content_type IS NULL AND cover_size_bytes IS NULL
            AND cover_width IS NULL AND cover_height IS NULL AND cover_updated_at IS NULL)
        OR
        (cover_storage_key IS NOT NULL AND cover_content_type IS NOT NULL AND cover_size_bytes > 0
            AND cover_width > 0 AND cover_height > 0 AND cover_updated_at IS NOT NULL)
    );

COMMENT ON COLUMN app_user.photo_storage_key IS 'Chave opaca da foto enviada pelo usuário no armazenamento privado.';
COMMENT ON COLUMN event.cover_storage_key IS 'Chave opaca da foto de capa no armazenamento privado.';
