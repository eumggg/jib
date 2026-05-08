// Unit tests for /api/stations validation + auth gating. The DB module is mocked
// here so these can run without a PostGIS container — see stations.integration.test.ts
// for the bbox / GIST assertions that require a real database.

jest.mock('../db', () => ({
  query: jest.fn(),
}));

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
import * as admin from 'firebase-admin';
import { query } from '../db';
import app from '../index';

const VALID_UUID = '11111111-2222-3333-4444-555555555555';

function getMockVerify() {
  return (admin.apps[0] as unknown as { auth: () => { verifyIdToken: jest.Mock } })
    .auth().verifyIdToken;
}

const mockedQuery = query as jest.MockedFunction<typeof query>;

describe('GET /api/stations', () => {
  beforeEach(() => {
    mockedQuery.mockReset();
  });

  it('rejects requests missing bbox params with invalid_bbox', async () => {
    const res = await request(app).get('/api/stations');
    expect(res.status).toBe(400);
    expect(res.body.error).toBe('invalid_bbox');
    expect(mockedQuery).not.toHaveBeenCalled();
  });

  it('rejects non-numeric bbox params with invalid_bbox', async () => {
    const res = await request(app)
      .get('/api/stations')
      .query({ swLat: 'foo', swLng: -122.5, neLat: 37.8, neLng: -122.3 });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe('invalid_bbox');
    expect(mockedQuery).not.toHaveBeenCalled();
  });

  it('rejects swapped lat order (swLat > neLat)', async () => {
    const res = await request(app)
      .get('/api/stations')
      .query({ swLat: 38, swLng: -122.5, neLat: 37, neLng: -122.3 });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe('invalid_bbox');
  });

  it('runs count + select queries and returns X-Total-Count when bbox is valid', async () => {
    mockedQuery
      .mockResolvedValueOnce([{ total: '3' }] as never)
      .mockResolvedValueOnce([
        {
          id: VALID_UUID,
          name: 'Stub',
          latitude: 37.7,
          longitude: -122.4,
          connectorTypes: ['CCS'],
          powerKw: 150,
          networkOperator: null,
          isAvailable: true,
        },
      ] as never);

    const res = await request(app)
      .get('/api/stations')
      .query({ swLat: 37.7, swLng: -122.5, neLat: 37.8, neLng: -122.3 });

    expect(res.status).toBe(200);
    expect(res.headers['x-total-count']).toBe('3');
    expect(res.body).toEqual({
      stations: [
        {
          id: VALID_UUID,
          name: 'Stub',
          latitude: 37.7,
          longitude: -122.4,
          connectorTypes: ['CCS'],
          powerKw: 150,
          networkOperator: null,
          isAvailable: true,
        },
      ],
    });
    expect(mockedQuery).toHaveBeenCalledTimes(2);
    const countSql = mockedQuery.mock.calls[0][0] as string;
    const selectSql = mockedQuery.mock.calls[1][0] as string;
    expect(countSql).toMatch(/COUNT\(\*\)/);
    expect(selectSql).toMatch(/ST_MakeEnvelope/);
    expect(selectSql).toMatch(/ST_Within/);
    expect(selectSql).toMatch(/LIMIT 200/);
  });

  it('appends connectorType filter to bbox query when supplied', async () => {
    mockedQuery
      .mockResolvedValueOnce([{ total: '0' }] as never)
      .mockResolvedValueOnce([] as never);

    const res = await request(app)
      .get('/api/stations')
      .query({ swLat: 37, swLng: -123, neLat: 38, neLng: -122, connectorType: 'CCS' });

    expect(res.status).toBe(200);
    const params = mockedQuery.mock.calls[1][1] as unknown[];
    expect(params).toContain('CCS');
    const selectSql = mockedQuery.mock.calls[1][0] as string;
    expect(selectSql).toMatch(/ANY\(s\.connector_types\)/);
  });

  it('joins lateral subqueries for avgRating + recentCheckInAt', async () => {
    mockedQuery
      .mockResolvedValueOnce([{ total: '0' }] as never)
      .mockResolvedValueOnce([] as never);

    await request(app)
      .get('/api/stations')
      .query({ swLat: 37, swLng: -123, neLat: 38, neLng: -122 });

    const selectSql = mockedQuery.mock.calls[1][0] as string;
    expect(selectSql).toMatch(/LEFT JOIN LATERAL/);
    expect(selectSql).toMatch(/AVG\(rating\)/);
    expect(selectSql).toMatch(/MAX\(created_at\)/);
    expect(selectSql).toMatch(/"avgRating"/);
    expect(selectSql).toMatch(/"recentCheckInAt"/);
  });
});

