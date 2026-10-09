
import express from 'express';
import { getPrices, getMarketRecommendations } from '../controllers/marketController.js';
import { protect } from '../middleware/authMiddleware.js';

const router = express.Router();

router.get('/', getPrices);
router.post('/recommendations', protect, getMarketRecommendations);

export default router;
