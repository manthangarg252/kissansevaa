import test from 'node:test';
import assert from 'node:assert/strict';
import { Type } from '@google/genai';
import {
  callGeminiStructured,
  callGeminiText,
  classifyGeminiError,
  GeminiServiceError,
  getGeminiStatus,
  resetGeminiRateLimitsForTests,
  setGeminiClientFactoryForTests,
  stripMarkdownJsonFences,
  validateAgainstSchema,
} from '../services/geminiService.js';
import { computeAgriWeatherAdvisories, getWeatherForecast, setWeatherFetcherForTests } from '../services/weatherService.js';
import { buildDeterministicSevenDayPlan } from '../services/cropPlannerService.js';
import { evaluateSchemeEligibility, searchSchemesCatalog, VERIFIED_SCHEMES } from '../services/schemeCatalogService.js';
import { marketService } from '../services/marketDataService.js';
import { evaluateLivestockRedFlags } from '../controllers/livestockController.js';
import { reconcileCropPredictions } from '../controllers/cropController.js';

test('Gemini service validates structured JSON, strips markdown fences, and tracks usage', async () => {
  resetGeminiRateLimitsForTests();
  setGeminiClientFactoryForTests(() => ({
    models: {
      generateContent: async () => ({
        text: '```json\n{"crop":"Wheat","advice":"Irrigate morning"}\n```',
        usageMetadata: { promptTokenCount: 12, candidatesTokenCount: 8, totalTokenCount: 20 },
      }),
    },
  }));

  try {
    const schema = {
      type: Type.OBJECT,
      properties: {
        crop: { type: Type.STRING },
        advice: { type: Type.STRING },
      },
      required: ['crop', 'advice'],
    };

    const res = await callGeminiStructured({
      prompt: 'Give advice',
      schema,
      userId: 'user_1',
      feature: 'unit_test_struct',
    });

    assert.equal(res.aiAvailable, true);
    assert.equal(res.data.crop, 'Wheat');
    assert.ok(res.requestId.startsWith('gem_'));
    assert.equal(res.usage.totalTokens, 20);
    assert.ok(getGeminiStatus().telemetry.usageByFeature.unit_test_struct.successes >= 1);
  } finally {
    setGeminiClientFactoryForTests(null);
  }
});

test('Gemini service rejects schema mismatch instead of silently passing malformed output', async () => {
  setGeminiClientFactoryForTests(() => ({
    models: {
      generateContent: async () => ({
        text: '{"crop": 123}', // missing required field & wrong type
      }),
    },
  }));

  try {
    const schema = {
      type: Type.OBJECT,
      properties: {
        crop: { type: Type.STRING },
        advice: { type: Type.STRING },
      },
      required: ['crop', 'advice'],
    };

    await assert.rejects(
      () => callGeminiStructured({ prompt: 'Test', schema, feature: 'schema_fail' }),
      (err) => err instanceof GeminiServiceError && err.code === 'invalid_schema',
    );
  } finally {
    setGeminiClientFactoryForTests(null);
  }
});

test('Gemini service classifies 429 free-tier quota exhaustion without exposing raw API keys', () => {
  const err = classifyGeminiError(
    new Error('key=AIzaSySecret1234567890abcd {"error":{"code":429,"message":"Quota exceeded for generativelanguage.googleapis.com/generate_content_free_tier_requests","status":"RESOURCE_EXHAUSTED"}}'),
  );
  assert.equal(err.code, 'quota_exhausted');
  assert.equal(err.statusCode, 429);
  assert.ok(!err.message.includes('AIzaSySecret1234567890abcd'));
  assert.equal(stripMarkdownJsonFences('```json\n{"a":1}\n```'), '{"a":1}');
  assert.equal(validateAgainstSchema({ a: 'ok' }, { type: 'OBJECT', required: ['a'], properties: { a: { type: 'STRING' } } }).length, 0);
});

test('Gemini per-user rate limit rejects burst exceeding RPM cleanly', async () => {
  resetGeminiRateLimitsForTests();
  setGeminiClientFactoryForTests(() => ({
    models: {
      generateContent: async () => ({ text: 'OK' }),
    },
  }));

  try {
    const rpm = Number(process.env.GEMINI_USER_RPM || 20);
    for (let i = 0; i < rpm; i++) {
      await callGeminiText({ prompt: 'hi', userId: 'burst_user', feature: 'burst' });
    }
    await assert.rejects(
      () => callGeminiText({ prompt: 'hi again', userId: 'burst_user', feature: 'burst' }),
      (err) => err instanceof GeminiServiceError && err.code === 'rate_limited' && err.statusCode === 429,
    );
  } finally {
    setGeminiClientFactoryForTests(null);
    resetGeminiRateLimitsForTests();
  }
});

