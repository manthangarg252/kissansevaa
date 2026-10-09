
import express from 'express';
import { getLiveStats, updateStats, getInsights } from '../controllers/iotController.js';
import { protect } from '../middleware/authMiddleware.js';

const router = express.Router();

router.get('/live', getLiveStats);
router.post('/update', updateStats);
router.post('/insights', protect, getInsights);

export default router;
