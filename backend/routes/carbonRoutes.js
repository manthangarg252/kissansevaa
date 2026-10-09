
import express from 'express';
import { estimateCarbon } from '../controllers/carbonController.js';
import { protect } from '../middleware/authMiddleware.js';

const router = express.Router();

router.post('/estimate', protect, estimateCarbon);

export default router;
