
import express from 'express';
import { recommendSchemes } from '../controllers/schemeController.js';
import { protect } from '../middleware/authMiddleware.js';

const router = express.Router();

router.post('/recommendations', protect, recommendSchemes);

export default router;
