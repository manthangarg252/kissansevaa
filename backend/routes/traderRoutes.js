
import express from 'express';
import { getTraders, addTrader, getTraderAdvisory } from '../controllers/traderController.js';
import { protect } from '../middleware/auth.js';

const router = express.Router();

router.get('/', getTraders);
router.post('/add', protect, addTrader);
router.post('/advisory', protect, getTraderAdvisory);

export default router;
