-- Users table: stores Firebase Auth users synced on first login.
-- PostGIS-schema-evolution lens: additive migration, nullable columns for safe rollout.
CREATE TABLE IF NOT EXISTS users (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uid          TEXT UNIQUE NOT NULL,      -- Firebase UID
    email        TEXT,
    display_name TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS users_uid_idx ON users (uid);
