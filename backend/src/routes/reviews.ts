import { Router, Request, Response, NextFunction } from 'express';
import { requireAuth, AuthenticatedRequest } from '../middleware/auth';
import { HttpError } from '../middleware/errors';
import { query } from '../db';
import { Review, CreateReviewDto } from '../models';

export const reviewsRouter = Router();

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const REVIEW_SELECT = `
  id, station_id AS "stationId", user_uid AS "userId",
  rating, body, created_at AS "createdAt"
`;

function validateCreateBody(body: unknown): CreateReviewDto {
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
  if (typeof b.rating !== 'number' || !Number.isInteger(b.rating) || b.rating < 1 || b.rating > 5) {
    throw new HttpError(400, 'validation_failed', 'rating must be an integer in [1, 5]');
  }
  if (b.body !== undefined && b.body !== null && typeof b.body !== 'string') {
    throw new HttpError(400, 'validation_failed', 'body must be a string or null');
  }
  return {
    idempotencyKey: b.idempotencyKey,
    stationId: b.stationId,
    rating: b.rating,
    body: (b.body as string | null | undefined) ?? undefined,
  };
}

// POST /api/reviews — auth required, idempotent via idempotencyKey.
//   - same idempotencyKey → 200 with existing row
//   - new key but station already reviewed by this user → 200 with existing row
//   - new key + new (station, user) pair → 201 with created row
reviewsRouter.post(
  '/',
  requireAuth,
  async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    try {
      const dto = validateCreateBody(req.body);
      const uid = req.user!.uid;

      const existingByKey = await query<Review>(
        `SELECT ${REVIEW_SELECT} FROM reviews WHERE idempotency_key = $1`,
        [dto.idempotencyKey]
      );
      if (existingByKey.length > 0) {
        res.status(200).json(existingByKey[0]);
        return;
      }

      const existingByUser = await query<Review>(
        `SELECT ${REVIEW_SELECT} FROM reviews
         WHERE station_id = $1 AND user_uid = $2`,
        [dto.stationId, uid]
      );
      if (existingByUser.length > 0) {
        throw new HttpError(
          409,
          'conflict',
          'You have already reviewed this station'
        );
      }

      const [created] = await query<Review>(
        `INSERT INTO reviews (idempotency_key, station_id, user_uid, rating, body)
         VALUES ($1, $2, $3, $4, $5)
         RETURNING ${REVIEW_SELECT}`,
        [dto.idempotencyKey, dto.stationId, uid, dto.rating, dto.body ?? null]
      );
      res.status(201).json(created);
    } catch (err) {
      next(err);
    }
  }
);

// GET /api/reviews?stationId=<uuid> — newest first, max 50.
reviewsRouter.get('/', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { stationId } = req.query as Record<string, string | undefined>;
    if (!stationId || !UUID_RE.test(stationId)) {
      throw new HttpError(400, 'validation_failed', 'stationId query param must be a UUID');
    }
    const reviews = await query<Review>(
      `SELECT ${REVIEW_SELECT} FROM reviews
       WHERE station_id = $1
       ORDER BY created_at DESC
       LIMIT 50`,
      [stationId]
    );
    res.json({ reviews });
  } catch (err) {
    next(err);
  }
});
