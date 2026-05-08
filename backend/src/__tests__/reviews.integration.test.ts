// Integration tests for /api/reviews against a real PostGIS instance.
// Skips when DATABASE_URL is not set so unit-test-only environments stay green.

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

describeIfDb('integration: /api/reviews', () => {
  let p: Pool;
  const stationId = randomUUID();
  const seedUid = 'reviews-it-uid';

  beforeAll(async () => {
    p = new Pool({ connectionString: databaseUrl });
    await p.query(
      `INSERT INTO stations
         (id, name, location, connector_types, is_available, created_by_uid)
       VALUES (
         $1, 'Reviews IT Station',
         ST_SetSRID(ST_MakePoint(-122.42, 37.75), 4326)::geography,
         ARRAY['CCS']::text[], TRUE, $2
       )`,
      [stationId, seedUid]
    );
  });

  afterAll(async () => {
    if (p) {
      await p.query('DELETE FROM reviews WHERE station_id = $1', [stationId]);
      await p.query('DELETE FROM stations WHERE id = $1', [stationId]);
      await p.end();
    }
    await pool.end();
  });

  afterEach(async () => {
    getMockVerify().mockReset();
    await pool.query('DELETE FROM reviews WHERE station_id = $1', [stationId]);
  });

  it('returns 401 without an Authorization header', async () => {
    const res = await request(app).post('/api/reviews').send({
      idempotencyKey: randomUUID(),
      stationId,
      rating: 5,
    });
    expect(res.status).toBe(401);
  });

  it('creates a review on first POST and is idempotent on retry', async () => {
    getMockVerify().mockResolvedValue({ uid: 'rev-user-1', email: 'r1@example.com' });
    const idempotencyKey = randomUUID();

    const first = await request(app)
      .post('/api/reviews')
      .set('Authorization', 'Bearer t')
      .send({ idempotencyKey, stationId, rating: 4, body: 'solid' });
    expect(first.status).toBe(201);
    expect(first.body.stationId).toBe(stationId);
    expect(first.body.rating).toBe(4);
    expect(first.body.body).toBe('solid');
    expect(first.body.userId).toBe('rev-user-1');

    const retry = await request(app)
      .post('/api/reviews')
      .set('Authorization', 'Bearer t')
      .send({ idempotencyKey, stationId, rating: 4, body: 'solid' });
    expect(retry.status).toBe(200);
    expect(retry.body.id).toBe(first.body.id);
  });

  it('returns 409 when the same user reviews the same station with a new key', async () => {
    getMockVerify().mockResolvedValue({ uid: 'rev-user-conflict', email: null });

    const ok = await request(app)
      .post('/api/reviews')
      .set('Authorization', 'Bearer t')
      .send({ idempotencyKey: randomUUID(), stationId, rating: 3 });
    expect(ok.status).toBe(201);

    const dup = await request(app)
      .post('/api/reviews')
      .set('Authorization', 'Bearer t')
      .send({ idempotencyKey: randomUUID(), stationId, rating: 5 });
    expect(dup.status).toBe(409);
    expect(dup.body.error).toBe('conflict');
  });

  it('rejects invalid rating with 400', async () => {
    getMockVerify().mockResolvedValue({ uid: 'rev-user-bad', email: null });
    const res = await request(app)
      .post('/api/reviews')
      .set('Authorization', 'Bearer t')
      .send({ idempotencyKey: randomUUID(), stationId, rating: 9 });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe('validation_failed');
  });

  it('lists reviews newest first', async () => {
    getMockVerify().mockResolvedValue({ uid: 'rev-user-list', email: null });
    const a = await request(app)
      .post('/api/reviews')
      .set('Authorization', 'Bearer t')
      .send({ idempotencyKey: randomUUID(), stationId, rating: 4 });
    expect(a.status).toBe(201);

    const list = await request(app).get('/api/reviews').query({ stationId });
    expect(list.status).toBe(200);
    expect(Array.isArray(list.body.reviews)).toBe(true);
    expect(list.body.reviews.length).toBeGreaterThanOrEqual(1);
    expect(list.body.reviews[0].id).toBe(a.body.id);
  });

  it('rejects GET without a stationId query param with 400', async () => {
    const res = await request(app).get('/api/reviews');
    expect(res.status).toBe(400);
    expect(res.body.error).toBe('validation_failed');
  });
});
