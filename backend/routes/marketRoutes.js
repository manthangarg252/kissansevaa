import express from 'express';
import { getPrices, refreshPrices, getMarketStats, getMarketRecommendations } from '../controllers/marketController.js';
import { protect } from '../middleware/authMiddleware.js';

const router = express.Router();

router.get('/', getPrices);
router.post('/refresh', refreshPrices);
router.get('/stats', getMarketStats);
router.post('/recommendations', protect, getMarketRecommendations);

export default router;
