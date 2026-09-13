-- User accounts. Never store plain passwords — only the BCrypt hash.
CREATE TABLE IF NOT EXISTS users (
    id            UUID         NOT NULL PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    username      VARCHAR(30)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    bio           TEXT         NOT NULL DEFAULT '',
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_users_email    UNIQUE (email),
    CONSTRAINT uk_users_username UNIQUE (username)
);