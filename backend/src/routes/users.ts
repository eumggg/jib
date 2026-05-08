import { Router, Response, NextFunction } from 'express';
import { requireAuth, AuthenticatedRequest } from '../middleware/auth';
import { HttpError } from '../middleware/errors';
import { query } from '../db';
import {
  User,
  Station,
  CheckIn,
  Review,
  StationPhoto,
} from '../models';

export const usersRouter = Router();

const USER_SELECT = `
  id, uid, email, display_name AS "displayName",
  created_at AS "createdAt"
`;

const ACTIVITY_LIMIT = 20;

async function getUserOrCreate(uid: string, email: string | undefined): Promise<User> {
  // Make /users/me self-healing: if the client never called /auth/sync (or it
  // failed) we still want to return a row instead of 404. The unique uid
  // constraint guards against duplicates under concurrent first-hits.
  const [user] = await query<User>(
    `INSERT INTO users (uid, email)
     VALUES ($1, $2)
     ON CONFLICT (uid) DO UPDATE
       SET email      = COALESCE(EXCLUDED.email, users.email),
           updated_at = NOW()
     RETURNING ${USER_SELECT}, updated_at AS "updatedAt"`,
    [uid, email ?? null]
  );
  return user;
}

// GET /api/users/me — current user profile.
usersRouter.get(
  '/me',
  requireAuth,
  async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    try {
      const user = await getUserOrCreate(req.user!.uid, req.user!.email);
      res.json({
        id: user.id,
        uid: user.uid,
        email: user.email,
        displayName: user.displayName,
        createdAt: user.createdAt,
      });
    } catch (err) {
      next(err);
    }
  }
);

// PATCH /api/users/me — update displayName only.
usersRouter.patch(
  '/me',
  requireAuth,
  async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    try {
      const body = req.body as Record<string, unknown> | undefined;
      if (!body || typeof body !== 'object') {
        throw new HttpError(400, 'validation_failed', 'Request body must be a JSON object');
      }
      const { displayName } = body;
      if (typeof displayName !== 'string' || displayName.trim().length === 0) {
        throw new HttpError(400, 'validation_failed', 'displayName must be a non-empty string');
      }
      // Ensure the row exists, then patch the name in a separate statement so
      // the response shape mirrors GET /users/me.
      await getUserOrCreate(req.user!.uid, req.user!.email);
      const [user] = await query<User>(
        `UPDATE users
            SET display_name = $1,
                updated_at   = NOW()
          WHERE uid = $2
        RETURNING ${USER_SELECT}, updated_at AS "updatedAt"`,
        [displayName.trim(), req.user!.uid]
      );
      res.json({
        id: user.id,
        uid: user.uid,
        email: user.email,
        displayName: user.displayName,
        createdAt: user.createdAt,
      });
    } catch (err) {
      next(err);
    }
  }
);

const STATION_ACTIVITY_COLUMNS = `
  id, name,
  ST_Y(location::geometry) AS latitude,
  ST_X(location::geometry) AS longitude,
  connector_types AS "connectorTypes",
  CASE WHEN power_kw IS NULL THEN NULL ELSE power_kw::float8 END AS "powerKw",
  network_operator AS "networkOperator",
  is_available AS "isAvailable",
  created_at AS "createdAt"
`;

// GET /api/users/me/activity — last 20 of each contribution type, newest first.
usersRouter.get(
  '/me/activity',
  requireAuth,
  async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    try {
      const uid = req.user!.uid;
      const [stations, checkIns, reviews, photos] = await Promise.all([
        query<Station & { createdAt: string }>(
          `SELECT ${STATION_ACTIVITY_COLUMNS}
             FROM stations
            WHERE created_by_uid = $1
            ORDER BY created_at DESC
            LIMIT $2`,
          [uid, ACTIVITY_LIMIT]
        ),
        query<CheckIn>(
          `SELECT id, station_id AS "stationId", user_uid AS "userId",
                  rating, comment, created_at AS "createdAt"
             FROM check_ins
            WHERE user_uid = $1
            ORDER BY created_at DESC
            LIMIT $2`,
          [uid, ACTIVITY_LIMIT]
        ),
        query<Review>(
          `SELECT id, station_id AS "stationId", user_uid AS "userId",
                  rating, body, created_at AS "createdAt"
             FROM reviews
            WHERE user_uid = $1
            ORDER BY created_at DESC
            LIMIT $2`,
          [uid, ACTIVITY_LIMIT]
        ),
        query<StationPhoto>(
          `SELECT id, station_id AS "stationId", user_uid AS "userId",
                  storage_url AS "storageUrl", created_at AS "createdAt"
             FROM station_photos
            WHERE user_uid = $1
            ORDER BY created_at DESC
            LIMIT $2`,
          [uid, ACTIVITY_LIMIT]
        ),
      ]);
      res.json({ stations, checkIns, reviews, photos });
    } catch (err) {
      next(err);
    }
  }
);
