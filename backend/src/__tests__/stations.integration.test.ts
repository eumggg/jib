// Integration tests for /api/stations against a real PostGIS instance.
// Skips when DATABASE_URL is not set so unit-test-only environments stay green.
//
// To run locally:
//   docker compose up -d db
//   DATABASE_URL=postgres://jib:jib_dev@localhost:5432/jib npm run migrate:up
//   DATABASE_URL=postgres://jib:jib_dev@localhost:5432/jib npm run test:integration

jest.mock('firebase-admin', () => {
  const verifyIdToken = jest.fn();
  const app = { auth: () => ({ verifyIdToken }) };
  return {
    apps: [app],
    initializeApp: jest.fn().mockReturnValue(app),
    credential: { cert: jest.fn((x: unknown) => x) },
  };
});

import request from 'supertest';
import { Pool } from 'pg';
import { randomUUID } from 'crypto';
import * as admin from 'firebase-admin';

import app from '../index';
import { pool } from '../db';

const databaseUrl = process.env.DATABASE_URL;
const describeIfDb = databaseUrl ? describe : describe.skip;

function getMockVerify() {
  return (admin.apps[0] as unknown as { auth: () => { verifyIdToken: jest.Mock } })
    .auth().verifyIdToken;
}

interface StationSeed {
  id: string;
  name: string;
  lat: number;
  lng: number;
  connectorTypes: string[];
  powerKw?: number | null;
  networkOperator?: string | null;
}

async function seedStation(p: Pool, s: StationSeed): Promise<void> {
  await p.query(
    `INSERT INTO stations
       (id, name, location, connector_types, power_kw, network_operator, is_available, created_by_uid)
     VALUES (
       $1, $2,
       ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography,
       $5, $6, $7, TRUE, 'seed-uid'
     )`,
    [s.id, s.name, s.lng, s.lat, s.connectorTypes, s.powerKw ?? null, s.networkOperator ?? null]
  );
}

