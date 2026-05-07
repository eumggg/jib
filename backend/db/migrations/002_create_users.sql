-- Up Migration
CREATE TABLE IF NOT EXISTS users (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uid          TEXT UNIQUE NOT NULL,
    email        TEXT,
    display_name TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS users_uid_idx ON users (uid);

-- Down Migration
DROP INDEX IF EXISTS users_uid_idx;
DROP TABLE IF EXISTS users;
