
import fetch from 'node-fetch';
import FormData from 'form-data';
import dotenv from 'dotenv';

dotenv.config();

const ML_SERVICE_URL = process.env.ML_SERVICE_URL || 'http://localhost:8000';

export const predictCropFromModel = async (imageBuffer, originalName) => {
  try {
    const form = new FormData();
    form.append('file', imageBuffer, { filename: originalName });

    const response = await fetch(`${ML_SERVICE_URL}/predict-crop`, {
      method: 'POST',
      body: form,
      headers: form.getHeaders(),
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`ML Service Error: ${response.status} - ${errorText}`);
    }

    return await response.json();
  } catch (error) {
    console.error('Error calling ML service:', error.message);
    // Return unknown state so backend falls back to Gemini
    return { error: 'ML Service Unavailable', is_unknown: true };
  }
};
