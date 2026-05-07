import { Router, Request, Response } from 'express';
import { requireAuth, AuthenticatedRequest } from '../middleware/auth';
import { query } from '../db';
import { CheckIn, CreateCheckInDto } from '../models';

export const checkInsRouter = Router();

// POST /api/v1/checkins  — idempotent via client-supplied idempotencyKey UUID
checkInsRouter.post('/', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
  const { idempotencyKey, stationId, rating, comment } = req.body as CreateCheckInDto;
  if (!idempotencyKey || !stationId) {
    res.status(400).json({ error: 'idempotencyKey and stationId are required' });
    return;
  }
  try {
    const existing = await query<CheckIn>(
      'SELECT id FROM check_ins WHERE idempotency_key = $1',
      [idempotencyKey]
    );
    if (existing.length > 0) {
      const [checkIn] = await query<CheckIn>('SELECT * FROM check_ins WHERE idempotency_key = $1', [idempotencyKey]);
      res.json(checkIn);
      return;
    }
    const [checkIn] = await query<CheckIn>(
      `INSERT INTO check_ins (idempotency_key, station_id, user_uid, rating, comment)
       VALUES ($1, $2, $3, $4, $5)
       RETURNING id, station_id AS "stationId", user_uid AS "userId",
                 rating, comment, created_at AS "createdAt"`,
      [idempotencyKey, stationId, req.uid, rating ?? null, comment ?? null]
    );
    res.status(201).json(checkIn);
  } catch (err) {
    res.status(500).json({ error: 'Database error' });
  }
});

// GET /api/v1/checkins?stationId=
checkInsRouter.get('/', async (req: Request, res: Response) => {
  const { stationId } = req.query;
  if (!stationId) {
    res.status(400).json({ error: 'stationId is required' });
    return;
  }
  try {
    const checkIns = await query<CheckIn>(
      `SELECT id, station_id AS "stationId", user_uid AS "userId",
              rating, comment, created_at AS "createdAt"
       FROM check_ins WHERE station_id = $1 ORDER BY created_at DESC LIMIT 50`,
      [stationId as string]
    );
    res.json({ checkIns });
  } catch (err) {
    res.status(500).json({ error: 'Database error' });
  }
});
