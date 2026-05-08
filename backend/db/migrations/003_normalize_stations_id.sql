-- Up Migration
-- Per the JIB-7 / JIB-14 wire contract, the client supplies a UUID `id` on
-- POST /api/stations as its own idempotency key. Drop the redundant
-- `idempotency_key` column and require clients to provide `id` explicitly.
-- Safe: stations table has no production data at the time of this migration.

ALTER TABLE stations DROP COLUMN IF EXISTS idempotency_key;
ALTER TABLE stations ALTER COLUMN id DROP DEFAULT;

-- Down Migration
ALTER TABLE stations ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE stations ADD COLUMN IF NOT EXISTS idempotency_key UUID;
ALTER TABLE stations ADD CONSTRAINT stations_idempotency_key_key UNIQUE (idempotency_key);
