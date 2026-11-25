import fetch from 'node-fetch';
import FormData from 'form-data';
import dotenv from 'dotenv';

dotenv.config();

const ML_SERVICE_URL = process.env.ML_SERVICE_URL || 'http://localhost:8000';

/**
 * Health check on the DINOv2 ML inference service.
 */
export const checkModelHealth = async () => {
  try {
    const res = await fetch(`${ML_SERVICE_URL}/health`);
    if (!res.ok) {
      return { ok: false, status: res.status, error: await res.text() };
    }
    return await res.json();
  } catch (error) {
    return { ok: false, status: 'unavailable', error: error.message };
  }
};

/**
 * Invokes the Phase 1 DINOv2-S/14 + FAISS crop diagnosis endpoint.
 * Falls back to legacy /predict-crop if /api/crop/predict returns 404.
 *
 * Never maps neighbor_agreement or cosine_similarity into `confidence`,
 * and never marks a valid DINOv2 prediction as unavailable solely due to `needs_review`.
 */
export const predictCropFromModel = async (imageBuffer, originalName) => {
  try {
    const form = new FormData();
    form.append('file', imageBuffer, { filename: originalName || 'leaf.jpg' });

    let response = await fetch(`${ML_SERVICE_URL}/api/crop/predict`, {
      method: 'POST',
      body: form,
      headers: form.getHeaders(),
    });

    if (response.status === 404) {
      const legacyForm = new FormData();
      legacyForm.append('file', imageBuffer, { filename: originalName || 'leaf.jpg' });
      response = await fetch(`${ML_SERVICE_URL}/predict-crop`, {
        method: 'POST',
        body: legacyForm,
        headers: legacyForm.getHeaders(),
      });
    }

    if (response.status === 503) {
      const errorJson = await response.json().catch(() => ({}));
      console.warn('DINOv2 Model assets missing on ML service:', errorJson);
      return {
        error: 'Model assets missing on ML service',
        unavailable: true,
        source: 'model_assets_missing',
        missing_files: errorJson.detail?.missing_files || [],
      };
    }

    if (!response.ok) {
      const errorText = await response.text();
      console.error(`ML Service Error HTTP ${response.status}:`, errorText);
      return { error: `ML Service Error: ${response.status}`, unavailable: true, source: 'ml_http_error' };
    }

    const data = await response.json();
    if (data.error || !(data.label || data.predicted_class)) {
      return {
        error: data.error || 'ML service returned no class label',
        unavailable: true,
        source: data.source || 'ml_invalid_response',
      };
    }

    return {
      predicted_class: data.label || data.predicted_class,
      crop: data.crop,
      condition: data.condition,
      // Explicitly null: retrieval similarity and neighbor agreement are NOT calibrated confidence probabilities
      confidence: null,
      top_cosine_similarity: data.top_cosine_similarity ?? data.cosine_similarity ?? null,
      neighbor_agreement: data.neighbor_agreement ?? null,
      needs_review: Boolean(data.needs_review ?? false),
      matches: data.matches || [],
      warning: data.warning || '',
      confidence_type: data.confidence_type || 'retrieval_similarity_and_neighbor_consensus_not_calibrated_probability',
      source: 'dinov2',
      predictionSource: 'dinov2',
      model: data.model || 'dinov2_vits14',
      unavailable: false,
    };
  } catch (error) {
    console.error('Error connecting to ML service:', error.message);
    return { error: 'ML Service Unavailable', unavailable: true, source: 'connection_error' };
  }
};
