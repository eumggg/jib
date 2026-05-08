import { Router, Request, Response, NextFunction } from 'express';
import { requireAuth, AuthenticatedRequest } from '../middleware/auth';
import { HttpError } from '../middleware/errors';
import { query } from '../db';
import { Station, CreateStationDto } from '../models';

export const stationsRouter = Router();

const MAX_RESULTS = 200;
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const STATION_SELECT_COLUMNS = `
  id, name,
  ST_Y(location::geometry) AS latitude,
  ST_X(location::geometry) AS longitude,
  connector_types AS "connectorTypes",
  CASE WHEN power_kw IS NULL THEN NULL ELSE power_kw::float8 END AS "powerKw",
  network_operator AS "networkOperator",
  is_available AS "isAvailable"
`;

// Same as STATION_SELECT_COLUMNS but joined with lateral subqueries for the
// Phase 2 enrichments (avgRating, recentCheckInAt). Kept separate so the bare
// GET /:id and POST handlers continue to return the canonical shape.
const STATION_SELECT_COLUMNS_WITH_PHASE2 = `
  s.id, s.name,
  ST_Y(s.location::geometry) AS latitude,
  ST_X(s.location::geometry) AS longitude,
  s.connector_types AS "connectorTypes",
  CASE WHEN s.power_kw IS NULL THEN NULL ELSE s.power_kw::float8 END AS "powerKw",
  s.network_operator AS "networkOperator",
  s.is_available AS "isAvailable",
  CASE WHEN r.avg_rating IS NULL THEN NULL ELSE r.avg_rating::float8 END AS "avgRating",
  c.recent_check_in_at AS "recentCheckInAt"
`;

function parseBbox(req: Request): {
  swLat: number; swLng: number; neLat: number; neLng: number;
} {
  const { swLat, swLng, neLat, neLng } = req.query as Record<string, string | undefined>;
  const required = { swLat, swLng, neLat, neLng };
  for (const [key, val] of Object.entries(required)) {
    if (val === undefined || val === '') {
      throw new HttpError(400, 'invalid_bbox', `Missing required query param: ${key}`);
    }
  }
  const parsed = {
    swLat: Number(required.swLat),
    swLng: Number(required.swLng),
    neLat: Number(required.neLat),
    neLng: Number(required.neLng),
  };
  for (const [key, val] of Object.entries(parsed)) {
    if (!Number.isFinite(val)) {
      throw new HttpError(400, 'invalid_bbox', `Query param ${key} is not a finite number`);
    }
  }
  if (parsed.swLat < -90 || parsed.swLat > 90 || parsed.neLat < -90 || parsed.neLat > 90) {
    throw new HttpError(400, 'invalid_bbox', 'Latitudes must be within [-90, 90]');
  }
  if (parsed.swLng < -180 || parsed.swLng > 180 || parsed.neLng < -180 || parsed.neLng > 180) {
    throw new HttpError(400, 'invalid_bbox', 'Longitudes must be within [-180, 180]');
  }
  if (parsed.swLat > parsed.neLat) {
    throw new HttpError(400, 'invalid_bbox', 'swLat must be <= neLat');
  }
  // swLng > neLng is technically valid for bboxes that cross the antimeridian.
  // ST_MakeEnvelope handles that case, so we don't reject it here.
  return parsed;
}

// GET /api/stations?swLat=&swLng=&neLat=&neLng=[&connectorType=CCS]
// Map-viewport-batching lens: bounding box, max 200 results, GIST index required.
// X-Total-Count reports total matching rows BEFORE the 200-row cap so
// callers (JIB-9 pagination) can detect truncation.
stationsRouter.get('/', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { swLat, swLng, neLat, neLng } = parseBbox(req);
    const { connectorType } = req.query as Record<string, string | undefined>;

    const params: unknown[] = [swLng, swLat, neLng, neLat];
    let where = `
      ST_Within(
        s.location::geometry,
        ST_MakeEnvelope($1, $2, $3, $4, 4326)
      )
    `;
    if (connectorType) {
      params.push(connectorType);
      where += ` AND $${params.length} = ANY(s.connector_types)`;
    }

    const countRows = await query<{ total: string }>(
      `SELECT COUNT(*)::text AS total FROM stations s WHERE ${where}`,
      params
    );
    const total = Number(countRows[0]?.total ?? '0');

    // Lateral subqueries avoid N+1: each station gets its avg rating and most
    // recent check-in timestamp in a single round trip. Both lateral results
    // return zero rows when the station has no community data, which the LEFT
    // JOINs collapse to NULL — surfaced as JSON null on the wire.
    const stations = await query<Station>(
      `SELECT ${STATION_SELECT_COLUMNS_WITH_PHASE2}
       FROM stations s
       LEFT JOIN LATERAL (
         SELECT ROUND(AVG(rating)::numeric, 1) AS avg_rating
         FROM reviews
         WHERE station_id = s.id
       ) r ON TRUE
       LEFT JOIN LATERAL (
         SELECT MAX(created_at) AS recent_check_in_at
         FROM check_ins
         WHERE station_id = s.id
       ) c ON TRUE
       WHERE ${where}
       ORDER BY s.id
       LIMIT ${MAX_RESULTS}`,
      params
    );

    res.setHeader('X-Total-Count', String(total));
    res.json({ stations });
  } catch (err) {
    next(err);
  }
});

// GET /api/stations/:id — bare StationDto (not wrapped in {stations: ...}).
stationsRouter.get('/:id', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { id } = req.params;
    if (!UUID_RE.test(id)) {
      throw new HttpError(404, 'not_found', `Station ${id} does not exist`);
    }
    const [station] = await query<Station>(
      `SELECT ${STATION_SELECT_COLUMNS} FROM stations WHERE id = $1`,
      [id]
    );
    if (!station) {
      throw new HttpError(404, 'not_found', `Station ${id} does not exist`);
    }
    res.json(station);
  } catch (err) {
    next(err);
  }
});

