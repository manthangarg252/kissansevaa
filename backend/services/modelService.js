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
 * Falls back to legacy /predict-crop if necessary.
 */
export const predictCropFromModel = async (imageBuffer, originalName) => {
  try {
    const form = new FormData();
    form.append('file', imageBuffer, { filename: originalName || 'leaf.jpg' });

    // Try Phase 1 Rebuild endpoint first
    let response = await fetch(`${ML_SERVICE_URL}/api/crop/predict`, {
      method: 'POST',
      body: form,
      headers: form.getHeaders(),
    });

    if (response.status === 404) {
      // Fallback to legacy endpoint if rebuild endpoint is not yet mounted
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
        is_unknown: true,
        source: 'model_assets_missing',
        missing_files: errorJson.detail?.missing_files || [],
      };
    }

    if (!response.ok) {
      const errorText = await response.text();
      console.error(`ML Service Error HTTP ${response.status}:`, errorText);
      return { error: `ML Service Error: ${response.status}`, is_unknown: true };
    }

    const data = await response.json();

    // Map Phase 1 response schema to unified structure
    return {
      predicted_class: data.label || data.predicted_class,
      crop: data.crop,
      condition: data.condition,
      confidence: data.neighbor_agreement ?? data.confidence,
      top_cosine_similarity: data.top_cosine_similarity ?? data.cosine_similarity,
      neighbor_agreement: data.neighbor_agreement ?? data.confidence,
      needs_review: data.needs_review ?? false,
      matches: data.matches || [],
      warning: data.warning || '',
      confidence_type: data.confidence_type || 'neighbor_agreement_not_calibrated_probability',
      source: 'dinov2',
      is_unknown: Boolean(data.needs_review && (data.neighbor_agreement ?? 0) < 0.50),
    };
  } catch (error) {
    console.error('Error connecting to ML service:', error.message);
    return { error: 'ML Service Unavailable', is_unknown: true, source: 'connection_error' };
  }
};
