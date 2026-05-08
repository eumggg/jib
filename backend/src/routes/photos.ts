import { Router, Request, Response, NextFunction } from 'express';
import { requireAuth, AuthenticatedRequest } from '../middleware/auth';
import { HttpError } from '../middleware/errors';
import { query } from '../db';
import { StationPhoto, CreatePhotoDto } from '../models';

export const photosRouter = Router();

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const PHOTO_SELECT = `
  id, station_id AS "stationId", user_uid AS "userId",
  storage_url AS "storageUrl", created_at AS "createdAt"
`;

function validateCreateBody(body: unknown): CreatePhotoDto {
  if (!body || typeof body !== 'object') {
    throw new HttpError(400, 'validation_failed', 'Request body must be a JSON object');
  }
  const b = body as Record<string, unknown>;
  if (typeof b.idempotencyKey !== 'string' || !UUID_RE.test(b.idempotencyKey)) {
    throw new HttpError(400, 'validation_failed', 'idempotencyKey must be a UUID string');
  }
  if (typeof b.stationId !== 'string' || !UUID_RE.test(b.stationId)) {
    throw new HttpError(400, 'validation_failed', 'stationId must be a UUID string');
  }
  if (typeof b.storageUrl !== 'string' || b.storageUrl.trim().length === 0) {
    throw new HttpError(400, 'validation_failed', 'storageUrl is required');
  }
  // Defense-in-depth: only accept https URLs so callers can't smuggle data: URIs.
  if (!/^https?:\/\//i.test(b.storageUrl)) {
    throw new HttpError(400, 'validation_failed', 'storageUrl must be an http(s) URL');
  }
  return {
    idempotencyKey: b.idempotencyKey,
    stationId: b.stationId,
    storageUrl: b.storageUrl,
  };
}

// POST /api/photos — auth required, idempotent via idempotencyKey.
photosRouter.post(
  '/',
  requireAuth,
  async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    try {
      const dto = validateCreateBody(req.body);
      const uid = req.user!.uid;

      const existing = await query<StationPhoto>(
        `SELECT ${PHOTO_SELECT} FROM station_photos WHERE idempotency_key = $1`,
        [dto.idempotencyKey]
      );
      if (existing.length > 0) {
        res.status(200).json(existing[0]);
        return;
      }

      const [created] = await query<StationPhoto>(
        `INSERT INTO station_photos (idempotency_key, station_id, user_uid, storage_url)
         VALUES ($1, $2, $3, $4)
         RETURNING ${PHOTO_SELECT}`,
        [dto.idempotencyKey, dto.stationId, uid, dto.storageUrl]
      );
      res.status(201).json(created);
    } catch (err) {
      next(err);
    }
  }
);

// GET /api/photos?stationId=<uuid> — newest first, max 20.
photosRouter.get('/', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { stationId } = req.query as Record<string, string | undefined>;
    if (!stationId || !UUID_RE.test(stationId)) {
      throw new HttpError(400, 'validation_failed', 'stationId query param must be a UUID');
    }
    const photos = await query<StationPhoto>(
      `SELECT ${PHOTO_SELECT} FROM station_photos
       WHERE station_id = $1
       ORDER BY created_at DESC
       LIMIT 20`,
      [stationId]
    );
    res.json({ photos });
  } catch (err) {
    next(err);
  }
});
