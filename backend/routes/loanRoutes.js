
import express from 'express';
import { getLoanAdvice } from '../controllers/loanController.js';
import { protect } from '../middleware/authMiddleware.js';

const router = express.Router();

router.post('/advisor', protect, getLoanAdvice);

export default router;
