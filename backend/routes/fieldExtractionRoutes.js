
import express from 'express';
import { extractFields } from '../controllers/fieldExtractionController.js';
import { protect } from '../middleware/authMiddleware.js';

const router = express.Router();

router.post('/', protect, extractFields);

export default router;
