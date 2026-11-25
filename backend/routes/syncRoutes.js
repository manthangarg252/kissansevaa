import express from 'express';
import { processSyncBatch } from '../controllers/syncController.js';
import { protect } from '../middleware/authMiddleware.js';

const router = express.Router();

router.post('/batch', protect, processSyncBatch);

export default router;
