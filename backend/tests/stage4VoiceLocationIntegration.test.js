import test from 'node:test';
import assert from 'node:assert/strict';
import mongoose from 'mongoose';
import { MongoMemoryServer } from 'mongodb-memory-server';

import app from '../app.js';
import User from '../models/User.js';
import CropPlan from '../models/CropPlan.js';
import PlannerTask from '../models/PlannerTask.js';
import { setWeatherFetcherForTests } from '../services/weatherService.js';

test('Stage 4 Backend Integration: Farm Location profile, offline sync replay, sowingDate/customTask planner, and location-aware weather/schemes/mandi', async () => {
  const mongod = await MongoMemoryServer.create();
  await mongoose.connect(mongod.getUri());

  setWeatherFetcherForTests(async (lat, lon) => ({
    current: {
      temperatureC: 24.5,
      feelsLikeC: 24.0,
      humidityPct: 50,
      windSpeedKmh: 7.5,
      precipitationMm: 0.0,
      weatherCode: 1,
      conditionLabel: 'Mainly Clear',
    },
    daily: [
      { date: '2026-11-26', tempMaxC: 28, tempMinC: 14, precipitationMm: 0, precipitationProbPct: 5, windMaxKmh: 8, weatherCode: 0, conditionLabel: 'Clear Sky' },
      { date: '2026-11-27', tempMaxC: 27, tempMinC: 13, precipitationMm: 0, precipitationProbPct: 10, windMaxKmh: 7, weatherCode: 1, conditionLabel: 'Mainly Clear' },
    ],
    hourlyNext24: [],
  }));

  process.env.JWT_SECRET ||= 'stage4_test_jwt_secret';
  const server = app.listen(0);
  await new Promise((resolve) => server.once('listening', resolve));
  const { port } = server.address();
  const baseUrl = `http://127.0.0.1:${port}`;

  try {
    // Register two separate farmers to verify multi-account isolation
    const regA = await fetch(`${baseUrl}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'Ramesh', email: 'ramesh_s4@kissan.com', password: 'Password123!' }),
    });
    assert.equal(regA.status, 201);
    const userA = await regA.json();

    const regB = await fetch(`${baseUrl}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'Suresh', email: 'suresh_s4@kissan.com', password: 'Password123!' }),
    });
    assert.equal(regB.status, 201);
    const userB = await regB.json();

    // Update Farmer A's confirmed farm location
    const profRes = await fetch(`${baseUrl}/api/auth/profile`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${userA.token}` },
      body: JSON.stringify({
        farmName: 'Satpura Plot 2',
        village: 'Bassi',
        city: 'Ludhiana',
        district: 'Ludhiana',
        state: 'Punjab',
        country: 'India',
        postalCode: '141001',
        latitude: 30.901,
        longitude: 75.8573,
        locationSource: 'gps_precise',
        locationUpdatedAt: '2026-11-26T10:00:00Z',
        farmSizeAcres: 2,
        soilType: 'Loamy',
        primaryCrops: ['Wheat'],
      }),
    });
    assert.equal(profRes.status, 200);
    const profJson = await profRes.json();
    assert.equal(profJson.farmName, 'Satpura Plot 2');
    assert.equal(profJson.district, 'Ludhiana');
    assert.equal(profJson.state, 'Punjab');
    assert.equal(profJson.locationSource, 'gps_precise');
    assert.equal(profJson.latitude, 30.901);

    // Farmer B must remain isolated
    const meB = await fetch(`${baseUrl}/api/auth/me`, {
      headers: { Authorization: `Bearer ${userB.token}` },
    });
    const meBJson = await meB.json();
    assert.equal(meBJson.district, '');
    assert.equal(meBJson.latitude, null);

    // Weather without explicit query automatically resolves Farmer A's saved coordinates
    const wRes = await fetch(`${baseUrl}/api/weather/forecast`, {
      headers: { Authorization: `Bearer ${userA.token}` },
    });
    assert.equal(wRes.status, 200);
    const wJson = await wRes.json();
    assert.equal(wJson.latitude, 30.901);
    assert.equal(wJson.longitude, 75.8573);

    // Create crop plan with sowingDate + customTask + saved location
    const planRes = await fetch(`${baseUrl}/api/planner/plans`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${userA.token}` },
      body: JSON.stringify({
        cropName: 'Wheat',
        farmSizeAcres: 2,
        soilType: 'Loamy',
        sowingDate: '2026-11-10',
        customTask: 'Apply zinc sulfate micronutrient',
        includeAi: false,
      }),
    });
    assert.equal(planRes.status, 201);
    const planJson = await planRes.json();
    assert.equal(planJson.plan.sowingDate, '2026-11-10');
    assert.equal(planJson.plan.customTask, 'Apply zinc sulfate micronutrient');
    assert.ok(planJson.tasks.some((t) => t.title.includes('zinc sulfate') && t.category === 'custom'));

    // Offline sync replay of profile_update + planner_plan_create
    const syncOpId = 'profile_sync_op_1';
    const syncBody = {
      operations: [
        {
          operationId: syncOpId,
          opType: 'profile_update',
          payload: {
            farmName: 'Updated Canal Field',
            district: 'Patiala',
            state: 'Punjab',
            locationSource: 'manual',
            locationUpdatedAt: '2026-11-26T10:30:00Z',
          },
        },
      ],
    };
    const s1 = await fetch(`${baseUrl}/api/sync/batch`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${userA.token}` },
      body: JSON.stringify(syncBody),
    });
    assert.equal(s1.status, 200);
    const s1Json = await s1.json();
    assert.equal(s1Json.results[0].status, 'applied');

    const s2 = await fetch(`${baseUrl}/api/sync/batch`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${userA.token}` },
      body: JSON.stringify(syncBody),
    });
    const s2Json = await s2.json();
    assert.equal(s2Json.results[0].status, 'already_applied');

    const updatedUserA = await User.findById(userA.id).lean();
    assert.equal(updatedUserA.farmName, 'Updated Canal Field');
    assert.equal(updatedUserA.district, 'Patiala');
  } finally {
    setWeatherFetcherForTests(null);
    await new Promise((resolve) => server.close(resolve));
    await mongoose.disconnect();
    await mongod.stop();
  }
});
