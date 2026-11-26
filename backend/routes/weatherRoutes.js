import express from 'express';
import { getForecast, postFarmWeatherAlerts } from '../controllers/weatherController.js';
import { protect, optionalProtect } from '../middleware/authMiddleware.js';

const router = express.Router();

router.get('/forecast', optionalProtect, getForecast);
router.post('/alerts', protect, postFarmWeatherAlerts);

export default router;
