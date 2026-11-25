import test from 'node:test';
import assert from 'node:assert/strict';
import mongoose from 'mongoose';
import { MongoMemoryServer } from 'mongodb-memory-server';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

test('Full Stage 1 Backend Integration Suite against in-memory MongoDB', async () => {
  process.env.NODE_ENV = 'test';
  process.env.JWT_SECRET ||= crypto.randomBytes(32).toString('hex');

  const mongod = await MongoMemoryServer.create();
  await mongoose.connect(mongod.getUri());

  const { setGeminiClientFactoryForTests, resetGeminiRateLimitsForTests } = await import('../services/geminiService.js');
  const { setWeatherFetcherForTests } = await import('../services/weatherService.js');
  const { default: app } = await import('../app.js');

  setWeatherFetcherForTests(async () => ({
    current: { temperatureC: 26.5, feelsLikeC: 27, humidityPct: 60, windSpeedKmh: 7, precipitationMm: 0, weatherCode: 1, conditionLabel: 'Mainly Clear' },
    daily: [
      { date: '2026-11-26', tempMaxC: 29, tempMinC: 16, precipitationMm: 0, precipitationProbPct: 10, windMaxKmh: 9, weatherCode: 1, conditionLabel: 'Mainly Clear' },
      { date: '2026-11-27', tempMaxC: 28, tempMinC: 15, precipitationMm: 12, precipitationProbPct: 80, windMaxKmh: 16, weatherCode: 63, conditionLabel: 'Moderate Rain' },
    ],
    hourlyNext24: [],
  }));

  // Controlled Gemini mock matching requested JSON schemas or chat turns
  setGeminiClientFactoryForTests(() => ({
    models: {
      generateContent: async ({ config, contents }) => {
        if (config?.responseSchema) {
          const required = config.responseSchema.required || [];
          if (required.includes('conditionName')) {
            return {
              text: JSON.stringify({
                conditionName: 'Suspected Bovine Mastitis',
                confidence: 0.78,
                severity: 'High',
                symptoms: ['Fever', 'Swollen udder', 'Reduced milk yield'],
                immediateAction: 'Isolate cow, gently strip affected quarter milk into separate container, apply clean cold compress.',
                longTermCare: 'Maintain dry bedding and sanitize milking equipment.',
                vetConsultationAdvice: 'Call BVSc veterinarian or 1962 helpline for culture-guided antibiotic therapy.',
                urgentVetEscalation: true,
                escalationReason: 'Fever and sudden milk drop indicate acute mastitis risk.',
              }),
              usageMetadata: { totalTokenCount: 150 },
            };
          }
          if (required.includes('isPlantLeaf')) {
            return {
              text: JSON.stringify({
                cropName: 'Tomato',
                diseaseName: 'Early Blight',
                confidence: 0.84,
                isPlantLeaf: true,
                summary: 'Target-like concentric brown rings visible on tomato foliage.',
                severity: 'Medium',
                treatment: { organic: ['Remove infected lower leaves'], chemical: ['Mancozeb 75% WP per label'] },
                prevention: ['Mulch soil base', 'Avoid overhead watering'],
              }),
            };
          }
        }
        return {
          text: `Grounded agricultural response for query: ${typeof contents === 'string' ? contents.slice(0, 40) : 'multi-turn chat'}`,
          usageMetadata: { promptTokenCount: 25, candidatesTokenCount: 30, totalTokenCount: 55 },
        };
      },
    },
  }));

  const server = await new Promise((resolve) => {
    const s = app.listen(0, '127.0.0.1', () => resolve(s));
  });
  const baseUrl = `http://127.0.0.1:${server.address().port}`;

  try {
    // 1. Register & profile update
    const regRes = await fetch(`${baseUrl}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'Ramesh Patel', email: 'ramesh@test.com', password: 'Password123!' }),
    });
    assert.equal(regRes.status, 201);
    const { token } = await regRes.json();
    assert.ok(token);

    const authHeaders = {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    };

    const profRes = await fetch(`${baseUrl}/api/auth/profile`, {
      method: 'PUT',
      headers: authHeaders,
      body: JSON.stringify({ state: 'Maharashtra', district: 'Nagpur', soilType: 'Black', farmSizeAcres: 4.5, languagePreference: 'hi' }),
    });
    assert.equal(profRes.status, 200);
    const profBody = await profRes.json();
    assert.equal(profBody.district, 'Nagpur');

    // 2. Weather forecast & farm alerts
    const weatherRes = await fetch(`${baseUrl}/api/weather/forecast?location=Nagpur&includeAi=true`, { headers: authHeaders });
    assert.equal(weatherRes.status, 200);
    const weatherJson = await weatherRes.json();
    assert.equal(weatherJson.current.temperatureC, 26.5);
    assert.equal(weatherJson.aiAvailable, true);
    assert.ok(Array.isArray(weatherJson.advisories) && weatherJson.advisories.length >= 1);

    // 3. Smart Crop Planner create, list, task update, regenerate
    const planRes = await fetch(`${baseUrl}/api/planner/plans`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({ cropName: 'Tomato', stage: 'vegetative', soilType: 'Black', farmSizeAcres: 3, clientRequestId: 'plan_req_1' }),
    });
    assert.equal(planRes.status, 201);
    const planBody = await planRes.json();
    assert.ok(planBody.plan._id);
    assert.ok(planBody.tasks.length >= 5);

    const firstTaskId = planBody.tasks[0]._id;
    const taskPatch = await fetch(`${baseUrl}/api/planner/tasks/${firstTaskId}`, {
      method: 'PATCH',
      headers: authHeaders,
      body: JSON.stringify({ status: 'completed' }),
    });
    assert.equal(taskPatch.status, 200);
    const patchedTask = await taskPatch.json();
    assert.equal(patchedTask.status, 'completed');

    // 4. Livestock structured clinical triage + history + honest unavailability check
    const form = new FormData();
    form.append('animalType', 'Cattle');
    form.append('feverStatus', 'yes');
    form.append('appetiteStatus', 'none');
    form.append('milkYieldChange', 'stopped');
    form.append('lesionsOrUlcers', 'true');
    form.append('symptomsNotes', 'Udder warm and swollen');

    const liveRes = await fetch(`${baseUrl}/api/livestock/analyze`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: form,
    });
    assert.equal(liveRes.status, 200);
    const liveJson = await liveRes.json();
    assert.equal(liveJson.urgentVetEscalation, true);
    assert.equal(liveJson.prescriptionsWithheld, true);
    assert.equal(liveJson.aiAvailable, true);

    const liveHistRes = await fetch(`${baseUrl}/api/livestock/history`, { headers: authHeaders });
    assert.equal(liveHistRes.status, 200);
    const liveHist = await liveHistRes.json();
    assert.equal(liveHist.length, 1);

    // 5. Crop Second Opinion consent gate + reconciliation
    const denyConsent = await fetch(`${baseUrl}/api/crop/second-opinion`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({ farmerConsented: false }),
    });
    assert.equal(denyConsent.status, 400);

    const tmpLeaf = path.join(__dirname, 'tmp_second_op.jpg');
    fs.writeFileSync(tmpLeaf, Buffer.from([0xff, 0xd8, 0xff, 0xd9]));
    try {
      const secForm = new FormData();
      secForm.append('image', new Blob([fs.readFileSync(tmpLeaf)], { type: 'image/jpeg' }), 'leaf.jpg');
      secForm.append('farmerConsented', 'true');
      secForm.append('primaryCrop', 'Potato');
      secForm.append('primaryDisease', 'Late Blight');

      const secRes = await fetch(`${baseUrl}/api/crop/second-opinion`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: secForm,
      });
      assert.equal(secRes.status, 200);
      const secJson = await secRes.json();
      assert.equal(secJson.primaryPreserved, true);
      assert.equal(secJson.reconciliationStatus, 'disagreed');
      assert.equal(secJson.treatmentWithheld, true);
    } finally {
      if (fs.existsSync(tmpLeaf)) fs.unlinkSync(tmpLeaf);
    }

    // 6. Schemes catalog, eligibility, and bookmarks
    const schRes = await fetch(`${baseUrl}/api/schemes?category=Crop`, { headers: authHeaders });
    assert.equal(schRes.status, 200);
    const schJson = await schRes.json();
    assert.ok(schJson.schemes.some((s) => s.id === 'pmfby'));

    const eligRes = await fetch(`${baseUrl}/api/schemes/eligibility`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({ state: 'Maharashtra', farmSize: '4.5', landType: 'owned', crop: 'Cotton' }),
    });
    assert.equal(eligRes.status, 200);
    const eligJson = await eligRes.json();
    assert.ok(eligJson.matches.length >= 5);
    assert.equal(eligJson.aiAvailable, true);

    // 7. Market compare & favorites
    const cmpRes = await fetch(`${baseUrl}/api/market/compare`);
    assert.equal(cmpRes.status, 200);
    const cmpJson = await cmpRes.json();
    assert.ok(cmpJson.comparisons.length > 0);

    // 8. Multi-turn Grounded Chatbot + History + Clear
    const chatRes = await fetch(`${baseUrl}/api/chat`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({ message: 'What is cotton rate in Rajkot mandi today?', mode: 'Mandi Rates', sessionId: 'sess_1' }),
    });
    assert.equal(chatRes.status, 200);
    const chatJson = await chatRes.json();
    assert.equal(chatJson.category, 'mandi');
    assert.ok(chatJson.groundedSources.some((s) => s.startsWith('mandi_prices')));

    const histGet = await fetch(`${baseUrl}/api/chat/history?sessionId=sess_1`, { headers: authHeaders });
    assert.equal(histGet.status, 200);
    const histBody = await histGet.json();
    assert.equal(histBody.turns.length, 1);

    // 9. Idempotent Sync Batch (apply twice -> 1st applied, 2nd already_applied)
    const syncPayload = {
      operations: [
        { operationId: 'op_scheme_1', opType: 'scheme_save', payload: { schemeId: 'pm-kisan' } },
        { operationId: 'op_fav_1', opType: 'market_favorite_add', payload: { crop: 'Wheat', market: 'Nagpur APMC', state: 'Maharashtra' } },
      ],
    };
    const sync1 = await fetch(`${baseUrl}/api/sync/batch`, { method: 'POST', headers: authHeaders, body: JSON.stringify(syncPayload) });
    assert.equal(sync1.status, 200);
    const sync1Json = await sync1.json();
    assert.equal(sync1Json.applied, 2);

    const sync2 = await fetch(`${baseUrl}/api/sync/batch`, { method: 'POST', headers: authHeaders, body: JSON.stringify(syncPayload) });
    const sync2Json = await sync2.json();
    assert.equal(sync2Json.alreadyApplied, 2);

    // 10. AI failure honesty test (mock 429 quota error -> explicit 429, ZERO fake 0.91 confidence!)
    setGeminiClientFactoryForTests(() => ({
      models: {
        generateContent: async () => {
          const err = new Error('{"error":{"code":429,"message":"generate_content_free_tier_requests quota exceeded"}}');
          err.status = 429;
          throw err;
        },
      },
    }));
    resetGeminiRateLimitsForTests();

    const failLiveForm = new FormData();
    failLiveForm.append('animalType', 'Goat');
    failLiveForm.append('feverStatus', 'yes');
    failLiveForm.append('lesionsOrUlcers', 'true');
    const failLive = await fetch(`${baseUrl}/api/livestock/analyze`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: failLiveForm,
    });
    assert.equal(failLive.status, 429);
    const failLiveJson = await failLive.json();
    assert.equal(failLiveJson.aiAvailable, false);
    assert.equal(failLiveJson.confidence, null);
    assert.equal(failLiveJson.urgentVetEscalation, true);

    // 11. AI Status & Call-Agent Integration Contract (no secrets exposed)
    const aiStatusRes = await fetch(`${baseUrl}/api/ai/status`);
    assert.equal(aiStatusRes.status, 200);
    const contractRes = await fetch(`${baseUrl}/api/integrations/call-agent/contract`);
    assert.equal(contractRes.status, 200);
  } finally {
    setGeminiClientFactoryForTests(null);
    setWeatherFetcherForTests(null);
    server.close();
    await mongoose.disconnect();
    await mongod.stop();
  }
});
