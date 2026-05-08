// Integration tests for /api/users/me and /api/users/me/activity.

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

describeIfDb('integration: /api/users', () => {
  let p: Pool;
  const uid = `users-it-${randomUUID()}`;
  const stationId = randomUUID();

  beforeAll(async () => {
    p = new Pool({ connectionString: databaseUrl });
    await p.query(
      `INSERT INTO stations
         (id, name, location, connector_types, is_available, created_by_uid)
       VALUES (
         $1, 'Users IT Station',
         ST_SetSRID(ST_MakePoint(-122.42, 37.75), 4326)::geography,
         ARRAY['CCS']::text[], TRUE, $2
       )`,
      [stationId, uid]
    );
  });

  afterAll(async () => {
    if (p) {
      await p.query('DELETE FROM reviews WHERE user_uid = $1', [uid]);
      await p.query('DELETE FROM station_photos WHERE user_uid = $1', [uid]);
      await p.query('DELETE FROM check_ins WHERE user_uid = $1', [uid]);
      await p.query('DELETE FROM stations WHERE created_by_uid = $1', [uid]);
      await p.query('DELETE FROM users WHERE uid = $1', [uid]);
      await p.end();
    }
    await pool.end();
  });

  afterEach(() => {
    getMockVerify().mockReset();
  });

  it('GET /api/users/me returns 401 without a token', async () => {
    const res = await request(app).get('/api/users/me');
    expect(res.status).toBe(401);
  });

  it('GET /api/users/me self-heals to a row on first call', async () => {
    getMockVerify().mockResolvedValue({ uid, email: 'me@example.com' });

    const res = await request(app)
      .get('/api/users/me')
      .set('Authorization', 'Bearer t');
    expect(res.status).toBe(200);
    expect(res.body.uid).toBe(uid);
    expect(res.body.email).toBe('me@example.com');
    expect(res.body.id).toEqual(expect.any(String));
    expect(res.body.createdAt).toEqual(expect.any(String));
    expect(res.body.updatedAt).toBeUndefined();
  });

  it('PATCH /api/users/me updates displayName only', async () => {
    getMockVerify().mockResolvedValue({ uid, email: 'me@example.com' });

    const res = await request(app)
      .patch('/api/users/me')
      .set('Authorization', 'Bearer t')
      .send({ displayName: '  Jay  ' });
    expect(res.status).toBe(200);
    expect(res.body.displayName).toBe('Jay');

    // Confirm round-trip via GET.
    const get = await request(app)
      .get('/api/users/me')
      .set('Authorization', 'Bearer t');
    expect(get.status).toBe(200);
    expect(get.body.displayName).toBe('Jay');
  });

  it('PATCH rejects empty displayName with 400', async () => {
    getMockVerify().mockResolvedValue({ uid, email: 'me@example.com' });
    const res = await request(app)
      .patch('/api/users/me')
      .set('Authorization', 'Bearer t')
      .send({ displayName: '   ' });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe('validation_failed');
  });

  it('GET /api/users/me/activity returns last 20 of each contribution type', async () => {
    getMockVerify().mockResolvedValue({ uid, email: 'me@example.com' });

    // Seed one row of each contribution type.
    const reviewId = randomUUID();
    await pool.query(
      `INSERT INTO reviews (id, idempotency_key, station_id, user_uid, rating)
       VALUES ($1, $2, $3, $4, 5)`,
      [reviewId, randomUUID(), stationId, uid]
    );
    const photoId = randomUUID();
    await pool.query(
      `INSERT INTO station_photos (id, idempotency_key, station_id, user_uid, storage_url)
       VALUES ($1, $2, $3, $4, 'https://example.com/x.jpg')`,
      [photoId, randomUUID(), stationId, uid]
    );
    const checkInId = randomUUID();
    await pool.query(
      `INSERT INTO check_ins (id, idempotency_key, station_id, user_uid, rating)
       VALUES ($1, $2, $3, $4, 5)`,
      [checkInId, randomUUID(), stationId, uid]
    );

    const res = await request(app)
      .get('/api/users/me/activity')
      .set('Authorization', 'Bearer t');
    expect(res.status).toBe(200);
    expect(Array.isArray(res.body.stations)).toBe(true);
    expect(Array.isArray(res.body.checkIns)).toBe(true);
    expect(Array.isArray(res.body.reviews)).toBe(true);
    expect(Array.isArray(res.body.photos)).toBe(true);

    expect(res.body.stations.some((s: { id: string }) => s.id === stationId)).toBe(true);
    expect(res.body.reviews.some((r: { id: string }) => r.id === reviewId)).toBe(true);
    expect(res.body.photos.some((ph: { id: string }) => ph.id === photoId)).toBe(true);
    expect(res.body.checkIns.some((c: { id: string }) => c.id === checkInId)).toBe(true);
  });

  it('GET /api/users/me/activity returns 401 without a token', async () => {
    const res = await request(app).get('/api/users/me/activity');
    expect(res.status).toBe(401);
  });
});
