ALTER TABLE event_invite
    ADD COLUMN page_title VARCHAR(120),
    ADD COLUMN page_body VARCHAR(3000),
    ADD COLUMN page_footer VARCHAR(500),
    ADD COLUMN page_location VARCHAR(300),
    ADD COLUMN background_color VARCHAR(7),
    ADD COLUMN background_gradient_color VARCHAR(7),
    ADD COLUMN background_image_layout VARCHAR(10),
    ADD COLUMN background_storage_key VARCHAR(200),
    ADD COLUMN background_content_type VARCHAR(50),
    ADD COLUMN background_updated_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_event_invite_background_layout
        CHECK (background_image_layout IS NULL OR background_image_layout IN ('COVER', 'REPEAT'));

UPDATE event_invite i
SET page_title = 'Você está convidado para ' || e.name,
    page_body = 'Esperamos você para compartilhar este momento com a gente.',
    page_location = e.city,
    background_color = '#163f47',
    background_gradient_color = '#256a75',
    background_image_layout = 'COVER'
FROM event e
WHERE e.id = i.event_id;

ALTER TABLE event_invite
    ALTER COLUMN page_title SET NOT NULL,
    ALTER COLUMN page_body SET NOT NULL,
    ALTER COLUMN background_color SET NOT NULL,
    ALTER COLUMN background_image_layout SET NOT NULL;

CREATE TABLE invite_response (
    id           UUID        PRIMARY KEY,
    invite_id    UUID        NOT NULL,
    user_id      UUID        NOT NULL,
    status       VARCHAR(20) NOT NULL,
    responded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_invite_response_invite FOREIGN KEY (invite_id) REFERENCES event_invite (id) ON DELETE CASCADE,
    CONSTRAINT fk_invite_response_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uq_invite_response_invite_user UNIQUE (invite_id, user_id),
    CONSTRAINT ck_invite_response_status CHECK (status IN ('DECLINED'))
);

CREATE INDEX ix_invite_response_user ON invite_response (user_id, responded_at DESC);
