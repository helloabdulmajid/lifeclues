-- V10: Heal a recurrence of the ddl-auto=create drift.
--
-- At one point the ddl-auto value was flipped back to `create` and the next
-- boot rebuilt the three ownership tables from JPA entities again. That
-- shredded the canonical FKs (cascade) and CHECK constraints, replacing them
-- with non-cascade FKs carrying random names and weaker, entity-derived
-- CHECKs -- exactly the failure mode V9 had to fix.
--
-- This migration is name-agnostic: it drops every FK on the three ownership
-- tables and every CHECK on memories, then lays down the canonical set. It
-- applies cleanly no matter what Hibernate (or anyone) named the constraints,
-- and it must succeed against the existing rows (they already satisfy the
-- canonical CHECKs). ddl-auto has been re-forced to none.
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
  FOR c IN SELECT conname FROM pg_constraint
           WHERE conrelid = 'memories'::regclass AND contype = 'c'
           ORDER BY conname LOOP
    EXECUTE format('ALTER TABLE memories DROP CONSTRAINT %I', c);
  END LOOP;
END $$;

ALTER TABLE auth_tokens    ADD CONSTRAINT auth_tokens_user_id_fkey    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE refresh_tokens ADD CONSTRAINT refresh_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE memories       ADD CONSTRAINT fk_memories_user             FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE memories ADD CONSTRAINT ck_memories_status      CHECK (status IN ('DRAFT', 'COMPLETED', 'TRASHED'));
ALTER TABLE memories ADD CONSTRAINT ck_memories_trashed_to CHECK (trashed_from IN ('DRAFT', 'COMPLETED') OR trashed_from IS NULL);
ALTER TABLE memories ADD CONSTRAINT ck_memories_consistent CHECK ((status = 'TRASHED') = (deleted_at IS NOT NULL));
ALTER TABLE memories ADD CONSTRAINT ck_memories_trash_origin CHECK (status <> 'TRASHED' OR trashed_from IS NOT NULL);
ALTER TABLE memories ADD CONSTRAINT ck_memories_content     CHECK (length(content) > 0 AND length(content) <= 10000);