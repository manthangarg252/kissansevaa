import CropPlan from '../models/CropPlan.js';
import PlannerTask from '../models/PlannerTask.js';
import { createPlanWithTasks, buildDeterministicSevenDayPlan } from '../services/cropPlannerService.js';
import { getWeatherForecast } from '../services/weatherService.js';

export const createCropPlan = async (req, res) => {
  try {
    const {
      cropName,
      variety,
      stage,
      soilType,
      irrigationType,
      farmSizeAcres,
      locationName,
      latitude,
      longitude,
      sowingDate,
      customTask,
      language,
      clientRequestId,
      includeAi,
    } = req.body || {};

    if (!cropName || !String(cropName).trim()) {
      return res.status(400).json({ error: 'cropName is required' });
    }

    const result = await createPlanWithTasks({
      userId: req.user._id,
      cropName: String(cropName).trim(),
      variety: variety || '',
      stage: stage || 'vegetative',
      soilType: soilType || req.user.soilType || 'Loamy',
      irrigationType: irrigationType || req.user.irrigationType || 'Drip / Canal',
      farmSizeAcres: farmSizeAcres ?? req.user.farmSizeAcres ?? 1,
      locationName: locationName || [req.user.district, req.user.state].filter(Boolean).join(', ') || 'India',
      latitude: latitude ?? req.user.latitude,
      longitude: longitude ?? req.user.longitude,
      sowingDate: sowingDate || '',
      customTask: customTask || '',
      language: language || req.user.languagePreference || 'en',
      clientRequestId,
      includeAi: includeAi !== false,
    });

    res.status(result.idempotentReplay ? 200 : 201).json(result);
  } catch (error) {
    console.error('Create Crop Plan Error:', error);
    res.status(500).json({ error: 'Failed to create crop plan' });
  }
};

export const listCropPlans = async (req, res) => {
  try {
    const plans = await CropPlan.find({ userId: req.user._id }).sort({ createdAt: -1 }).lean();
    const planIds = plans.map((p) => p._id);
    const tasks = planIds.length
      ? await PlannerTask.find({ userId: req.user._id, planId: { $in: planIds } }).sort({ dayOffset: 1 }).lean()
      : [];

    res.json({ plans, tasks });
  } catch (error) {
    res.status(500).json({ error: 'Failed to list crop plans' });
  }
};

export const getPlanTasks = async (req, res) => {
  try {
    const plan = await CropPlan.findOne({ _id: req.params.id, userId: req.user._id }).lean();
    if (!plan) {
      return res.status(404).json({ error: 'Crop plan not found' });
    }
    const tasks = await PlannerTask.find({ planId: plan._id, userId: req.user._id }).sort({ dayOffset: 1 }).lean();
    res.json({ plan, tasks });
  } catch (error) {
    res.status(500).json({ error: 'Failed to load plan tasks' });
  }
};

export const updatePlannerTask = async (req, res) => {
  try {
    const { status, reminderEnabled } = req.body || {};
    const task = await PlannerTask.findOne({ _id: req.params.taskId, userId: req.user._id });
    if (!task) {
      return res.status(404).json({ error: 'Planner task not found' });
    }

    if (status && ['pending', 'completed', 'skipped'].includes(status)) {
      task.status = status;
      task.completedAt = status === 'completed' ? new Date() : null;
    }
    if (typeof reminderEnabled === 'boolean') {
      task.reminderEnabled = reminderEnabled;
    }

    await task.save();
    res.json(task);
  } catch (error) {
    res.status(500).json({ error: 'Failed to update planner task' });
  }
};

export const regeneratePlanTasks = async (req, res) => {
  try {
    const plan = await CropPlan.findOne({ _id: req.params.id, userId: req.user._id });
    if (!plan) {
      return res.status(404).json({ error: 'Crop plan not found' });
    }

    if (req.body?.stage) {
      plan.stage = req.body.stage;
      await plan.save();
    }

    let dailyWeather = [];
    try {
      const forecast = await getWeatherForecast({
        lat: plan.latitude,
        lon: plan.longitude,
        location: plan.locationName,
        includeAi: false,
      });
      dailyWeather = forecast.daily || [];
    } catch {
      // Deterministic rules still execute offline
    }

    await PlannerTask.deleteMany({ planId: plan._id, userId: req.user._id, status: 'pending' });
    const generated = buildDeterministicSevenDayPlan({
      cropName: plan.cropName,
      stage: plan.stage,
      soilType: plan.soilType,
      irrigationType: plan.irrigationType,
      farmSizeAcres: plan.farmSizeAcres,
      dailyWeather,
      startDate: new Date(),
    });

    const newTasks = await PlannerTask.insertMany(
      generated.map((t) => ({ ...t, userId: req.user._id, planId: plan._id })),
    );

    const allTasks = await PlannerTask.find({ planId: plan._id, userId: req.user._id }).sort({ dayOffset: 1 }).lean();
    res.json({ plan: plan.toObject(), tasks: allTasks, regeneratedCount: newTasks.length });
  } catch (error) {
    res.status(500).json({ error: 'Failed to regenerate plan tasks' });
  }
};
