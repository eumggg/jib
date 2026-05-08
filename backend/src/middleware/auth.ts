import { Request, Response, NextFunction } from 'express';
import * as admin from 'firebase-admin';

function getFirebaseApp(): admin.app.App {
  if (admin.apps.length > 0) {
    return admin.apps[0]!;
  }
  const serviceAccountJson = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (serviceAccountJson) {
    return admin.initializeApp({
      credential: admin.credential.cert(
        JSON.parse(serviceAccountJson) as admin.ServiceAccount
      ),
    });
  }
  // Falls back to GOOGLE_APPLICATION_CREDENTIALS / ADC in CI/cloud environments
  return admin.initializeApp();
}

export interface AuthUser {
  uid: string;
  email: string | undefined;
}

export interface AuthenticatedRequest extends Request {
  user?: AuthUser;
}

// JWT-expiry-discipline lens: Firebase ID tokens expire in 1h; verify on every request.
// Never cache raw tokens on disk. Force-refresh happens only on 401, not here.
export async function requireAuth(
  req: AuthenticatedRequest,
  res: Response,
  next: NextFunction
): Promise<void> {
  const authHeader = req.headers.authorization;
  if (!authHeader?.startsWith('Bearer ')) {
    res.status(401).json({ error: 'unauthorized', message: 'Missing or invalid Authorization header' });
    return;
  }
  const idToken = authHeader.slice(7);
  try {
    const decoded = await getFirebaseApp().auth().verifyIdToken(idToken);
    req.user = { uid: decoded.uid, email: decoded.email };
    next();
  } catch {
    res.status(401).json({ error: 'unauthorized', message: 'Invalid or expired token' });
  }
}
