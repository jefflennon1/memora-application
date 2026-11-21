-- V10 — Cadastro e autenticação local por @usuário e senha.

ALTER TABLE app_user
    ADD COLUMN phone VARCHAR(15),
    ADD CONSTRAINT ck_app_user_phone
        CHECK (phone IS NULL OR phone ~ '^[0-9]{10,15}$');

COMMENT ON COLUMN app_user.phone IS
    'Telefone privado normalizado (somente dígitos). Obrigatório nas contas locais.';

CREATE TABLE local_credential (
    id                  UUID         NOT NULL,
    user_id             UUID         NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    password_changed_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_local_credential PRIMARY KEY (id),
    CONSTRAINT fk_local_credential_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uq_local_credential_user UNIQUE (user_id)
);

COMMENT ON TABLE local_credential IS
    'Credencial local da conta. A senha nunca é armazenada, apenas seu hash adaptativo.';
