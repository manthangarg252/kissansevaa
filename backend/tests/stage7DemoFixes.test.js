import test from 'node:test';
import assert from 'node:assert/strict';
import mongoose from 'mongoose';
import { MongoMemoryServer } from 'mongodb-memory-server';
import app from '../app.js';
import { VERIFIED_SCHEMES } from '../services/schemeCatalogService.js';

test('Verified schemes catalog provides Hindi translations for all schemes', () => {
  assert.ok(VERIFIED_SCHEMES.length >= 10);
  for (const scheme of VERIFIED_SCHEMES) {
    assert.ok(scheme.nameHi && scheme.nameHi.trim().length > 0, `Missing nameHi on ${scheme.id}`);
    assert.ok(scheme.descriptionHi && scheme.descriptionHi.trim().length > 0, `Missing descriptionHi on ${scheme.id}`);
    assert.ok(scheme.benefitsHi && scheme.benefitsHi.trim().length > 0, `Missing benefitsHi on ${scheme.id}`);
    assert.ok(scheme.categoryHi && scheme.categoryHi.trim().length > 0, `Missing categoryHi on ${scheme.id}`);
  }
});

test('PATCH /api/planner/tasks/:taskId edits title, description, scheduledDate, and reminderEnabled cleanly', async () => {
  process.env.JWT_SECRET ||= 'stage7_test_jwt_secret';
  const mongod = await MongoMemoryServer.create();
  await mongoose.connect(mongod.getUri());
  const server = app.listen(0);
  await new Promise((resolve) => server.once('listening', resolve));
  const { port } = server.address();
  const baseUrl = `http://127.0.0.1:${port}`;

  try {
    const reg = await (await fetch(`${baseUrl}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: 'Planner Edit Farmer',
        email: 'planner_edit_test@example.com',
        password: 'Password123!',
      }),
    })).json();
    const headers = {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${reg.token}`,
    };

    const planRes = await (await fetch(`${baseUrl}/api/planner/plans`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ cropName: 'Wheat', includeAi: false }),
    })).json();
    assert.ok(planRes.tasks?.length > 0);
    const firstTaskId = planRes.tasks[0]._id;

    const patchRes = await fetch(`${baseUrl}/api/planner/tasks/${firstTaskId}`, {
      method: 'PATCH',
      headers,
      body: JSON.stringify({
        title: 'Wheat: Edited Morning Inspection',
        description: 'Inspect lower leaves for yellow rust spores.',
        scheduledDate: '2026-11-29',
        reminderEnabled: false,
      }),
    });
    assert.equal(patchRes.status, 200);
    const updated = await patchRes.json();
    assert.equal(updated.title, 'Wheat: Edited Morning Inspection');
    assert.equal(updated.description, 'Inspect lower leaves for yellow rust spores.');
    assert.equal(updated.scheduledDate, '2026-11-29');
    assert.equal(updated.reminderEnabled, false);
  } finally {
    await new Promise((resolve) => server.close(resolve));
    await mongoose.disconnect();
    await mongod.stop();
  }
});
