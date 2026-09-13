-- A temporary ddl-auto=create run replaced the Flyway-built auth_tokens table
-- with one lacking ON DELETE CASCADE. Recreate it with the intended shape so
-- deleting a user also removes their (ephemeral, single-use) tokens.
DROP TABLE IF EXISTS auth_tokens;

CREATE TABLE auth_tokens (
    id         UUID        NOT NULL PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(20) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_auth_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS idx_auth_tokens_user_id ON auth_tokens (user_id);