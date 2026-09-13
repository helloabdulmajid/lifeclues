-- Memories: the core of LifeClues. A memory belongs to exactly one user and
-- carries a required event date plus optional time. Records follow a simple
-- status lifecycle: DRAFT -> COMPLETED -> TRASHED. Trash keeps the memory for
-- a retention window (deleted_at anchor) before a scheduled sweep removes it.

CREATE TABLE IF NOT EXISTS memories (
    id            UUID         NOT NULL PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title         VARCHAR(120),
    content       TEXT         NOT NULL,
    event_date    DATE         NOT NULL,
    event_time    TIME,
    status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    trashed_from  VARCHAR(20),
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_memories_status     CHECK (status IN ('DRAFT', 'COMPLETED', 'TRASHED')),
    CONSTRAINT ck_memories_trashed_to CHECK (trashed_from IN ('DRAFT', 'COMPLETED') OR trashed_from IS NULL),
    CONSTRAINT ck_memories_consistent CHECK ((status = 'TRASHED') = (deleted_at IS NOT NULL)),
    CONSTRAINT ck_memories_trash_origin CHECK (status <> 'TRASHED' OR trashed_from IS NOT NULL),
    CONSTRAINT ck_memories_content    CHECK (length(content) > 0 AND length(content) <= 10000)
);

CREATE INDEX IF NOT EXISTS idx_memories_user_event  ON memories (user_id, event_date DESC, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_memories_user_status ON memories (user_id, status);
CREATE INDEX IF NOT EXISTS idx_memories_sweep       ON memories (deleted_at);