import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import express from 'express';
import multer from 'multer';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Test Node cropController contract directly against a controlled mock ML server
test('Node backend preserves DINOv2 prediction, needs_review, null confidence, and withholds BOTH organic and chemical treatments when uncertain', async () => {
  // 1. Spin up mock ML server returning uncertain DINOv2 response
  const mlApp = express();
  mlApp.post('/api/crop/predict', (_req, res) => {
    res.json({
      source: 'dinov2',
      label: 'Wheat/Wheat_Black_Rust',
      crop: 'Wheat',
      condition: 'Wheat_Black_Rust',
      confidence: null,
      top_cosine_similarity: 0.482,
      neighbor_agreement: 0.40, // < 0.50: previously falsely triggered is_unknown and Gemini overwrite!
      needs_review: true,
      confidence_type: 'retrieval_similarity_and_neighbor_consensus_not_calibrated_probability',
      matches: [
        { class_name: 'Wheat/Wheat_Black_Rust', cosine_similarity: 0.482 },
        { class_name: 'Wheat/Wheat_Brown_Rust', cosine_similarity: 0.471 },
      ],
      model: 'dinov2_vits14',
      warning: 'Visual similarity is not a definitive disease diagnosis.',
    });
  });

  const mlServer = await new Promise((resolve) => {
    const s = mlApp.listen(0, '127.0.0.1', () => resolve(s));
  });
  const mlPort = mlServer.address().port;
  process.env.ML_SERVICE_URL = `http://127.0.0.1:${mlPort}`;

  const { analyzeCrop } = await import('../controllers/cropController.js');

  const tmpImg = path.join(__dirname, 'tmp_leaf.jpg');
  fs.writeFileSync(tmpImg, Buffer.from([0xff, 0xd8, 0xff, 0xd9]));

  try {
    let statusCode = 200;
    let payload = null;
    const req = {
      body: { location: 'Rajkot', soilType: 'Black', language: 'en' },
      file: { path: tmpImg, originalname: 'leaf.jpg', mimetype: 'image/jpeg' },
      user: { _id: '507f1f77bcf86cd799439011' },
    };
    const res = {
      status(code) { statusCode = code; return this; },
      json(body) { payload = body; return this; },
    };

    await analyzeCrop(req, res);

    assert.equal(statusCode, 200);
    assert.ok(payload, 'Response JSON must be returned');
    // Verify DINOv2 result is NEVER silently overwritten by Gemini
    assert.equal(payload.source, 'dinov2');
    assert.equal(payload.predictionSource, 'dinov2');
    // Verify neighbor_agreement is NEVER copied to confidence
    assert.equal(payload.confidence, null);
    assert.equal(payload.top_cosine_similarity, 0.482);
    assert.equal(payload.neighbor_agreement, 0.40);
    // Verify needs_review is preserved
    assert.equal(payload.needs_review, true);
    assert.equal(payload.treatmentWithheld, true);
    // Verify BOTH organic and chemical treatments are withheld
    assert.deepEqual(payload.treatment.organic, []);
    assert.deepEqual(payload.treatment.chemical, []);
  } finally {
    mlServer.close();
    if (fs.existsSync(tmpImg)) fs.unlinkSync(tmpImg);
  }
});