test('Weather rules engine detects spray wash-off risk, heat stress, and works with mock Open-Meteo', async () => {
  const { advisories, sprayWindowSuitable } = computeAgriWeatherAdvisories({
    current: { temperatureC: 41.2, windSpeedKmh: 10, humidityPct: 35, precipitationMm: 0 },
    daily: [
      { date: '2026-11-26', tempMaxC: 41.5, tempMinC: 24, precipitationMm: 12, precipitationProbPct: 80, windMaxKmh: 18 },
      { date: '2026-11-27', tempMaxC: 38, tempMinC: 22, precipitationMm: 2, precipitationProbPct: 30, windMaxKmh: 12 },
      { date: '2026-11-28', tempMaxC: 36, tempMinC: 21, precipitationMm: 0, precipitationProbPct: 10, windMaxKmh: 10 },
    ],
  });

  assert.equal(sprayWindowSuitable, false);
  assert.ok(advisories.some((a) => a.id === 'spray_rain_risk'));
  assert.ok(advisories.some((a) => a.id === 'heat_stress'));
  assert.ok(advisories.some((a) => a.id === 'irrigation_defer'));

  setWeatherFetcherForTests(async () => ({
    current: { temperatureC: 27, feelsLikeC: 27, humidityPct: 55, windSpeedKmh: 6, precipitationMm: 0, weatherCode: 0, conditionLabel: 'Clear Sky' },
    daily: [{ date: '2026-11-26', tempMaxC: 30, tempMinC: 18, precipitationMm: 0, precipitationProbPct: 5, windMaxKmh: 9, weatherCode: 0, conditionLabel: 'Clear Sky' }],
    hourlyNext24: [],
  }));
  try {
    const forecast = await getWeatherForecast({ lat: 21.15, lon: 79.08, location: 'Nagpur', includeAi: false });
    assert.equal(forecast.source, 'live');
    assert.equal(forecast.sprayWindowSuitable, true);
  } finally {
    setWeatherFetcherForTests(null);
  }
});

test('Crop planner reschedules sprays away from rainy days deterministically', () => {
  const dailyWeather = [
    { date: '2026-11-26', precipitationProbPct: 10, precipitationMm: 0, windMaxKmh: 8 },
    { date: '2026-11-27', precipitationProbPct: 80, precipitationMm: 15, windMaxKmh: 12 },
    { date: '2026-11-28', precipitationProbPct: 20, precipitationMm: 0, windMaxKmh: 10 },
    { date: '2026-11-29', precipitationProbPct: 10, precipitationMm: 0, windMaxKmh: 9 },
    { date: '2026-11-30', precipitationProbPct: 10, precipitationMm: 0, windMaxKmh: 9 },
    { date: '2026-12-01', precipitationProbPct: 85, precipitationMm: 12, windMaxKmh: 25 },
    { date: '2026-12-02', precipitationProbPct: 5, precipitationMm: 0, windMaxKmh: 6 },
  ];

  const tasks = buildDeterministicSevenDayPlan({
    cropName: 'Tomato',
    stage: 'vegetative',
    soilType: 'Loamy',
    dailyWeather,
    startDate: new Date('2026-11-26T00:00:00Z'),
  });

  const sprayTask = tasks.find((t) => t.category === 'spray');
  assert.ok(sprayTask, 'Must contain spray task');
  assert.equal(sprayTask.weatherAdjusted, true);
  assert.equal(sprayTask.dayOffset, 6); // moved from rainy Day 5 to clear Day 6
});

test('Schemes catalog, eligibility engine, livestock red flags, crop reconciliation, and mandi comparisons are accurate', () => {
  assert.ok(VERIFIED_SCHEMES.length >= 10);
  const kisanOnly = searchSchemesCatalog({ query: 'PM-KISAN' });
  assert.equal(kisanOnly[0].id, 'pm-kisan');

  const tenantEval = evaluateSchemeEligibility({ state: 'Maharashtra', farmSize: '2', landType: 'tenant', crop: 'Cotton', hasLivestock: true });
  const pmKisanMatch = tenantEval.find((e) => e.scheme.id === 'pm-kisan');
  assert.ok(pmKisanMatch.cautions.length > 0, 'Tenant farmer must get land ownership caution for PM-KISAN');

  const livestockFlag = evaluateLivestockRedFlags({
    animalType: 'Cattle',
    feverStatus: 'yes',
    appetiteStatus: 'none',
    milkYieldChange: 'stopped',
    lesionsOrUlcers: true,
    symptomsNotes: '',
    symptomDuration: '2 days',
  });
  assert.equal(livestockFlag.urgentVetEscalation, true);

  const reconDisagree = reconcileCropPredictions('Wheat', 'Wheat_Black_Rust', {
    ok: true,
    isPlantLeaf: true,
    cropName: 'Tomato',
    diseaseName: 'Early Blight',
  });
  assert.equal(reconDisagree.status, 'disagreed');

  const reconAgree = reconcileCropPredictions('Tomato', 'Tomato_Early_Blight', {
    ok: true,
    isPlantLeaf: true,
    cropName: 'Tomato',
    diseaseName: 'Early Blight',
  });
  assert.equal(reconAgree.status, 'agreed');

  const stats = marketService.getStats();
  assert.ok(stats.cropsCount > 0 && stats.vegetablesCount > 0 && stats.fruitsCount > 0);
  const cmp = marketService.compareMarkets({});
  assert.ok(cmp.length > 0);
});
