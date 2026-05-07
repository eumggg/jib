import { Router, Request, Response } from 'express';
import { requireAuth, AuthenticatedRequest } from '../middleware/auth';
import { query } from '../db';
import { Station, CreateStationDto } from '../models';

export const stationsRouter = Router();

// GET /api/v1/stations?swLat=&swLng=&neLat=&neLng=[&connectorType=CCS]
// Map-viewport-batching lens: bounding box, max 200 results, GIST index required.
stationsRouter.get('/', async (req: Request, res: Response) => {
  const { swLat, swLng, neLat, neLng, connectorType } = req.query as Record<string, string>;
  if (!swLat || !swLng || !neLat || !neLng) {
    res.status(400).json({ error: 'swLat, swLng, neLat, neLng are required' });
    return;
  }
  try {
    const params: unknown[] = [
      parseFloat(swLng), parseFloat(swLat),
      parseFloat(neLng), parseFloat(neLat),
    ];
    let sql = `
      SELECT id, name,
             ST_Y(location::geometry) AS latitude,
             ST_X(location::geometry) AS longitude,
             connector_types AS "connectorTypes",
             power_kw AS "powerKw",
             network_operator AS "networkOperator",
             is_available AS "isAvailable",
             created_at AS "createdAt",
             updated_at AS "updatedAt"
      FROM stations
      WHERE ST_Within(
              location::geometry,
              ST_MakeEnvelope($1, $2, $3, $4, 4326)
            )
    `;
    if (connectorType) {
      params.push(connectorType);
      sql += ` AND $${params.length} = ANY(connector_types)`;
    }
    sql += ' LIMIT 200';
    const stations = await query<Station>(sql, params);
    res.setHeader('X-Total-Count', String(stations.length));
    res.json({ stations });
  } catch (err) {
    res.status(500).json({ error: 'Database error' });
  }
});

// POST /api/v1/stations  — idempotent via client-supplied idempotencyKey UUID
stationsRouter.post('/', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
  const { idempotencyKey, name, latitude, longitude, connectorTypes, powerKw, networkOperator } =
    req.body as CreateStationDto;
  if (!idempotencyKey || !name || latitude == null || longitude == null) {
    res.status(400).json({ error: 'idempotencyKey, name, latitude, longitude are required' });
    return;
  }
  try {
    const existing = await query<Station>(
      'SELECT id FROM stations WHERE idempotency_key = $1',
      [idempotencyKey]
    );
    if (existing.length > 0) {
      const [station] = await query<Station>('SELECT * FROM stations WHERE idempotency_key = $1', [idempotencyKey]);
      res.json(station);
      return;
    }
    const [station] = await query<Station>(
      `INSERT INTO stations
         (idempotency_key, name, location, connector_types, power_kw, network_operator, created_by_uid)
       VALUES ($1, $2, ST_SetSRID(ST_MakePoint($3, $4), 4326), $5, $6, $7, $8)
       RETURNING id, name,
         ST_Y(location::geometry) AS latitude,
         ST_X(location::geometry) AS longitude,
         connector_types AS "connectorTypes",
         power_kw AS "powerKw",
         network_operator AS "networkOperator",
         is_available AS "isAvailable",
         created_at AS "createdAt",
         updated_at AS "updatedAt"`,
      [
        idempotencyKey, name, longitude, latitude,
        connectorTypes ?? [],
        powerKw ?? null,
        networkOperator ?? null,
        req.user?.uid,
      ]
    );
    res.status(201).json(station);
  } catch (err) {
    res.status(500).json({ error: 'Database error' });
  }
});

// GET /api/v1/stations/:id
stationsRouter.get('/:id', async (req: Request, res: Response) => {
  try {
    const [station] = await query<Station>(
      `SELECT id, name,
              ST_Y(location::geometry) AS latitude,
              ST_X(location::geometry) AS longitude,
              connector_types AS "connectorTypes",
              power_kw AS "powerKw",
              network_operator AS "networkOperator",
              is_available AS "isAvailable",
              created_at AS "createdAt",
              updated_at AS "updatedAt"
       FROM stations WHERE id = $1`,
      [req.params.id]
    );
    if (!station) {
      res.status(404).json({ error: 'Station not found' });
      return;
    }
    res.json(station);
  } catch (err) {
    res.status(500).json({ error: 'Database error' });
  }
});
