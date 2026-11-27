import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import mongoose from 'mongoose';
import app from '../app.js';

test('GET /health returns 503 degraded when MongoDB is disconnected and 200 healthy when connected without leaking secrets', async (t) => {
  const originalReadyStateDesc = Object.getOwnPropertyDescriptor(mongoose.connection, 'readyState');
  let simulatedState = 0;

  Object.defineProperty(mongoose.connection, 'readyState', {
    configurable: true,
    get: () => simulatedState,
  });

  t.after(() => {
    if (originalReadyStateDesc) {
      Object.defineProperty(mongoose.connection, 'readyState', originalReadyStateDesc);
    } else {
      delete mongoose.connection.readyState;
    }
  });

  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise((resolve) => server.close(resolve)));

  const { port } = server.address();
  const baseUrl = `http://127.0.0.1:${port}`;

  // 1. Disconnected state -> 503
  simulatedState = 0;
  const degradedRes = await fetch(`${baseUrl}/health`);
  assert.equal(degradedRes.status, 503);
  const degradedBody = await degradedRes.json();
  assert.equal(degradedBody.ok, false);
  assert.equal(degradedBody.status, 'degraded');
  assert.equal(degradedBody.mongodb, 'disconnected');
  assert.equal(degradedBody.service, 'kissansevaai-backend');

  // 2. Connected state -> 200
  simulatedState = 1;
  const healthyRes = await fetch(`${baseUrl}/health`);
  assert.equal(healthyRes.status, 200);
  const healthyBody = await healthyRes.json();
  assert.equal(healthyBody.ok, true);
  assert.equal(healthyBody.status, 'healthy');
  assert.equal(healthyBody.mongodb, 'connected');
  assert.equal(typeof healthyBody.uptimeSeconds, 'number');

  // Ensure no sensitive config strings or keys leak in health payload
  const serialized = JSON.stringify(healthyBody).toLowerCase();
  for (const forbidden of ['jwt', 'secret', 'gemini', 'mongo_uri', 'password', 'api_key']) {
    assert.equal(serialized.includes(forbidden), false, `Health payload leaked forbidden token: ${forbidden}`);
  }
});
