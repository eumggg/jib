import { Router } from 'express';
import { stationsRouter } from './stations';
import { checkInsRouter } from './checkins';

export const router = Router();

router.use('/stations', stationsRouter);
router.use('/checkins', checkInsRouter);
