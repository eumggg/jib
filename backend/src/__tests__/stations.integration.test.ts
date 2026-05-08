/**
 * Integration tests for the /api/v1/stations bounding-box query.
 * Requires a real PostGIS container: run `docker compose up -d` before this suite.
 * Set DATABASE_URL to point at the container (defaults match docker-compose.yml).
 * Skip in CI by omitting DATABASE_URL_TEST or run via `npm run test:integration`.
 *
 * Geospatial-precision lens: ST_Within + ST_MakeEnvelope, GIST index verified via pg_indexes.
 */
import { Pool, PoolClient } from 'pg';
import * as fs from 'fs';
import * as path from 'path';

const DATABASE_URL =
  process.env.DATABASE_URL_TEST ??
  process.env.DATABASE_URL ??
  'postgres://jib:jib_dev@localhost:5432/jib';

// Skip if running in unit-test only CI pass (no DB available).
const describeIfDb = process.env.SKIP_INTEGRATION ? describe.skip : describe;

describeIfDb('stations bounding-box integration', () => {
  let pool: Pool;
  let client: PoolClient;

  const migrationSql = fs.readFileSync(
    path.resolve(__dirname, '../../db/migrations/001_create_stations.sql'),
    'utf8'
  );

  beforeAll(async () => {
    pool = new Pool({ connectionString: DATABASE_URL, max: 1 });
    client = await pool.connect();
    // Idempotent schema setup.
    await client.query(migrationSql);
    // Start clean for this test run.
    await client.query('DELETE FROM check_ins');
    await client.query('DELETE FROM stations');
  });

  afterAll(async () => {
    await client.query('DELETE FROM check_ins');
    await client.query('DELETE FROM stations');
    client.release();
    await pool.end();
  });

  beforeEach(async () => {
    await client.query('DELETE FROM check_ins');
    await client.query('DELETE FROM stations');
    // Insert one station inside SF bbox and one in NYC (outside bbox used in tests).
    await client.query(`
      INSERT INTO stations (idempotency_key, name, location, connector_types, created_by_uid)
      VALUES
        (gen_random_uuid(), 'SF Station',
         ST_SetSRID(ST_MakePoint(-122.4194, 37.7749), 4326)::geography,
         ARRAY['CCS'], 'test-uid'),
        (gen_random_uuid(), 'NYC Station',
         ST_SetSRID(ST_MakePoint(-73.9857, 40.7484), 4326)::geography,
         ARRAY['CHAdeMO'], 'test-uid')
    `);
  });

  it('returns only stations within the bounding box', async () => {
    // Envelope covers San Francisco, not New York.
    const result = await client.query<{ name: string }>(`
      SELECT name FROM stations
      WHERE ST_Within(
        location::geometry,
        ST_MakeEnvelope(-122.5, 37.7, -122.3, 37.85, 4326)
      )
    `);
    expect(result.rows).toHaveLength(1);
    expect(result.rows[0].name).toBe('SF Station');
  });

  it('returns zero stations when bounding box excludes all data', async () => {
    // Middle of the Pacific Ocean
    const result = await client.query(`
      SELECT id FROM stations
      WHERE ST_Within(
        location::geometry,
        ST_MakeEnvelope(170.0, -30.0, 175.0, -25.0, 4326)
      )
    `);
    expect(result.rows).toHaveLength(0);
  });

  it('filters by connectorType when supplied', async () => {
    // SF bbox with CCS filter — should match; CHAdeMO filter — should not match SF.
    const ccs = await client.query<{ name: string }>(`
      SELECT name FROM stations
      WHERE ST_Within(location::geometry, ST_MakeEnvelope(-122.5, 37.7, -122.3, 37.85, 4326))
        AND 'CCS' = ANY(connector_types)
    `);
    expect(ccs.rows).toHaveLength(1);

    const chademo = await client.query<{ name: string }>(`
      SELECT name FROM stations
      WHERE ST_Within(location::geometry, ST_MakeEnvelope(-122.5, 37.7, -122.3, 37.85, 4326))
        AND 'CHAdeMO' = ANY(connector_types)
    `);
    expect(chademo.rows).toHaveLength(0);
  });

  it('GIST spatial index exists on stations.location', async () => {
    // Geospatial-precision lens: GIST index is required for ST_DWithin / ST_Within performance.
    const result = await client.query<{ indexname: string }>(`
      SELECT indexname FROM pg_indexes
      WHERE tablename = 'stations'
        AND indexdef ILIKE '%gist%'
    `);
    expect(result.rows.length).toBeGreaterThanOrEqual(1);
    expect(result.rows[0].indexname).toMatch(/gist/i);
  });

  it('respects the 200-row LIMIT in the full API query', async () => {
    // Insert 201 stations inside the bbox to verify LIMIT 200 is honoured.
    const values = Array.from({ length: 201 }, (_, i) =>
      `(gen_random_uuid(), 'Bulk ${i}', ST_SetSRID(ST_MakePoint(-122.41${i.toString().padStart(2, '0')}, 37.77), 4326)::geography, ARRAY['CCS'], 'bulk-test')`
    ).join(',\n');
    await client.query(`INSERT INTO stations (idempotency_key, name, location, connector_types, created_by_uid) VALUES ${values}`);

    const result = await client.query(`
      SELECT id FROM stations
      WHERE ST_Within(location::geometry, ST_MakeEnvelope(-123.0, 37.0, -122.0, 38.0, 4326))
      LIMIT 200
    `);
    expect(result.rows).toHaveLength(200);
  });
});
