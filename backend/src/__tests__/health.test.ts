import request from 'supertest';
import app from '../index';

describe('health endpoints', () => {
  it('GET /health returns 200 ok', async () => {
    const res = await request(app).get('/health');
    expect(res.status).toBe(200);
    expect(res.body.status).toBe('ok');
  });

  it('GET /api/health returns 200 ok', async () => {
    const res = await request(app).get('/api/health');
    expect(res.status).toBe(200);
    expect(res.body.status).toBe('ok');
  });

  it('GET /api/health responds in well under 50ms', async () => {
    // Warm-up round-trip first to avoid first-request module-init noise.
    await request(app).get('/api/health');
    const t0 = process.hrtime.bigint();
    const res = await request(app).get('/api/health');
    const elapsedMs = Number(process.hrtime.bigint() - t0) / 1_000_000;
    expect(res.status).toBe(200);
    expect(elapsedMs).toBeLessThan(50);
  });
});
