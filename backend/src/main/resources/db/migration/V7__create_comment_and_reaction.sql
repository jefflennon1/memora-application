-- V7 — Interações nas memórias: comentários, respostas (dois níveis), @menções e reações por emoji.
--
-- Tudo é apagado junto com a memória (ON DELETE CASCADE). Um comentário principal apagado leva junto as suas
-- respostas.

CREATE TABLE comment (
    id          UUID          NOT NULL,
    memory_id   UUID          NOT NULL,
    parent_id   UUID,
    author_id   UUID          NOT NULL,
    text        VARCHAR(200)  NOT NULL,
    visibility  VARCHAR(20)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    edited_at   TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ   NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_comment PRIMARY KEY (id),
    CONSTRAINT fk_comment_memory FOREIGN KEY (memory_id) REFERENCES memory (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES comment (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES app_user (id),
    CONSTRAINT ck_comment_visibility CHECK (visibility IN ('PUBLIC', 'PARTICIPANTS_ONLY')),
    CONSTRAINT ck_comment_text_not_blank CHECK (btrim(text) <> ''),
    CONSTRAINT ck_comment_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

COMMENT ON TABLE comment IS 'Comentários (parent_id nulo) e respostas a um comentário principal. Não há resposta de resposta.';
COMMENT ON COLUMN comment.visibility IS 'Nunca mais público que a memória; uma resposta nunca mais pública que o comentário.';

-- Comentários principais de uma memória e respostas de um comentário, em ordem cronológica.
CREATE INDEX ix_comment_memory_created ON comment (memory_id, created_at, id) WHERE parent_id IS NULL;
CREATE INDEX ix_comment_parent_created ON comment (parent_id, created_at, id) WHERE parent_id IS NOT NULL;
CREATE INDEX ix_comment_author ON comment (author_id);

-- @menções: apontam para a pessoa (user_id), não para o texto do @usuário. start_index/length marcam o trecho
-- "@usuario" dentro de comment.text (em unidades UTF-16, como em JavaScript).
CREATE TABLE comment_mention (
    comment_id   UUID      NOT NULL,
    position     INTEGER   NOT NULL,
    user_id      UUID      NOT NULL,
    start_index  INTEGER   NOT NULL,
    length       INTEGER   NOT NULL,
    CONSTRAINT pk_comment_mention PRIMARY KEY (comment_id, position),
    CONSTRAINT fk_comment_mention_comment FOREIGN KEY (comment_id) REFERENCES comment (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_mention_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_comment_mention_range CHECK (start_index >= 0 AND length > 0)
);

CREATE INDEX ix_comment_mention_user ON comment_mention (user_id);

-- Uma reação (um emoji) por pessoa em cada memória. Reagir de novo troca o emoji.
CREATE TABLE reaction (
    id          UUID          NOT NULL,
    memory_id   UUID          NOT NULL,
    user_id     UUID          NOT NULL,
    emoji       VARCHAR(32)   NOT NULL,
    reacted_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_reaction PRIMARY KEY (id),
    CONSTRAINT fk_reaction_memory FOREIGN KEY (memory_id) REFERENCES memory (id) ON DELETE CASCADE,
    CONSTRAINT fk_reaction_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uq_reaction_memory_user UNIQUE (memory_id, user_id)
);

COMMENT ON TABLE reaction IS 'Quem reagiu nunca é exposto pela API: só a contagem por emoji e a reação de quem consulta.';

CREATE INDEX ix_reaction_user ON reaction (user_id);
