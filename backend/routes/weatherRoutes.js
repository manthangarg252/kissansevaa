import express from 'express';
import { getForecast, postFarmWeatherAlerts } from '../controllers/weatherController.js';
import { protect } from '../middleware/authMiddleware.js';

const router = express.Router();

router.get('/forecast', getForecast);
router.post('/alerts', protect, postFarmWeatherAlerts);

export default router;
