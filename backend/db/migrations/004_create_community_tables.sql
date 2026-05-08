-- Up Migration
-- Phase 2 community tables: reviews, station_photos, station_reports.
-- All three carry a client-supplied idempotency_key so retried POSTs are safe,
-- and cascade on station delete so removing a station cleans up its community
-- rows.

CREATE TABLE IF NOT EXISTS reviews (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key UUID UNIQUE NOT NULL,
    station_id      UUID NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
    user_uid        TEXT NOT NULL,
    rating          SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    body            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT reviews_station_user_unique UNIQUE (station_id, user_uid)
);

CREATE INDEX IF NOT EXISTS reviews_station_id_idx ON reviews (station_id);

CREATE TABLE IF NOT EXISTS station_photos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key UUID UNIQUE NOT NULL,
    station_id      UUID NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
    user_uid        TEXT NOT NULL,
    storage_url     TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS station_photos_station_id_idx ON station_photos (station_id);

CREATE TABLE IF NOT EXISTS station_reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key UUID UNIQUE NOT NULL,
    station_id      UUID NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
    user_uid        TEXT NOT NULL,
    kind            TEXT NOT NULL CHECK (kind IN ('broken', 'closed', 'incorrect_info')),
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS station_reports_station_id_idx ON station_reports (station_id);

-- Down Migration
DROP INDEX IF EXISTS station_reports_station_id_idx;
DROP TABLE IF EXISTS station_reports;
DROP INDEX IF EXISTS station_photos_station_id_idx;
DROP TABLE IF EXISTS station_photos;
DROP INDEX IF EXISTS reviews_station_id_idx;
DROP TABLE IF EXISTS reviews;
