import express from 'express';
import request from 'supertest';
import { errorHandler, notFoundHandler, HttpError } from '../middleware/errors';

function buildApp(): express.Express {
  const app = express();
  app.use(express.json());

  app.get('/throw-http', () => {
    throw new HttpError(403, 'forbidden', 'Not allowed');
  });

  app.get('/throw-generic', () => {
    throw new Error('boom');
  });

  app.use(notFoundHandler);
  app.use(errorHandler);
  return app;
}

describe('error middleware', () => {
  it('returns structured 404 for unknown routes', async () => {
    const res = await request(buildApp()).get('/no-such-route');
    expect(res.status).toBe(404);
    expect(res.body).toEqual({
      error: 'not_found',
      message: expect.stringContaining('/no-such-route'),
    });
  });

  it('serializes HttpError with code and message', async () => {
    const res = await request(buildApp()).get('/throw-http');
    expect(res.status).toBe(403);
    expect(res.body).toEqual({
      error: 'forbidden',
      message: 'Not allowed',
    });
  });

  it('returns generic 500 envelope for unexpected errors', async () => {
    const res = await request(buildApp()).get('/throw-generic');
    expect(res.status).toBe(500);
    expect(res.body).toEqual({
      error: 'internal_error',
      message: 'Internal Server Error',
    });
  });
});
