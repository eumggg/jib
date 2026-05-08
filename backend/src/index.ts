import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import morgan from 'morgan';
import * as admin from 'firebase-admin';
import { router } from './routes';
import { errorHandler, notFoundHandler } from './middleware/errors';

// Eagerly initialize Firebase Admin so startup errors surface immediately.
if (admin.apps.length === 0) {
  const serviceAccountJson = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (serviceAccountJson) {
    admin.initializeApp({
      credential: admin.credential.cert(
        JSON.parse(serviceAccountJson) as admin.ServiceAccount
      ),
    });
    console.log('Firebase Admin initialized');
  } else if (process.env.FIREBASE_AUTH_EMULATOR_HOST) {
    admin.initializeApp({ projectId: process.env.GCLOUD_PROJECT ?? 'jib-demo' });
    console.log('Firebase Admin initialized (emulator mode)');
  } else if (process.env.NODE_ENV !== 'test') {
    // ADC fallback for cloud environments (e.g. Cloud Run with Workload Identity)
    admin.initializeApp();
    console.log('Firebase Admin initialized (ADC)');
  }
}

const app = express();
const PORT = process.env.PORT ?? 3000;

app.use(helmet());
app.use(cors({ origin: process.env.CORS_ORIGIN ?? '*' }));
app.use(express.json());

if (process.env.NODE_ENV !== 'test') {
  app.use(morgan('dev'));
}

// Both paths for compatibility (JIB-5 spec: GET /api/health)
app.get('/health', (_req, res) => res.json({ status: 'ok' }));
app.get('/api/health', (_req, res) => res.json({ status: 'ok' }));

app.use('/api/v1', router);

app.use(notFoundHandler);
app.use(errorHandler);

if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`JIB API listening on port ${PORT}`);
  });
}

export default app;
