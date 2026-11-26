import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import mongoose from 'mongoose';
import { MongoMemoryServer } from 'mongodb-memory-server';

import app from '../app.js';
import User from '../models/User.js';
import CropAnalysis from '../models/CropAnalysis.js';
import LivestockAnalysis from '../models/LivestockAnalysis.js';
import CropPlan from '../models/CropPlan.js';
import { setWeatherFetcherForTests } from '../services/weatherService.js';
import { setGeminiClientFactoryForTests, resetGeminiRateLimitsForTests } from '../services/geminiService.js';

function signSarvamPayload(secret, timestamp, bodyJson) {
  return (
    'sha256=' +
    crypto
      .createHmac('sha256', secret)
      .update(`${timestamp}.${bodyJson}`)
      .digest('hex')
  );
}

test('Sarvam Call-Agent backend auth, HMAC signing, validation, and all 6 grounded tools', async () => {
  const mongod = await MongoMemoryServer.create();
  await mongoose.connect(mongod.getUri());

  setWeatherFetcherForTests(async () => ({
    current: {
      temperatureC: 29.0,
      feelsLikeC: 29.0,
      humidityPct: 58,
      windSpeedKmh: 6.2,
      precipitationMm: 0.0,
      weatherCode: 0,
      conditionLabel: 'Clear Sky',
    },
    daily: [
      { date: '2026-11-26', tempMaxC: 31, tempMinC: 19, precipitationMm: 0, precipitationProbPct: 5, windMaxKmh: 8, weatherCode: 0, conditionLabel: 'Clear Sky' },
      { date: '2026-11-27', tempMaxC: 32, tempMinC: 20, precipitationMm: 0, precipitationProbPct: 10, windMaxKmh: 9, weatherCode: 1, conditionLabel: 'Partly Cloudy' },
    ],
    hourlyNext24: [],
  }));

  const prevWebhookSecret = process.env.CALL_AGENT_WEBHOOK_SECRET;
  const prevSigningSecret = process.env.SARVAM_WEBHOOK_SIGNING_SECRET;
  delete process.env.CALL_AGENT_WEBHOOK_SECRET;
  delete process.env.SARVAM_WEBHOOK_SIGNING_SECRET;

  const server = app.listen(0);
  await new Promise((resolve) => server.once('listening', resolve));
  const { port } = server.address();
  const baseUrl = `http://127.0.0.1:${port}`;

  try {
    // 1. Contract endpoint lists all 6 Sarvam tools cleanly without secrets
    const c1 = await fetch(`${baseUrl}/api/integrations/call-agent/contract`);
    assert.equal(c1.status, 200);
    const c1Json = await c1.json();
    assert.equal(c1Json.configuration.sharedSecretConfigured, false);
    assert.equal(c1Json.supportedTools.length, 6);

    // 2. Unconfigured webhook fails safely with 503
    const unconfRes = await fetch(`${baseUrl}/api/integrations/call-agent/webhook`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ intent: 'mandi_rate_lookup', crop: 'Wheat' }),
    });
    assert.equal(unconfRes.status, 503);

    // Configure secrets for live test assertions (never real secrets)
    const testSecret = 'test_call_agent_secret_xyz';
    const testSignSecret = 'test_sarvam_hmac_key_abc';
    process.env.CALL_AGENT_WEBHOOK_SECRET = testSecret;
    process.env.SARVAM_WEBHOOK_SIGNING_SECRET = testSignSecret;

    // 3. Reject invalid secret & invalid HMAC signature & stale replay timestamp
    const badSecret = await fetch(`${baseUrl}/api/integrations/call-agent/webhook`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Call-Agent-Secret': 'wrong' },
      body: JSON.stringify({ tool: 'mandi_prices', crop: 'Wheat' }),
    });
    assert.equal(badSecret.status, 401);

    const payloadBody = JSON.stringify({ tool: 'mandi_prices', parameters: { crop: 'Wheat' } });
    const badSig = await fetch(`${baseUrl}/api/integrations/call-agent/tools/mandi_prices`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Sarvam-Timestamp': String(Math.floor(Date.now() / 1000)),
        'X-Sarvam-Signature': 'sha256=deadbeef',
      },
      body: payloadBody,
    });
    assert.equal(badSig.status, 401);

    const staleTs = String(Math.floor(Date.now() / 1000) - 900);
    const staleSig = signSarvamPayload(testSignSecret, staleTs, payloadBody);
    const replayReject = await fetch(`${baseUrl}/api/integrations/call-agent/tools/mandi_prices`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Sarvam-Timestamp': staleTs,
        'X-Sarvam-Signature': staleSig,
      },
      body: payloadBody,
    });
    assert.equal(replayReject.status, 401);

    // 4. Reject unknown tool with 400 validation error
    const badTool = await fetch(`${baseUrl}/api/integrations/call-agent/tools/nonexistent_tool`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Call-Agent-Secret': testSecret },
      body: JSON.stringify({}),
    });
    assert.equal(badTool.status, 400);

    // Seed farmer data linked to phone number + crop uncertainty + livestock + plan
    const farmer = await User.create({
      name: 'Ramesh Kisan',
      email: 'ramesh.sarvam@example.com',
      password: 'Password123!',
      phone: '+91 98765 43219',
      state: 'Maharashtra',
      district: 'Nagpur',
      latitude: 21.1458,
      longitude: 79.0882,
      farmSizeAcres: 3.5,
      soilType: 'Black',
      primaryCrops: ['Wheat', 'Cotton'],
    });

    await CropAnalysis.create({
      userId: farmer._id,
      cropName: 'Wheat',
      diseaseName: 'Wheat Leaf Blight',
      confidence: null,
      top_cosine_similarity: 0.42,
      neighbor_agreement: 0.4,
      needs_review: true,
      treatmentWithheld: true,
      source: 'dinov2',
      predictionSource: 'dinov2',
      advisorySource: 'local_reference',
      treatment: { organic: ['Should stay hidden'], chemical: ['Should stay hidden'] },
      imagePath: 'uploads/test.jpg',
    });

    await CropPlan.create({
      userId: farmer._id,
      cropName: 'Wheat',
      stage: 'vegetative',
      soilType: 'Black',
      status: 'active',
    });

    await LivestockAnalysis.create({
      userId: farmer._id,
      animalType: 'Cattle',
      feverStatus: 'yes',
      lesionsOrUlcers: true,
      urgentVetEscalation: true,
      escalationReason: 'Fever with skin lesions.',
      source: 'service_unavailable',
      conditionName: 'Urgent Veterinary Escalation Flagged (AI Unavailable)',
    });

    // Helper for HMAC signed Sarvam call
    async function callWithHmac(endpoint, bodyObj) {
      const json = JSON.stringify(bodyObj);
      const ts = String(Math.floor(Date.now() / 1000));
      const sig = signSarvamPayload(testSignSecret, ts, json);
      return fetch(`${baseUrl}/api/integrations/call-agent${endpoint}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Sarvam-Timestamp': ts,
          'X-Sarvam-Signature': sig,
        },
        body: json,
      });
    }

    // 5a. Crop assistance honors needs_review + treatmentWithheld
    const cropRes = await callWithHmac('/tools/crop_assistance', { callerPhone: '9876543219', crop: 'Wheat' });
    assert.equal(cropRes.status, 200);
    const cropJson = await cropRes.json();
    assert.equal(cropJson.authMethod, 'hmac_sha256');
    assert.equal(cropJson.farmerMatched, true);
    assert.equal(cropJson.latestScan.treatmentWithheld, true);
    assert.deepEqual(cropJson.latestScan.treatment, { organic: [], chemical: [] });
    assert.match(cropJson.spokenSummary, /withheld/i);

    // 5b. Livestock questions triggers urgent 1962 escalation without prescribing antibiotics
    const liveRes = await callWithHmac('/tools/livestock_questions', {
      callerPhone: '+91-9876543219',
      animalType: 'Cow',
      feverStatus: 'yes',
      lesionsOrUlcers: true,
      symptomsNotes: 'High fever and blisters on mouth',
    });
    assert.equal(liveRes.status, 200);
    const liveJson = await liveRes.json();
    assert.equal(liveJson.animalType, 'Cattle');
    assert.equal(liveJson.urgentVetEscalation, true);
    assert.equal(liveJson.prescriptionsWithheld, true);
    assert.equal(liveJson.helplineNumber, '1962');
    assert.match(liveJson.spokenSummary, /1962/);

    // 5c. Weather forecast resolves farmer profile location automatically
    const weatherRes = await callWithHmac('/tools/weather_forecasts', { callerPhone: '9876543219' });
    assert.equal(weatherRes.status, 200);
    const weatherJson = await weatherRes.json();
    assert.equal(weatherJson.farmerMatched, true);
    assert.equal(weatherJson.current.temperatureC, 29);
    assert.equal(weatherJson.sprayWindowSuitable, true);

    // 5d. Mandi prices returns real catalog prices + spread
    const mandiRes = await callWithHmac('/webhook', { intent: 'mandi_rate_lookup', crop: 'Wheat' });
    assert.equal(mandiRes.status, 200);
    const mandiJson = await mandiRes.json();
    assert.ok(mandiJson.prices.length >= 1);
    assert.match(mandiJson.spokenSummary, /Wheat/i);

    // 5e. Government schemes supports profile-matched eligibility
    const schemeRes = await callWithHmac('/tools/government_schemes', { callerPhone: '9876543219', crop: 'Wheat' });
    assert.equal(schemeRes.status, 200);
    const schemeJson = await schemeRes.json();
    assert.equal(schemeJson.eligibilityEvaluated, true);
    assert.ok(schemeJson.schemes.length >= 1);

    // 5f. General farming advice degrades gracefully if Gemini returns 429
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

    const adviceRes = await callWithHmac('/tools/general_farming_advice', {
      callerPhone: '9876543219',
      query: 'What should I check on my wheat crop today?',
    });
    assert.equal(adviceRes.status, 200);
    const adviceJson = await adviceRes.json();
    assert.equal(adviceJson.aiAvailable, false);
    assert.equal(adviceJson.serviceStatus, 'quota_exhausted');
    assert.ok(adviceJson.groundedSources.length >= 1);
    assert.match(adviceJson.spokenSummary, /quota_exhausted/i);
  } finally {
    setGeminiClientFactoryForTests(null);
    setWeatherFetcherForTests(null);
    if (prevWebhookSecret === undefined) delete process.env.CALL_AGENT_WEBHOOK_SECRET;
    else process.env.CALL_AGENT_WEBHOOK_SECRET = prevWebhookSecret;
    if (prevSigningSecret === undefined) delete process.env.SARVAM_WEBHOOK_SIGNING_SECRET;
    else process.env.SARVAM_WEBHOOK_SIGNING_SECRET = prevSigningSecret;
    server.close();
    await mongoose.disconnect();
    await mongod.stop();
  }
});
