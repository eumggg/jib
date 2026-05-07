import { Pool } from 'pg';

// Geospatial-precision lens: PostGIS ST_DWithin queries require GIST spatial index.
// See migrations/001_create_stations.sql for schema.
export const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  max: 10,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
});

export async function query<T = unknown>(
  text: string,
  params?: unknown[]
): Promise<T[]> {
  const result = await pool.query(text, params);
  return result.rows as T[];
}
