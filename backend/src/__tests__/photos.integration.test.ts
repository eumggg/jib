// Integration tests for /api/photos.

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

describeIfDb('integration: /api/photos', () => {
  let p: Pool;
  const stationId = randomUUID();
  const seedUid = 'photos-it-uid';

  beforeAll(async () => {
    p = new Pool({ connectionString: databaseUrl });
    await p.query(
      `INSERT INTO stations
         (id, name, location, connector_types, is_available, created_by_uid)
       VALUES (
         $1, 'Photos IT Station',
         ST_SetSRID(ST_MakePoint(-122.42, 37.75), 4326)::geography,
         ARRAY['CCS']::text[], TRUE, $2
       )`,
      [stationId, seedUid]
    );
  });

  afterAll(async () => {
    if (p) {
      await p.query('DELETE FROM station_photos WHERE station_id = $1', [stationId]);
      await p.query('DELETE FROM stations WHERE id = $1', [stationId]);
      await p.end();
    }
    await pool.end();
  });

  afterEach(async () => {
    getMockVerify().mockReset();
    await pool.query('DELETE FROM station_photos WHERE station_id = $1', [stationId]);
  });

  it('returns 401 without an Authorization header', async () => {
    const res = await request(app).post('/api/photos').send({
      idempotencyKey: randomUUID(),
      stationId,
      storageUrl: 'https://example.com/p.jpg',
    });
    expect(res.status).toBe(401);
  });

  it('creates a photo and is idempotent on retry', async () => {
    getMockVerify().mockResolvedValue({ uid: 'photo-user-1', email: null });
    const idempotencyKey = randomUUID();
    const url = `https://storage.example.com/${randomUUID()}.jpg`;

    const first = await request(app)
      .post('/api/photos')
      .set('Authorization', 'Bearer t')
      .send({ idempotencyKey, stationId, storageUrl: url });
    expect(first.status).toBe(201);
    expect(first.body.storageUrl).toBe(url);
    expect(first.body.userId).toBe('photo-user-1');

    const retry = await request(app)
      .post('/api/photos')
      .set('Authorization', 'Bearer t')
      .send({ idempotencyKey, stationId, storageUrl: url });
    expect(retry.status).toBe(200);
    expect(retry.body.id).toBe(first.body.id);
  });

  it('rejects non-http(s) storageUrl with 400', async () => {
    getMockVerify().mockResolvedValue({ uid: 'photo-user-bad', email: null });
    const res = await request(app)
      .post('/api/photos')
      .set('Authorization', 'Bearer t')
      .send({
        idempotencyKey: randomUUID(),
        stationId,
        storageUrl: 'data:image/png;base64,AAAA',
      });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe('validation_failed');
  });

  it('lists photos newest first, max 20', async () => {
    getMockVerify().mockResolvedValue({ uid: 'photo-user-list', email: null });
    const created: string[] = [];
    for (let i = 0; i < 3; i += 1) {
      const r = await request(app)
        .post('/api/photos')
        .set('Authorization', 'Bearer t')
        .send({
          idempotencyKey: randomUUID(),
          stationId,
          storageUrl: `https://storage.example.com/${i}.jpg`,
        });
      expect(r.status).toBe(201);
      created.push(r.body.id);
    }

    const list = await request(app).get('/api/photos').query({ stationId });
    expect(list.status).toBe(200);
    expect(list.body.photos.length).toBeGreaterThanOrEqual(3);
    // Newest first means the last-inserted id is at index 0.
    expect(list.body.photos[0].id).toBe(created[2]);
  });
});
