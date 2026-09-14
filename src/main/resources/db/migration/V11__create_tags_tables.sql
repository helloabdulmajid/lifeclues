-- Tags (Clues) for memories. Each tag belongs to a single user and carries a
-- short label (max 50 chars). The unique index on (user_id, LOWER(name))
-- enforces case-insensitive uniqueness per user while preserving display casing.

CREATE TABLE IF NOT EXISTS tags (
    id         UUID        NOT NULL PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_tags_user_name_lower ON tags (user_id, LOWER(name));
CREATE INDEX idx_tags_user ON tags (user_id);

-- Junction table: many-to-many between memories and tags.
CREATE TABLE IF NOT EXISTS memory_tags (
    memory_id UUID NOT NULL REFERENCES memories(id) ON DELETE CASCADE,
    tag_id    UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (memory_id, tag_id)
);

CREATE INDEX idx_memory_tags_tag ON memory_tags (tag_id);
