import mongoose from 'mongoose';
import SyncOperation from '../models/SyncOperation.js';
import SavedScheme from '../models/SavedScheme.js';
import MarketFavorite from '../models/MarketFavorite.js';
import CropPlan from '../models/CropPlan.js';
import PlannerTask from '../models/PlannerTask.js';
import User from '../models/User.js';
import { createPlanWithTasks } from '../services/cropPlannerService.js';

export const processSyncBatch = async (req, res) => {
  try {
    const { operations = [] } = req.body || {};
    if (!Array.isArray(operations)) {
      return res.status(400).json({ error: 'operations array is required' });
    }

    const userId = req.user._id;
    const results = [];

    for (const op of operations) {
      const { operationId, opType, payload = {} } = op || {};
      if (!operationId || !opType) {
        results.push({ operationId: operationId || null, status: 'failed', error: 'Missing operationId or opType' });
        continue;
      }

      const existing = await SyncOperation.findOne({ userId, operationId }).lean();
      if (existing && existing.status === 'applied') {
        results.push({
          operationId,
          opType,
          status: 'already_applied',
          resultId: existing.resultId,
        });
        continue;
      }

      try {
        let resultId = null;

        switch (opType) {
          case 'planner_plan_create': {
            const created = await createPlanWithTasks({
              userId,
              cropName: payload.cropName || 'Wheat',
              variety: payload.variety || '',
              stage: payload.stage || 'vegetative',
              soilType: payload.soilType || req.user.soilType || 'Loamy',
              irrigationType: payload.irrigationType || 'Drip / Canal',
              farmSizeAcres: payload.farmSizeAcres ?? 1,
              locationName: payload.locationName || req.user.district || 'India',
              latitude: payload.latitude ?? req.user.latitude,
              longitude: payload.longitude ?? req.user.longitude,
              sowingDate: payload.sowingDate || '',
              customTask: payload.customTask || '',
              language: payload.language || req.user.languagePreference || 'en',
              clientRequestId: payload.clientRequestId || operationId,
              includeAi: false,
            });
            resultId = String(created.plan._id);
            break;
          }

          case 'planner_task_update': {
            let task = null;
            if (payload.taskId && mongoose.Types.ObjectId.isValid(String(payload.taskId))) {
              task = await PlannerTask.findOne({ _id: payload.taskId, userId });
            } else if (payload.planClientRequestId) {
              const parentPlan = await CropPlan.findOne({
                userId,
                clientRequestId: String(payload.planClientRequestId),
              }).lean();
              if (parentPlan) {
                const offset = Number(payload.dayOffset ?? 0);
                task = await PlannerTask.findOne({ planId: parentPlan._id, userId, dayOffset: offset });
              }
            }
            if (task && ['pending', 'completed', 'skipped'].includes(payload.status)) {
              task.status = payload.status;
              task.completedAt = payload.status === 'completed' ? new Date() : null;
              await task.save();
              resultId = String(task._id);
            } else {
              // Offline local task without mapped server parent no longer crashes batch sync with CastError
              resultId = String(payload.taskId || 'unmapped');
            }
            break;
          }

          case 'scheme_save': {
            if (payload.schemeId) {
              const saved = await SavedScheme.findOneAndUpdate(
                { userId, schemeId: payload.schemeId },
                { $set: { status: payload.status || 'saved', notes: payload.notes || '', clientRequestId: operationId } },
                { upsert: true, new: true },
              );
              resultId = String(saved._id);
            }
            break;
          }

          case 'scheme_unsave': {
            if (payload.schemeId) {
              await SavedScheme.deleteOne({ userId, schemeId: payload.schemeId });
              resultId = payload.schemeId;
            }
            break;
          }

          case 'market_favorite_add': {
            if (payload.crop) {
              const fav = await MarketFavorite.findOneAndUpdate(
                { userId, crop: payload.crop, market: payload.market || '' },
                {
                  $set: {
                    state: payload.state || '',
                    category: payload.category || 'crop',
                    clientRequestId: operationId,
                  },
                },
                { upsert: true, new: true },
              );
              resultId = String(fav._id);
            }
            break;
          }

          case 'market_favorite_remove': {
            if (payload.crop) {
              await MarketFavorite.deleteOne({ userId, crop: payload.crop, market: payload.market || '' });
              resultId = `${payload.crop}:${payload.market || ''}`;
            }
            break;
          }

          case 'profile_update': {
            const allowed = [
              'name',
              'phone',
              'languagePreference',
              'farmName',
              'state',
              'district',
              'city',
              'village',
              'country',
              'postalCode',
              'latitude',
              'longitude',
              'locationSource',
              'locationUpdatedAt',
              'farmSizeAcres',
              'soilType',
              'irrigationType',
              'primaryCrops',
            ];
            const setFields = {};
            for (const key of allowed) {
              if (payload[key] !== undefined) setFields[key] = payload[key];
            }
            if (Object.keys(setFields).length) {
              await User.updateOne({ _id: userId }, { $set: setFields });
            }
            resultId = String(userId);
            break;
          }

          default:
            throw new Error(`Unsupported sync opType "${opType}"`);
        }

        await SyncOperation.findOneAndUpdate(
          { userId, operationId },
          { $set: { opType, status: 'applied', resultId, processedAt: new Date() } },
          { upsert: true },
        );

        results.push({ operationId, opType, status: 'applied', resultId });
      } catch (opErr) {
        await SyncOperation.findOneAndUpdate(
          { userId, operationId },
          { $set: { opType, status: 'failed', summary: opErr.message, processedAt: new Date() } },
          { upsert: true },
        );
        results.push({ operationId, opType, status: 'failed', error: opErr.message });
      }
    }

    const applied = results.filter((r) => r.status === 'applied').length;
    const alreadyApplied = results.filter((r) => r.status === 'already_applied').length;
    const failed = results.filter((r) => r.status === 'failed').length;

    res.json({ ok: failed === 0, applied, alreadyApplied, failed, results });
  } catch (error) {
    console.error('Sync Batch Error:', error);
    res.status(500).json({ error: 'Sync batch processing failed' });
  }
};
