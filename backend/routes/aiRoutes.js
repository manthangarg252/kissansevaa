import express from 'express';
import { getGeminiStatus } from '../services/geminiService.js';
import { marketService } from '../services/marketDataService.js';

const router = express.Router();

router.get('/status', (_req, res) => {
  res.json({
    gemini: getGeminiStatus(),
    marketData: {
      apiKeyConfigured: Boolean(marketService.getApiKey()),
      sourceStatus: marketService.getStats().sourceStatus,
      lastLiveSuccessAt: marketService.getStats().lastLiveSuccessAt,
    },
    weather: {
      provider: 'open-meteo',
      requiresApiKey: false,
    },
  });
});

export default router;
