import express from 'express';
import {
  getSchemesCatalog,
  checkSchemeEligibility,
  recommendSchemes,
  explainScheme,
  getSavedSchemes,
  saveScheme,
  removeSavedScheme,
} from '../controllers/schemeController.js';
import { protect } from '../middleware/authMiddleware.js';

const router = express.Router();

router.get('/', getSchemesCatalog);
router.post('/eligibility', protect, checkSchemeEligibility);
router.post('/recommendations', protect, recommendSchemes);
router.post('/explain', protect, explainScheme);
router.get('/saved', protect, getSavedSchemes);
router.post('/saved', protect, saveScheme);
router.delete('/saved/:schemeId', protect, removeSavedScheme);

export default router;
