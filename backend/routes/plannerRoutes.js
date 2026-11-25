import express from 'express';
import {
  createCropPlan,
  listCropPlans,
  getPlanTasks,
  updatePlannerTask,
  regeneratePlanTasks,
} from '../controllers/plannerController.js';
import { protect } from '../middleware/authMiddleware.js';

const router = express.Router();

router.post('/plans', protect, createCropPlan);
router.get('/plans', protect, listCropPlans);
router.get('/plans/:id/tasks', protect, getPlanTasks);
router.patch('/tasks/:taskId', protect, updatePlannerTask);
router.post('/plans/:id/regenerate', protect, regeneratePlanTasks);

export default router;
