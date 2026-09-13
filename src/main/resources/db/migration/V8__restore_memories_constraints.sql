-- Drift repair (continuation of V7): a stray ddl-auto run also rebuilt the
-- memories table via Hibernate, replacing the V6 FK (no ON DELETE CASCADE) and
-- dropping its semantic CHECK constraints. Align it back to V6's intended shape.
ALTER TABLE memories DROP CONSTRAINT IF EXISTS fkia77spbkhm2x332hxi2bfj1md;
ALTER TABLE memories DROP CONSTRAINT IF EXISTS memories_status_check;
ALTER TABLE memories DROP CONSTRAINT IF EXISTS memories_trashed_from_check;

ALTER TABLE memories ADD CONSTRAINT fk_memories_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE memories ADD CONSTRAINT ck_memories_status     CHECK (status IN ('DRAFT', 'COMPLETED', 'TRASHED'));
ALTER TABLE memories ADD CONSTRAINT ck_memories_trashed_to CHECK (trashed_from IN ('DRAFT', 'COMPLETED') OR trashed_from IS NULL);
ALTER TABLE memories ADD CONSTRAINT ck_memories_consistent CHECK ((status = 'TRASHED') = (deleted_at IS NOT NULL));
ALTER TABLE memories ADD CONSTRAINT ck_memories_trash_origin CHECK (status <> 'TRASHED' OR trashed_from IS NOT NULL);
ALTER TABLE memories ADD CONSTRAINT ck_memories_content    CHECK (length(content) > 0 AND length(content) <= 10000);