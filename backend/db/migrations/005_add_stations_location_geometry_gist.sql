-- Up Migration
-- The bbox GET query (`ST_Within(location::geometry, ST_MakeEnvelope(...))`)
-- can't use the geography GIST index from migration 001 because the cast
-- to geometry produces a non-leakproof expression on the indexed column.
-- Add an expression GIST index on `(location::geometry)` so the bbox query
-- is index-backed in production and the EXPLAIN ANALYZE assertion in
-- stations.integration.test.ts has a usable plan to find.
CREATE INDEX IF NOT EXISTS stations_location_geom_gist
  ON stations
  USING gist ((location::geometry));

-- Down Migration
DROP INDEX IF EXISTS stations_location_geom_gist;
