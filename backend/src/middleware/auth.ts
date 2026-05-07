import { Request, Response, NextFunction } from 'express';
import * as admin from 'firebase-admin';

// Lazy-init Firebase Admin (avoids import-time errors when env not configured)
function getFirebaseApp(): admin.app.App {
  if (admin.apps.length === 0) {
    admin.initializeApp();
  }
  return admin.apps[0]!;
}

export interface AuthenticatedRequest extends Request {
  uid?: string;
}

// JWT-expiry-discipline lens: Firebase ID tokens expire in 1h; verify on every request.
// Never cache raw tokens on disk.
export async function requireAuth(
  req: AuthenticatedRequest,
  res: Response,
  next: NextFunction
): Promise<void> {
  const authHeader = req.headers.authorization;
  if (!authHeader?.startsWith('Bearer ')) {
    res.status(401).json({ error: 'Missing or invalid Authorization header' });
    return;
  }
  const idToken = authHeader.slice(7);
  try {
    const decoded = await getFirebaseApp().auth().verifyIdToken(idToken);
    req.uid = decoded.uid;
    next();
  } catch {
    res.status(401).json({ error: 'Invalid or expired token' });
  }
}
