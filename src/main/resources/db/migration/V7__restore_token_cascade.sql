-- Drift repair (same as V5, covering both token tables): a stray ddl-auto=create
-- run rebuilt refresh_tokens and auth_tokens via Hibernate, which added FKs
-- without ON DELETE CASCADE (constraints like "fk1lih5y2npsf8u5o3vhdb9y0os" /
-- "fkkhs4tpy3l5krnk87ykkmafeic"). Restore the intended shape so deleting a user
-- also removes their ephemeral tokens.
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS auth_tokens;

CREATE TABLE refresh_tokens (
    id         UUID        NOT NULL PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);

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