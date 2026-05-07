import { Pool } from 'pg';

const databaseUrl = process.env.DATABASE_URL;
const itIfDb = databaseUrl ? it : it.skip;

describe('database migrations', () => {
  let pool: Pool;

  beforeAll(() => {
    if (databaseUrl) {
      pool = new Pool({ connectionString: databaseUrl });
    }
  });

  afterAll(async () => {
    if (pool) {
      await pool.end();
    }
  });

  itIfDb('postgis extension is installed', async () => {
    const { rows } = await pool.query<{ extname: string }>(
      "SELECT extname FROM pg_extension WHERE extname = 'postgis'"
    );
    expect(rows).toHaveLength(1);
  });

  itIfDb('stations + users + check_ins tables exist', async () => {
    const { rows } = await pool.query<{ table_name: string }>(
      `SELECT table_name FROM information_schema.tables
       WHERE table_schema = 'public'
         AND table_name IN ('stations', 'users', 'check_ins')`
    );
    const names = rows.map((r) => r.table_name).sort();
    expect(names).toEqual(['check_ins', 'stations', 'users']);
  });

  itIfDb('GIST spatial index on stations.location exists', async () => {
    const { rows } = await pool.query<{ indexdef: string }>(
      `SELECT indexdef FROM pg_indexes
       WHERE schemaname = 'public'
         AND tablename = 'stations'
         AND indexname = 'stations_location_gist'`
    );
    expect(rows).toHaveLength(1);
    expect(rows[0].indexdef).toMatch(/USING gist/i);
  });

  itIfDb('migration ledger records both migrations', async () => {
    const { rows } = await pool.query<{ name: string }>(
      'SELECT name FROM pgmigrations ORDER BY id ASC'
    );
    const names = rows.map((r) => r.name);
    expect(names).toEqual(expect.arrayContaining(['001_create_stations', '002_create_users']));
  });
});
