import { Router, Request, Response, NextFunction } from 'express';
import { requireAuth, AuthenticatedRequest } from '../middleware/auth';
import { HttpError } from '../middleware/errors';
import { query } from '../db';
import { StationReport, CreateReportDto, StationReportKind } from '../models';

export const reportsRouter = Router();

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const VALID_KINDS: StationReportKind[] = ['broken', 'closed', 'incorrect_info'];
const REPORT_SELECT = `
  id, station_id AS "stationId", user_uid AS "userId",
  kind, notes, created_at AS "createdAt"
`;

function validateCreateBody(body: unknown): CreateReportDto {
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
  if (typeof b.kind !== 'string' || !VALID_KINDS.includes(b.kind as StationReportKind)) {
    throw new HttpError(
      400,
      'validation_failed',
      `kind must be one of ${VALID_KINDS.join(', ')}`
    );
  }
  if (b.notes !== undefined && b.notes !== null && typeof b.notes !== 'string') {
    throw new HttpError(400, 'validation_failed', 'notes must be a string or null');
  }
  return {
    idempotencyKey: b.idempotencyKey,
    stationId: b.stationId,
    kind: b.kind as StationReportKind,
    notes: (b.notes as string | null | undefined) ?? undefined,
  };
}

// POST /api/reports — auth required, idempotent via idempotencyKey.
reportsRouter.post(
  '/',
  requireAuth,
  async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    try {
      const dto = validateCreateBody(req.body);
      const uid = req.user!.uid;

      const existing = await query<StationReport>(
        `SELECT ${REPORT_SELECT} FROM station_reports WHERE idempotency_key = $1`,
        [dto.idempotencyKey]
      );
      if (existing.length > 0) {
        res.status(200).json(existing[0]);
        return;
      }

      const [created] = await query<StationReport>(
        `INSERT INTO station_reports (idempotency_key, station_id, user_uid, kind, notes)
         VALUES ($1, $2, $3, $4, $5)
         RETURNING ${REPORT_SELECT}`,
        [dto.idempotencyKey, dto.stationId, uid, dto.kind, dto.notes ?? null]
      );
      res.status(201).json(created);
    } catch (err) {
      next(err);
    }
  }
);

// GET /api/reports?stationId=<uuid> — newest first (no auth required).
reportsRouter.get('/', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { stationId } = req.query as Record<string, string | undefined>;
    if (!stationId || !UUID_RE.test(stationId)) {
      throw new HttpError(400, 'validation_failed', 'stationId query param must be a UUID');
    }
    const reports = await query<StationReport>(
      `SELECT ${REPORT_SELECT} FROM station_reports
       WHERE station_id = $1
       ORDER BY created_at DESC
       LIMIT 50`,
      [stationId]
    );
    res.json({ reports });
  } catch (err) {
    next(err);
  }
});
