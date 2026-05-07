import { Response, NextFunction } from 'express';

// Mock firebase-admin before importing the middleware under test.
// The factory runs at hoist-time, so we embed the mock fn reference inside the module.
jest.mock('firebase-admin', () => {
  const verifyIdToken = jest.fn();
  const app = { auth: () => ({ verifyIdToken }) };
  return {
    apps: [app],           // pre-populate so getFirebaseApp() skips initializeApp
    initializeApp: jest.fn().mockReturnValue(app),
    credential: { cert: jest.fn((x: unknown) => x) },
  };
});

// Import AFTER the mock is registered
import * as admin from 'firebase-admin';
import { requireAuth, AuthenticatedRequest } from '../middleware/auth';

// Helper to reach the mocked verifyIdToken
function getMockVerify() {
  return (admin.apps[0] as unknown as { auth: () => { verifyIdToken: jest.Mock } })
    .auth().verifyIdToken;
}

function makeReq(authHeader?: string): AuthenticatedRequest {
  return { headers: { authorization: authHeader } } as unknown as AuthenticatedRequest;
}

function makeRes() {
  const res = {
    status: jest.fn().mockReturnThis(),
    json: jest.fn().mockReturnThis(),
  } as unknown as Response;
  return res;
}

describe('requireAuth middleware', () => {
  let next: NextFunction;

  beforeEach(() => {
    next = jest.fn();
    getMockVerify().mockReset();
  });

  it('returns 401 when Authorization header is missing', async () => {
    const res = makeRes();
    await requireAuth(makeReq(undefined), res, next);
    expect(res.status).toHaveBeenCalledWith(401);
    expect(next).not.toHaveBeenCalled();
  });

  it('returns 401 when Authorization header is not a Bearer token', async () => {
    const res = makeRes();
    await requireAuth(makeReq('Basic abc123'), res, next);
    expect(res.status).toHaveBeenCalledWith(401);
    expect(next).not.toHaveBeenCalled();
  });

  it('returns 401 when Firebase rejects the token', async () => {
    getMockVerify().mockRejectedValueOnce(new Error('token-expired'));
    const res = makeRes();
    await requireAuth(makeReq('Bearer bad-token'), res, next);
    expect(res.status).toHaveBeenCalledWith(401);
    expect(next).not.toHaveBeenCalled();
  });

  it('attaches req.user and calls next() on a valid token', async () => {
    getMockVerify().mockResolvedValueOnce({ uid: 'uid-123', email: 'user@example.com' });
    const req = makeReq('Bearer valid-token');
    const res = makeRes();
    await requireAuth(req, res, next);
    expect(next).toHaveBeenCalled();
    expect(req.user).toEqual({ uid: 'uid-123', email: 'user@example.com' });
    expect(res.status).not.toHaveBeenCalled();
  });

  it('sets email to undefined when token has no email claim', async () => {
    getMockVerify().mockResolvedValueOnce({ uid: 'uid-456', email: undefined });
    const req = makeReq('Bearer anon-token');
    const res = makeRes();
    await requireAuth(req, res, next);
    expect(next).toHaveBeenCalled();
    expect(req.user).toEqual({ uid: 'uid-456', email: undefined });
  });
});
