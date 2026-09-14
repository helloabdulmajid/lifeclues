ALTER TABLE users ADD COLUMN time_format VARCHAR(3) DEFAULT 'H12';

UPDATE users SET time_format = 'H12' WHERE time_format IS NULL;

ALTER TABLE users ALTER COLUMN time_format SET DEFAULT 'H12';
ALTER TABLE users ALTER COLUMN time_format SET NOT NULL;
