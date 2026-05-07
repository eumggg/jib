import { Router } from 'express';
import { stationsRouter } from './stations';
import { checkInsRouter } from './checkins';
import { authRouter } from './auth';

export const router = Router();

router.use('/auth', authRouter);
router.use('/stations', stationsRouter);
router.use('/checkins', checkInsRouter);
