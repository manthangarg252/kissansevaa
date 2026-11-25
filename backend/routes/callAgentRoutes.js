import express from 'express';
import { searchSchemesCatalog } from '../services/schemeCatalogService.js';
import { getWeatherForecast } from '../services/weatherService.js';
import { marketService } from '../services/marketDataService.js';

const router = express.Router();

/**
 * Integration contract discovery endpoint for external voice/telephony call agents.
 * Note: Stage 1 does NOT build a telephony dialer or replacement call agent; this exposes a clean
 * webhook/context contract if an external telephony partner connects later.
 */
router.get('/contract', (_req, res) => {
  res.json({
    service: 'kissansevaai-call-agent-adapter',
    status: 'contract_ready_external_telephony_required',
    note: 'External telephony/SIP/IVR credentials are required before live voice call routing is active.',
    webhookEndpoint: '/api/integrations/call-agent/webhook',
    authHeader: 'X-Call-Agent-Secret',
    supportedIntents: ['mandi_rate_lookup', 'weather_spray_check', 'scheme_summary'],
  });
});

router.post('/webhook', async (req, res) => {
  const expectedSecret = process.env.CALL_AGENT_WEBHOOK_SECRET;
  if (!expectedSecret) {
    return res.status(503).json({
      error: 'External call-agent telephony secret (CALL_AGENT_WEBHOOK_SECRET) is not configured.',
      serviceStatus: 'not_configured',
    });
  }

  const provided = req.headers['x-call-agent-secret'];
  if (provided !== expectedSecret) {
    return res.status(401).json({ error: 'Invalid integration secret' });
  }

  const { intent = 'mandi_rate_lookup', crop = '', state = '', location = '' } = req.body || {};

  if (intent === 'weather_spray_check') {
    const w = await getWeatherForecast({ location: location || state || 'New Delhi', includeAi: false });
    return res.json({
      intent,
      spokenSummary: `Weather at ${w.locationName}: ${w.current.temperatureC} degrees, humidity ${w.current.humidityPct} percent. ${w.advisories?.[0]?.message || ''}`,
      sprayWindowSuitable: w.sprayWindowSuitable,
      source: w.source,
    });
  }

  if (intent === 'scheme_summary') {
    const schemes = searchSchemesCatalog({ state, query: crop }).slice(0, 3);
    return res.json({
      intent,
      spokenSummary: schemes.map((s) => `${s.name}: ${s.benefits}`).join('. '),
      schemes: schemes.map((s) => ({ id: s.id, name: s.name, officialPortal: s.officialPortal })),
    });
  }

  const prices = marketService.getPrices({ crop, state }).slice(0, 3);
  return res.json({
    intent: 'mandi_rate_lookup',
    spokenSummary: prices.length
      ? prices.map((p) => `${p.crop} in ${p.market}: rupees ${p.modalPrice || p.price} per quintal`).join('. ')
      : 'No matching mandi rates found right now.',
    prices,
  });
});

export default router;
