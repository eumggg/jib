import { Router } from 'express';
import { stationsRouter } from './stations';
import { checkInsRouter } from './checkins';
import { authRouter } from './auth';
import { reviewsRouter } from './reviews';
import { photosRouter } from './photos';
import { reportsRouter } from './reports';
import { usersRouter } from './users';

export const router = Router();

router.use('/auth', authRouter);
router.use('/stations', stationsRouter);
router.use('/checkins', checkInsRouter);
router.use('/reviews', reviewsRouter);
router.use('/photos', photosRouter);
router.use('/reports', reportsRouter);
router.use('/users', usersRouter);
