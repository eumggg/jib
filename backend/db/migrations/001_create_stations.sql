-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;

-- Stations table with geospatial column
-- PostGIS-schema-evolution lens: additive migrations, nullable cols for safe rollout
CREATE TABLE IF NOT EXISTS stations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key UUID UNIQUE NOT NULL,
    name            TEXT NOT NULL,
    location        GEOGRAPHY(Point, 4326) NOT NULL,
    connector_types TEXT[] NOT NULL DEFAULT '{}',
    power_kw        NUMERIC(6, 2),
    network_operator TEXT,
    is_available    BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_uid  TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- GIST spatial index for ST_DWithin radius queries
CREATE INDEX IF NOT EXISTS stations_location_gist ON stations USING GIST (location);

-- Check-ins table
CREATE TABLE IF NOT EXISTS check_ins (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key UUID UNIQUE NOT NULL,
    station_id      UUID NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
    user_uid        TEXT NOT NULL,
    rating          SMALLINT CHECK (rating BETWEEN 1 AND 5),
    comment         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS check_ins_station_id_idx ON check_ins (station_id);
