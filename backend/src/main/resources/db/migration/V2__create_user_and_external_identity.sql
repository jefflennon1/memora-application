-- V2 — Conta interna da Memora e identidades externas (Google; futuramente Apple e Facebook).
--
-- A conta é identificada por UUID próprio. O e-mail e o `sub` do provedor NUNCA são chave da conta.
-- Uma identidade externa é reconhecida exclusivamente por (provider, provider_subject).

CREATE TABLE app_user (
    id                      UUID         NOT NULL,
    username                VARCHAR(30),
    display_name            VARCHAR(80),
    photo_url               VARCHAR(2048),
    profile_privacy         VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC',
    status                  VARCHAR(30)  NOT NULL,
    onboarding_completed_at TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    -- O @usuário é armazenado normalizado (minúsculas), logo a unicidade simples já é case-insensitive.
    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT ck_app_user_username_format
        CHECK (username IS NULL OR username ~ '^[a-z0-9][a-z0-9._]{1,28}[a-z0-9]$'),
    CONSTRAINT ck_app_user_status
        CHECK (status IN ('PENDING_ONBOARDING', 'ACTIVE', 'SUSPENDED', 'DELETED')),
    CONSTRAINT ck_app_user_profile_privacy
        CHECK (profile_privacy IN ('PUBLIC', 'PRIVATE')),
    -- Conta ativa precisa de perfil completo.
    CONSTRAINT ck_app_user_active_profile
        CHECK (status <> 'ACTIVE'
               OR (username IS NOT NULL AND display_name IS NOT NULL AND onboarding_completed_at IS NOT NULL))
);

COMMENT ON TABLE app_user IS 'Conta interna da Memora (independente do provedor de identidade).';
COMMENT ON COLUMN app_user.status IS 'PENDING_ONBOARDING até o usuário confirmar nome, foto e @usuário.';

CREATE TABLE external_identity (
    id               UUID         NOT NULL,
    user_id          UUID         NOT NULL,
    provider         VARCHAR(20)  NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    email            VARCHAR(320),
    email_verified   BOOLEAN,
    linked_at        TIMESTAMPTZ  NOT NULL,
    last_login_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_external_identity PRIMARY KEY (id),
    CONSTRAINT fk_external_identity_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_external_identity_provider CHECK (provider IN ('GOOGLE', 'APPLE', 'FACEBOOK')),
    -- Referência estável da identidade no provedor (`sub` do OpenID Connect).
    CONSTRAINT uq_external_identity_provider_subject UNIQUE (provider, provider_subject),
    -- Cada conta vincula no máximo uma identidade por provedor (também indexa user_id).
    CONSTRAINT uq_external_identity_user_provider UNIQUE (user_id, provider)
);

COMMENT ON TABLE external_identity IS 'Contas de provedores OIDC vinculadas a uma conta Memora.';
COMMENT ON COLUMN external_identity.email IS 'Informativo. Nunca usado para reconhecer ou unir contas.';
