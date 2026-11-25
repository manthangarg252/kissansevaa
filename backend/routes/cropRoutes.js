import express from 'express';
import multer from 'multer';
import path from 'path';
import {
  analyzeCrop,
  requestCropSecondOpinion,
  getHistory,
  getModelStatus,
} from '../controllers/cropController.js';
import { protect } from '../middleware/authMiddleware.js';

import { fileURLToPath } from 'url';
import fs from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const uploadDir = path.join(__dirname, '../uploads');

if (!fs.existsSync(uploadDir)) {
  fs.mkdirSync(uploadDir, { recursive: true });
}

const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    if (!fs.existsSync(uploadDir)) {
      fs.mkdirSync(uploadDir, { recursive: true });
    }
    cb(null, uploadDir);
  },
  filename: (req, file, cb) => cb(null, `${Date.now()}-${file.originalname}`),
});

const upload = multer({ storage, limits: { fileSize: 10 * 1024 * 1024 } });
const router = express.Router();

router.post('/analyze', protect, upload.single('image'), analyzeCrop);
router.post('/second-opinion', protect, upload.single('image'), requestCropSecondOpinion);
router.get('/history', protect, getHistory);
router.get('/model-status', getModelStatus);

export default router;