describe('GET /api/stations/:id', () => {
  beforeEach(() => {
    mockedQuery.mockReset();
  });

  it('returns 404 not_found for malformed UUIDs', async () => {
    const res = await request(app).get('/api/stations/not-a-uuid');
    expect(res.status).toBe(404);
    expect(res.body.error).toBe('not_found');
    expect(mockedQuery).not.toHaveBeenCalled();
  });

  it('returns 404 not_found when no row matches', async () => {
    mockedQuery.mockResolvedValueOnce([] as never);
    const res = await request(app).get(`/api/stations/${VALID_UUID}`);
    expect(res.status).toBe(404);
    expect(res.body).toEqual({
      error: 'not_found',
      message: `Station ${VALID_UUID} does not exist`,
    });
  });

  it('returns the bare DTO (not wrapped) on hit', async () => {
    mockedQuery.mockResolvedValueOnce([
      {
        id: VALID_UUID,
        name: 'Foo',
        latitude: 37.7,
        longitude: -122.4,
        connectorTypes: ['CCS'],
        powerKw: 150,
        networkOperator: 'Tesla',
        isAvailable: true,
      },
    ] as never);
    const res = await request(app).get(`/api/stations/${VALID_UUID}`);
    expect(res.status).toBe(200);
    expect(res.body.id).toBe(VALID_UUID);
    expect(res.body.stations).toBeUndefined();
  });
});

describe('POST /api/stations', () => {
  const validBody = {
    id: VALID_UUID,
    name: 'Foo Supercharger',
    latitude: 37.7749,
    longitude: -122.4194,
    connectorTypes: ['CCS', 'CHAdeMO'],
    powerKw: 150,
    networkOperator: 'Tesla',
    isAvailable: true,
  };

  beforeEach(() => {
    mockedQuery.mockReset();
    getMockVerify().mockReset();
  });

  it('returns 401 unauthorized without a Bearer token', async () => {
    const res = await request(app).post('/api/stations').send(validBody);
    expect(res.status).toBe(401);
    expect(res.body.error).toBe('unauthorized');
    expect(mockedQuery).not.toHaveBeenCalled();
  });

  it('returns 400 validation_failed for missing id', async () => {
    getMockVerify().mockResolvedValueOnce({ uid: 'u1', email: 'u1@example.com' });
    const bodyMinusId: Record<string, unknown> = { ...validBody };
    delete bodyMinusId.id;
    const res = await request(app)
      .post('/api/stations')
      .set('Authorization', 'Bearer good')
      .send(bodyMinusId);
    expect(res.status).toBe(400);
    expect(res.body.error).toBe('validation_failed');
  });

  it('returns 400 validation_failed for non-UUID id', async () => {
    getMockVerify().mockResolvedValueOnce({ uid: 'u1', email: 'u1@example.com' });
    const res = await request(app)
      .post('/api/stations')
      .set('Authorization', 'Bearer good')
      .send({ ...validBody, id: 'not-a-uuid' });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe('validation_failed');
  });

  it('returns 400 validation_failed for out-of-range latitude', async () => {
    getMockVerify().mockResolvedValueOnce({ uid: 'u1', email: 'u1@example.com' });
    const res = await request(app)
      .post('/api/stations')
      .set('Authorization', 'Bearer good')
      .send({ ...validBody, latitude: 999 });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe('validation_failed');
  });

  it('returns 201 with created row for a new id', async () => {
    getMockVerify().mockResolvedValueOnce({ uid: 'u1', email: 'u1@example.com' });
    mockedQuery
      .mockResolvedValueOnce([] as never) // existing lookup
      .mockResolvedValueOnce([{ ...validBody }] as never); // insert RETURNING

    const res = await request(app)
      .post('/api/stations')
      .set('Authorization', 'Bearer good')
      .send(validBody);
    expect(res.status).toBe(201);
    expect(res.body.id).toBe(VALID_UUID);
  });

  it('returns 200 with existing row when re-POSTed with identical body', async () => {
    getMockVerify().mockResolvedValueOnce({ uid: 'u1', email: 'u1@example.com' });
    mockedQuery.mockResolvedValueOnce([
      { ...validBody, created_by_uid: 'u1' },
    ] as never);

    const res = await request(app)
      .post('/api/stations')
      .set('Authorization', 'Bearer good')
      .send(validBody);
    expect(res.status).toBe(200);
    expect(res.body.id).toBe(VALID_UUID);
    // Internal column must not leak.
    expect(res.body.created_by_uid).toBeUndefined();
  });

  it('returns 409 conflict when re-POSTed with conflicting body', async () => {
    getMockVerify().mockResolvedValueOnce({ uid: 'u1', email: 'u1@example.com' });
    mockedQuery.mockResolvedValueOnce([
      { ...validBody, name: 'Different Name', created_by_uid: 'u1' },
    ] as never);

    const res = await request(app)
      .post('/api/stations')
      .set('Authorization', 'Bearer good')
      .send(validBody);
    expect(res.status).toBe(409);
    expect(res.body.error).toBe('conflict');
  });
});