interface ExistingStationRow extends Station {
  created_by_uid: string;
}

function bodiesMatch(a: ExistingStationRow, b: CreateStationDto): boolean {
  // Bytewise comparison on the persisted shape: same UUID may be re-POSTed but
  // diverging coordinates / metadata is a real conflict.
  if (a.name !== b.name) return false;
  if (Math.abs(a.latitude - b.latitude) > 1e-7) return false;
  if (Math.abs(a.longitude - b.longitude) > 1e-7) return false;
  const incomingConnectors = [...b.connectorTypes].sort();
  const storedConnectors = [...a.connectorTypes].sort();
  if (incomingConnectors.length !== storedConnectors.length) return false;
  for (let i = 0; i < incomingConnectors.length; i += 1) {
    if (incomingConnectors[i] !== storedConnectors[i]) return false;
  }
  const incomingPower = b.powerKw ?? null;
  const storedPower = a.powerKw ?? null;
  if (incomingPower === null) {
    if (storedPower !== null) return false;
  } else if (storedPower === null || Math.abs(incomingPower - storedPower) > 1e-4) {
    return false;
  }
  if ((b.networkOperator ?? null) !== (a.networkOperator ?? null)) return false;
  // isAvailable defaults to true at the DB level; treat undefined-on-the-wire as "true".
  const incomingAvailable = b.isAvailable ?? true;
  if (incomingAvailable !== a.isAvailable) return false;
  return true;
}

function validateCreateBody(body: unknown): CreateStationDto {
  if (!body || typeof body !== 'object') {
    throw new HttpError(400, 'validation_failed', 'Request body must be a JSON object');
  }
  const b = body as Record<string, unknown>;
  if (typeof b.id !== 'string' || !UUID_RE.test(b.id)) {
    throw new HttpError(400, 'validation_failed', 'id must be a UUID string');
  }
  if (typeof b.name !== 'string' || b.name.trim().length === 0) {
    throw new HttpError(400, 'validation_failed', 'name is required');
  }
  if (typeof b.latitude !== 'number' || !Number.isFinite(b.latitude) || b.latitude < -90 || b.latitude > 90) {
    throw new HttpError(400, 'validation_failed', 'latitude must be a number in [-90, 90]');
  }
  if (typeof b.longitude !== 'number' || !Number.isFinite(b.longitude) || b.longitude < -180 || b.longitude > 180) {
    throw new HttpError(400, 'validation_failed', 'longitude must be a number in [-180, 180]');
  }
  if (!Array.isArray(b.connectorTypes) || !b.connectorTypes.every((c) => typeof c === 'string')) {
    throw new HttpError(400, 'validation_failed', 'connectorTypes must be an array of strings');
  }
  if (b.powerKw !== undefined && b.powerKw !== null && (typeof b.powerKw !== 'number' || !Number.isFinite(b.powerKw))) {
    throw new HttpError(400, 'validation_failed', 'powerKw must be a finite number or null');
  }
  if (b.networkOperator !== undefined && b.networkOperator !== null && typeof b.networkOperator !== 'string') {
    throw new HttpError(400, 'validation_failed', 'networkOperator must be a string or null');
  }
  if (b.isAvailable !== undefined && typeof b.isAvailable !== 'boolean') {
    throw new HttpError(400, 'validation_failed', 'isAvailable must be a boolean');
  }
  return {
    id: b.id,
    name: (b.name as string).trim(),
    latitude: b.latitude,
    longitude: b.longitude,
    connectorTypes: b.connectorTypes as string[],
    powerKw: (b.powerKw as number | null | undefined) ?? null,
    networkOperator: (b.networkOperator as string | null | undefined) ?? null,
    isAvailable: (b.isAvailable as boolean | undefined) ?? true,
  };
}

// POST /api/stations  — auth required. Idempotency contract:
//   - same id, identical body → 200 with existing row
//   - same id, conflicting body → 409 conflict
//   - new id → 201 with created row
stationsRouter.post(
  '/',
  requireAuth,
  async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    try {
      const dto = validateCreateBody(req.body);

      const existingRows = await query<ExistingStationRow>(
        `SELECT ${STATION_SELECT_COLUMNS}, created_by_uid FROM stations WHERE id = $1`,
        [dto.id]
      );
      if (existingRows.length > 0) {
        const existing = existingRows[0];
        if (bodiesMatch(existing, dto)) {
          const wire: Station = {
            id: existing.id,
            name: existing.name,
            latitude: existing.latitude,
            longitude: existing.longitude,
            connectorTypes: existing.connectorTypes,
            powerKw: existing.powerKw,
            networkOperator: existing.networkOperator,
            isAvailable: existing.isAvailable,
          };
          res.status(200).json(wire);
          return;
        }
        throw new HttpError(
          409,
          'conflict',
          `Station ${dto.id} already exists with a different body`
        );
      }

      const [station] = await query<Station>(
        `INSERT INTO stations
           (id, name, location, connector_types, power_kw, network_operator, is_available, created_by_uid)
         VALUES (
           $1, $2,
           ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography,
           $5, $6, $7, $8, $9
         )
         RETURNING ${STATION_SELECT_COLUMNS}`,
        [
          dto.id,
          dto.name,
          dto.longitude,
          dto.latitude,
          dto.connectorTypes,
          dto.powerKw,
          dto.networkOperator,
          dto.isAvailable,
          req.user!.uid,
        ]
      );
      res.status(201).json(station);
    } catch (err) {
      next(err);
    }
  }
);
