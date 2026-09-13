-- Hard guarantee that ownership-cascade FKs and the memory CHECK constraints
-- exist under canonical names, regardless of any leftover Hibernate-created
-- foreign keys (random names like fkkhs4tpy3l5krnk87ykkmafeic, no cascade).
-- A previous ddl-auto=create setting rebuilt these tables from JPA entities on
-- boot, undoing V7/V8. ddl-auto is now disabled; this migration locks the shape.
DO $$
DECLARE t TEXT; c TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['auth_tokens', 'refresh_tokens', 'memories'] LOOP
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = t::regclass AND contype = 'f'
             ORDER BY conname LOOP
      EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', t, c);
    END LOOP;
  END LOOP;
END $$;

ALTER TABLE auth_tokens    ADD CONSTRAINT auth_tokens_user_id_fkey    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE refresh_tokens ADD CONSTRAINT refresh_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE memories       ADD CONSTRAINT fk_memories_user             FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE memories DROP CONSTRAINT IF EXISTS memories_status_check;
ALTER TABLE memories DROP CONSTRAINT IF EXISTS memories_trashed_from_check;
ALTER TABLE memories DROP CONSTRAINT IF EXISTS ck_memories_status;
ALTER TABLE memories DROP CONSTRAINT IF EXISTS ck_memories_trashed_to;
ALTER TABLE memories DROP CONSTRAINT IF EXISTS ck_memories_consistent;
ALTER TABLE memories DROP CONSTRAINT IF EXISTS ck_memories_trash_origin;
ALTER TABLE memories DROP CONSTRAINT IF EXISTS ck_memories_content;
ALTER TABLE memories DROP CONSTRAINT IF EXISTS ck_memories_status_check;
ALTER TABLE memories DROP CONSTRAINT IF EXISTS ck_memories_trash;
ALTER TABLE memories DROP CONSTRAINT IF EXISTS ck_memories_content_not_null;

ALTER TABLE memories ADD CONSTRAINT ck_memories_status      CHECK (status IN ('DRAFT', 'COMPLETED', 'TRASHED'));
ALTER TABLE memories ADD CONSTRAINT ck_memories_trashed_to CHECK (trashed_from IN ('DRAFT', 'COMPLETED') OR trashed_from IS NULL);
ALTER TABLE memories ADD CONSTRAINT ck_memories_consistent CHECK ((status = 'TRASHED') = (deleted_at IS NOT NULL));
ALTER TABLE memories ADD CONSTRAINT ck_memories_trash_origin CHECK (status <> 'TRASHED' OR trashed_from IS NOT NULL);
ALTER TABLE memories ADD CONSTRAINT ck_memories_content     CHECK (length(content) > 0 AND length(content) <= 10000);