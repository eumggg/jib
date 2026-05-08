// Integration tests for /api/reports.

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

describeIfDb('integration: /api/reports', () => {
  let p: Pool;
  const stationId = randomUUID();
  const seedUid = 'reports-it-uid';

  beforeAll(async () => {
    p = new Pool({ connectionString: databaseUrl });
    await p.query(
      `INSERT INTO stations
         (id, name, location, connector_types, is_available, created_by_uid)
       VALUES (
         $1, 'Reports IT Station',
         ST_SetSRID(ST_MakePoint(-122.42, 37.75), 4326)::geography,
         ARRAY['CCS']::text[], TRUE, $2
       )`,
      [stationId, seedUid]
    );
  });

  afterAll(async () => {
    if (p) {
      await p.query('DELETE FROM station_reports WHERE station_id = $1', [stationId]);
      await p.query('DELETE FROM stations WHERE id = $1', [stationId]);
      await p.end();
    }
    await pool.end();
  });

  afterEach(async () => {
    getMockVerify().mockReset();
    await pool.query('DELETE FROM station_reports WHERE station_id = $1', [stationId]);
  });

  it('returns 401 without an Authorization header', async () => {
    const res = await request(app).post('/api/reports').send({
      idempotencyKey: randomUUID(),
      stationId,
      kind: 'broken',
    });
    expect(res.status).toBe(401);
  });

  it('creates a report and is idempotent on retry', async () => {
    getMockVerify().mockResolvedValue({ uid: 'report-user-1', email: null });
    const idempotencyKey = randomUUID();

    const first = await request(app)
      .post('/api/reports')
      .set('Authorization', 'Bearer t')
      .send({ idempotencyKey, stationId, kind: 'broken', notes: 'cable cut' });
    expect(first.status).toBe(201);
    expect(first.body.kind).toBe('broken');
    expect(first.body.notes).toBe('cable cut');
    expect(first.body.userId).toBe('report-user-1');

    const retry = await request(app)
      .post('/api/reports')
      .set('Authorization', 'Bearer t')
      .send({ idempotencyKey, stationId, kind: 'broken', notes: 'cable cut' });
    expect(retry.status).toBe(200);
    expect(retry.body.id).toBe(first.body.id);
  });

  it('rejects an unknown kind with 400', async () => {
    getMockVerify().mockResolvedValue({ uid: 'report-user-bad', email: null });
    const res = await request(app)
      .post('/api/reports')
      .set('Authorization', 'Bearer t')
      .send({ idempotencyKey: randomUUID(), stationId, kind: 'rude_attendant' });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe('validation_failed');
  });

  it('lists reports without auth, newest first', async () => {
    getMockVerify().mockResolvedValue({ uid: 'report-user-list', email: null });
    const a = await request(app)
      .post('/api/reports')
      .set('Authorization', 'Bearer t')
      .send({ idempotencyKey: randomUUID(), stationId, kind: 'closed' });
    expect(a.status).toBe(201);

    // No Authorization header on the GET; it must still return 200.
    const list = await request(app).get('/api/reports').query({ stationId });
    expect(list.status).toBe(200);
    expect(list.body.reports.length).toBeGreaterThanOrEqual(1);
    expect(list.body.reports[0].id).toBe(a.body.id);
  });
});
