import { Router, Response } from 'express';
import { requireAuth, AuthenticatedRequest } from '../middleware/auth';
import { query } from '../db';
import { User } from '../models';

export const authRouter = Router();

// POST /api/v1/auth/sync — upsert user record on first Firebase login
// Called by the Android client immediately after sign-in to persist the user in postgres.
authRouter.post('/sync', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
  const { uid, email } = req.user!;
  const { displayName } = req.body as { displayName?: string };

  try {
    const [user] = await query<User>(
      `INSERT INTO users (uid, email, display_name)
       VALUES ($1, $2, $3)
       ON CONFLICT (uid) DO UPDATE
         SET email        = EXCLUDED.email,
             display_name = COALESCE(EXCLUDED.display_name, users.display_name),
             updated_at   = NOW()
       RETURNING id, uid, email, display_name AS "displayName",
                 created_at AS "createdAt", updated_at AS "updatedAt"`,
      [uid, email ?? null, displayName ?? null]
    );
    res.json(user);
  } catch (err) {
    res.status(500).json({ error: 'Database error' });
  }
});
