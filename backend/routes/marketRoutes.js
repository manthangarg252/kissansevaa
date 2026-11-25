import express from 'express';
import {
  getPrices,
  refreshPrices,
  getMarketStats,
  compareMarketPrices,
  getMarketFavorites,
  addMarketFavorite,
  removeMarketFavorite,
  getMarketRecommendations,
} from '../controllers/marketController.js';
import { protect } from '../middleware/authMiddleware.js';

const router = express.Router();

router.get('/', getPrices);
router.post('/refresh', refreshPrices);
router.get('/stats', getMarketStats);
router.get('/compare', compareMarketPrices);
router.get('/favorites', protect, getMarketFavorites);
router.post('/favorites', protect, addMarketFavorite);
router.delete('/favorites', protect, removeMarketFavorite);
router.post('/recommendations', protect, getMarketRecommendations);

export default router;