describeIfDb('integration: /api/stations against real PostGIS', () => {
  let p: Pool;
  let seedIds: string[] = [];

  // Bay Area sample box: SW=(37.70,-122.50), NE=(37.81,-122.35).
  const bbox = { swLat: 37.70, swLng: -122.50, neLat: 37.81, neLng: -122.35 };

  // Three rows inside the box, three outside (opposite hemisphere + north).
  const inside: StationSeed[] = [
    { id: randomUUID(), name: 'In-A', lat: 37.7749, lng: -122.4194, connectorTypes: ['CCS'] },
    { id: randomUUID(), name: 'In-B', lat: 37.78, lng: -122.40, connectorTypes: ['CHAdeMO'], powerKw: 50 },
    { id: randomUUID(), name: 'In-C', lat: 37.71, lng: -122.49, connectorTypes: ['CCS', 'J1772'], powerKw: 250, networkOperator: 'Tesla' },
  ];
  const outside: StationSeed[] = [
    { id: randomUUID(), name: 'Out-NY', lat: 40.7128, lng: -74.0060, connectorTypes: ['CCS'] },
    { id: randomUUID(), name: 'Out-Tokyo', lat: 35.6762, lng: 139.6503, connectorTypes: ['CHAdeMO'] },
    { id: randomUUID(), name: 'Out-Far', lat: 38.5, lng: -122.4, connectorTypes: ['CCS'] }, // just north of bbox
  ];

  beforeAll(async () => {
    p = new Pool({ connectionString: databaseUrl });
    seedIds = [...inside, ...outside].map((s) => s.id);
    // Best-effort cleanup of any prior stale rows by the same ids.
    await p.query('DELETE FROM stations WHERE id = ANY($1::uuid[])', [seedIds]);
    for (const s of [...inside, ...outside]) {
      await seedStation(p, s);
    }
  });

  afterAll(async () => {
    if (p) {
      await p.query('DELETE FROM stations WHERE id = ANY($1::uuid[])', [seedIds]);
      await p.end();
    }
    // The shared pool used by the app must be closed so jest exits cleanly.
    await pool.end();
  });

  it('GIST spatial index exists on stations.location', async () => {
    const { rows } = await p.query<{ indexdef: string }>(
      `SELECT indexdef FROM pg_indexes
       WHERE schemaname = 'public'
         AND tablename = 'stations'
         AND indexname = 'stations_location_gist'`
    );
    expect(rows).toHaveLength(1);
    expect(rows[0].indexdef).toMatch(/USING gist/i);
  });

  it('bbox query returns only stations inside the box', async () => {
    const res = await request(app).get('/api/stations').query(bbox);
    expect(res.status).toBe(200);
    const ids = res.body.stations.map((s: { id: string }) => s.id);
    for (const s of inside) expect(ids).toContain(s.id);
    for (const s of outside) expect(ids).not.toContain(s.id);
  });

  it('X-Total-Count reports total before the 200-row cap', async () => {
    const res = await request(app).get('/api/stations').query(bbox);
    expect(res.status).toBe(200);
    const total = Number(res.headers['x-total-count']);
    // Includes only inside seeds plus any other in-box rows that happen to
    // exist in the dev DB; never counts the outside seeds.
    expect(total).toBeGreaterThanOrEqual(inside.length);
    expect(res.body.stations.length).toBeLessThanOrEqual(200);
    expect(res.body.stations.length).toBeLessThanOrEqual(total);
  });

  it('connectorType filter narrows to matching rows only', async () => {
    const res = await request(app)
      .get('/api/stations')
      .query({ ...bbox, connectorType: 'CHAdeMO' });
    expect(res.status).toBe(200);
    const ids = res.body.stations.map((s: { id: string }) => s.id);
    expect(ids).toContain(inside[1].id); // CHAdeMO
    expect(ids).not.toContain(inside[0].id); // CCS only
  });

  it('bbox response carries Phase 2 enrichments avgRating + recentCheckInAt', async () => {
    // Seed one review and one check-in on inside[0] so we can assert the
    // lateral-join numbers come back rounded to one decimal.
    await p.query(
      `INSERT INTO reviews (idempotency_key, station_id, user_uid, rating)
       VALUES ($1, $2, 'phase2-seed-uid', 4)`,
      [randomUUID(), inside[0].id]
    );
    await p.query(
      `INSERT INTO reviews (idempotency_key, station_id, user_uid, rating)
       VALUES ($1, $2, 'phase2-seed-uid-2', 5)`,
      [randomUUID(), inside[0].id]
    );
    await p.query(
      `INSERT INTO check_ins (idempotency_key, station_id, user_uid)
       VALUES ($1, $2, 'phase2-checkin-uid')`,
      [randomUUID(), inside[0].id]
    );

    try {
      const res = await request(app).get('/api/stations').query(bbox);
      expect(res.status).toBe(200);
      const target = res.body.stations.find(
        (s: { id: string }) => s.id === inside[0].id
      );
      expect(target).toBeDefined();
      // (4 + 5) / 2 = 4.5 — exact one-decimal expectation.
      expect(target.avgRating).toBeCloseTo(4.5, 1);
      expect(target.recentCheckInAt).toEqual(expect.any(String));

      // A station that has no reviews / no check-ins surfaces null for both.
      const empty = res.body.stations.find(
        (s: { id: string }) => s.id === inside[2].id
      );
      expect(empty).toBeDefined();
      expect(empty.avgRating).toBeNull();
      expect(empty.recentCheckInAt).toBeNull();
    } finally {
      await p.query('DELETE FROM reviews WHERE station_id = $1', [inside[0].id]);
      await p.query('DELETE FROM check_ins WHERE station_id = $1', [inside[0].id]);
    }
  });

  // Verifies the bbox query is reachable via the GIST index even on small
  // test data. enable_seqscan=off forces the planner to choose an index plan
  // when one exists; if no GIST index were defined here, the planner would
  // either fall back to seq scan (failing this assertion) or use a different
  // index (also failing — the assertion names ours).
  it('EXPLAIN ANALYZE bbox query uses stations_location_gist when seqscan is disabled', async () => {
    const explainSql = `EXPLAIN (ANALYZE, FORMAT TEXT)
      SELECT id FROM stations
      WHERE ST_Within(location::geometry, ST_MakeEnvelope($1, $2, $3, $4, 4326))`;
    // SET LOCAL only takes effect inside an explicit transaction, so wrap
    // the EXPLAIN in BEGIN/ROLLBACK. Without that the setting is silently
    // ignored and the planner picks Seq Scan on small fixtures, masking
    // whether the GIST index is actually reachable.
    const client = await p.connect();
    try {
      await client.query('BEGIN');
      await client.query('SET LOCAL enable_seqscan = OFF');
      const { rows } = await client.query<{ 'QUERY PLAN': string }>(
        explainSql,
        [bbox.swLng, bbox.swLat, bbox.neLng, bbox.neLat]
      );
      await client.query('ROLLBACK');
      const plan = rows.map((r) => r['QUERY PLAN']).join('\n');
      // Either the geography GIST (legacy) or the geometry-cast GIST added in
      // migration 005 satisfies the index-reachable invariant. The bbox
      // query casts to geometry, so in practice the geom_gist variant is the
      // one the planner picks once seqscan is disabled.
      expect(plan).toMatch(/stations_location(_geom)?_gist/);
    } finally {
      client.release();
    }
  });

  describe('GET /api/stations/:id', () => {
    it('returns the bare DTO on hit', async () => {
      const target = inside[0];
      const res = await request(app).get(`/api/stations/${target.id}`);
      expect(res.status).toBe(200);
      expect(res.body.id).toBe(target.id);
      expect(res.body.name).toBe('In-A');
      expect(res.body.latitude).toBeCloseTo(target.lat, 5);
      expect(res.body.longitude).toBeCloseTo(target.lng, 5);
      expect(res.body.stations).toBeUndefined();
    });

    it('returns 404 not_found for a missing id', async () => {
      const res = await request(app).get(`/api/stations/${randomUUID()}`);
      expect(res.status).toBe(404);
      expect(res.body).toEqual({
        error: 'not_found',
        message: expect.stringContaining('does not exist'),
      });
    });
  });

  describe('Phase 2 enrichments (avgRating, recentCheckInAt)', () => {
    const target = inside[0];

    afterAll(async () => {
      await p.query('DELETE FROM reviews WHERE station_id = $1', [target.id]);
      await p.query('DELETE FROM check_ins WHERE station_id = $1', [target.id]);
    });

    it('bbox response includes the new fields, NULL by default', async () => {
      const res = await request(app).get('/api/stations').query(bbox);
      expect(res.status).toBe(200);
      const row = res.body.stations.find((s: { id: string }) => s.id === target.id);
      expect(row).toBeDefined();
      expect(row).toHaveProperty('avgRating');
      expect(row).toHaveProperty('recentCheckInAt');
      expect(row.avgRating).toBeNull();
      expect(row.recentCheckInAt).toBeNull();
    });

    it('avgRating is the rounded mean and recentCheckInAt is the latest timestamp', async () => {
      // Seed two reviews and two check-ins from different fake users.
      await p.query(
        `INSERT INTO reviews (idempotency_key, station_id, user_uid, rating, body)
         VALUES ($1, $2, 'phase2-u1', 5, 'great'),
                ($3, $2, 'phase2-u2', 4, 'good')`,
        [randomUUID(), target.id, randomUUID()]
      );
      await p.query(
        `INSERT INTO check_ins (idempotency_key, station_id, user_uid, rating, comment, created_at)
         VALUES ($1, $2, 'phase2-u1', 4, NULL, NOW() - INTERVAL '1 hour'),
                ($3, $2, 'phase2-u2', 5, NULL, NOW())`,
        [randomUUID(), target.id, randomUUID()]
      );

      const res = await request(app).get('/api/stations').query(bbox);
      expect(res.status).toBe(200);
      const row = res.body.stations.find((s: { id: string }) => s.id === target.id);
      expect(row).toBeDefined();
      expect(typeof row.avgRating).toBe('number');
      expect(row.avgRating).toBeCloseTo(4.5, 5);
      expect(typeof row.recentCheckInAt).toBe('string');
      // The most recent check-in timestamp should parse and be within the last hour.
      const recent = new Date(row.recentCheckInAt).getTime();
      expect(Number.isFinite(recent)).toBe(true);
      expect(Date.now() - recent).toBeLessThan(5 * 60 * 1000);
    });
  });

  describe('POST /api/stations idempotency', () => {
    afterEach(() => {
      getMockVerify().mockReset();
    });

    it('returns 201 on a new id, 200 on identical re-POST, 409 on conflicting body', async () => {
      const newId = randomUUID();
      seedIds.push(newId);
      getMockVerify().mockResolvedValue({ uid: 'integration-uid', email: 'it@example.com' });

      const body = {
        id: newId,
        name: 'Integration Station',
        latitude: 37.75,
        longitude: -122.42,
        connectorTypes: ['CCS', 'J1772'],
        powerKw: 150,
        networkOperator: 'TestNet',
        isAvailable: true,
      };

      const first = await request(app)
        .post('/api/stations')
        .set('Authorization', 'Bearer t')
        .send(body);
      expect(first.status).toBe(201);
      expect(first.body.id).toBe(newId);

      const same = await request(app)
        .post('/api/stations')
        .set('Authorization', 'Bearer t')
        .send(body);
      expect(same.status).toBe(200);
      expect(same.body.id).toBe(newId);

      const conflicting = await request(app)
        .post('/api/stations')
        .set('Authorization', 'Bearer t')
        .send({ ...body, name: 'Different' });
      expect(conflicting.status).toBe(409);
      expect(conflicting.body.error).toBe('conflict');
    });
  });
});
