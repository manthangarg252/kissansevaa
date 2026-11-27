import { Type } from '@google/genai';
import {
  callGeminiStructured,
  callGeminiVision,
  classifyGeminiError,
} from './geminiService.js';

const cropDiagnosisSchema = {
  type: Type.OBJECT,
  properties: {
    cropName: { type: Type.STRING },
    diseaseName: { type: Type.STRING },
    confidence: { type: Type.NUMBER },
    isPlantLeaf: { type: Type.BOOLEAN },
    summary: { type: Type.STRING },
    severity: { type: Type.STRING, enum: ['Low', 'Medium', 'High', 'Unknown'] },
    treatment: {
      type: Type.OBJECT,
      properties: {
        organic: { type: Type.ARRAY, items: { type: Type.STRING } },
        chemical: { type: Type.ARRAY, items: { type: Type.STRING } },
      },
      required: ['organic', 'chemical'],
    },
    prevention: { type: Type.ARRAY, items: { type: Type.STRING } },
  },
  required: ['cropName', 'diseaseName', 'confidence', 'isPlantLeaf', 'summary', 'severity', 'treatment', 'prevention'],
};

const treatmentDetailsSchema = {
  type: Type.OBJECT,
  properties: {
    cropName: { type: Type.STRING },
    diseaseName: { type: Type.STRING },
    summary: { type: Type.STRING },
    severity: { type: Type.STRING, enum: ['Low', 'Medium', 'High', 'Unknown'] },
    treatment: {
      type: Type.OBJECT,
      properties: {
        organic: { type: Type.ARRAY, items: { type: Type.STRING } },
        chemical: { type: Type.ARRAY, items: { type: Type.STRING } },
      },
      required: ['organic', 'chemical'],
    },
    prevention: { type: Type.ARRAY, items: { type: Type.STRING } },
  },
  required: ['cropName', 'diseaseName', 'summary', 'severity', 'treatment', 'prevention'],
};

/**
 * Gemini Vision crop diagnosis — used ONLY when DINOv2 service is unavailable
 * or when the farmer explicitly requests a second opinion for uncertain / unsupported leaves.
 * Never returns fabricated mock diagnoses when Gemini fails.
 */
export async function analyzeCropWithGeminiVision(
  imageBuffer,
  mimeType,
  location,
  soilType,
  language,
  options = {},
) {
  const { userId = null, primaryPrediction = null, includePrescriptions = true } = options;
  const langName = {
    en: 'English',
    hi: 'Hindi',
    bho: 'Bhojpuri',
    mr: 'Marathi',
    pa: 'Punjabi',
    sa: 'Sanskrit',
  }[(language || 'en').toLowerCase().trim()] || 'English';

  const contextHint = primaryPrediction
    ? `Note: Local DINOv2 model returned an uncertain/flagged match "${primaryPrediction}". Independently inspect whether this is a healthy/diseased plant leaf, an unsupported crop outside the local taxonomy, or inconclusive.`
    : '';

  const prescriptionRule = includePrescriptions
    ? 'Provide up to 3 practical organic and 3 safe standard chemical treatments ONLY if confident (>= 0.60) and visual leaf pathology is clear.'
    : 'Leave treatment.organic and treatment.chemical as empty arrays [].';

  const prompt = `You are a senior Agronomist and Plant Pathologist.
Inspect this photo carefully.
Context: Location=${location || 'India'}, Soil=${soilType || 'Unknown'}, Response language=${langName}.
${contextHint}
Rules:
1. If the photo is NOT a plant leaf or crop (e.g., screenshot, wall, person, animal, random object), set isPlantLeaf=false, diseaseName="Non-Leaf Image", confidence=0.15, and leave treatment arrays empty.
2. Describe visible leaf symptoms clearly in summary. If image lighting, blur, or symptoms are unclear/ambiguous, set diseaseName="Inconclusive Visual Screening", confidence <= 0.50, and leave treatment arrays empty []. Do NOT force unfamiliar crops/diseases into an unrelated label.
3. Give confidence in [0.0, 1.0] strictly as a heuristic screening score (never treat it as calibrated probability).
4. ${prescriptionRule}
5. Provide 2-3 clear prevention practices in ${langName}.
Return strictly structured JSON matching the schema.`;

  try {
    const res = await callGeminiVision({
      prompt,
      imageBuffer,
      mimeType,
      schema: cropDiagnosisSchema,
      userId,
      feature: 'crop_vision',
    });

    const parsed = res.data;
    const isNonLeaf = parsed.isPlantLeaf === false;
    const rawConf = Number(parsed.confidence);
    const normConf = Number.isFinite(rawConf)
      ? Math.max(0, Math.min(1, rawConf > 1 ? rawConf / 100 : rawConf))
      : null;

    const uncertain = isNonLeaf || normConf === null || normConf < 0.60;

    return {
      ok: true,
      source: 'gemini',
      advisorySource: 'gemini',
      cropName: parsed.cropName,
      diseaseName: parsed.diseaseName,
      confidence: normConf,
      isPlantLeaf: parsed.isPlantLeaf,
      needs_review: uncertain,
      summary: parsed.summary,
      severity: parsed.severity,
      treatment: uncertain || !includePrescriptions
        ? { organic: [], chemical: [] }
        : parsed.treatment,
      prevention: parsed.prevention || [],
      requestId: res.requestId,
      model: res.model,
    };
  } catch (err) {
    const classified = classifyGeminiError(err);
    return {
      ok: false,
      source: 'service_unavailable',
      aiAvailable: false,
      serviceStatus: classified.code,
      statusCode: classified.statusCode,
      error: classified.userMessage,
      requestId: classified.requestId,
      cropName: null,
      diseaseName: null,
      confidence: null,
      needs_review: true,
      treatment: { organic: [], chemical: [] },
      prevention: [],
    };
  }
}

// Keep alias for any existing callers during refactor
export const fallbackToGeminiVision = analyzeCropWithGeminiVision;

/**
 * Generates localized agronomic notes for a DINOv2-classified disease label.
 * When `includePrescriptions` is false, both Gemini prompts and fallback templates
 * strictly withhold all organic and chemical treatment lists.
 */
export async function getTreatmentDetails(
  predictedClass,
  location,
  soilType,
  language,
  options = {},
) {
  const { includePrescriptions = true, userId = null } = options;
  const parts = String(predictedClass || 'Unknown').split('/');
  const crop = parts[0]?.replace(/_/g, ' ') || 'Crop';
  const disease = (parts[1] || predictedClass || 'Unknown').replace(/_/g, ' ');
  const isHealthy = disease.toLowerCase().includes('healthy');

  const langMap = {
    en: 'English',
    hi: 'Hindi',
    bho: 'Bhojpuri',
    mr: 'Marathi',
    pa: 'Punjabi',
    sa: 'Sanskrit',
  };
  const langCode = (language || 'en').toLowerCase().trim();
  const targetLang = langMap[langCode] || 'English';

  const prompt = includePrescriptions
    ? `Provide localized agronomic treatment and prevention guidance for "${disease}" in "${crop}". Context: Location ${location || 'India'}, Soil ${soilType || 'Unknown'}. Reply in ${targetLang}.`
    : `Provide ONLY a brief non-prescriptive visual overview and general field monitoring hygiene points for suspected "${disease}" in "${crop}" in ${targetLang}. Leave treatment.organic and treatment.chemical completely empty [].`;

  try {
    const res = await callGeminiStructured({
      prompt,
      schema: treatmentDetailsSchema,
      userId,
      feature: 'crop_advisory',
    });

    const parsed = res.data;
    return {
      ...parsed,
      cropName: parsed.cropName || crop,
      diseaseName: parsed.diseaseName || disease,
      treatment: includePrescriptions ? parsed.treatment : { organic: [], chemical: [] },
      advisorySource: 'gemini',
      aiAvailable: true,
      requestId: res.requestId,
      model: res.model,
    };
  } catch (err) {
    const classified = classifyGeminiError(err);
    // DINOv2 classification already succeeded; return non-prescriptive local educational reference
    // without pretending Gemini answered.
    return {
      cropName: crop,
      diseaseName: disease,
      severity: isHealthy ? 'Low' : 'Unknown',
      summary: includePrescriptions
        ? `Visual match points to ${disease} on ${crop}. Live AI treatment customization is currently unavailable (${classified.code}); consult a local KVK or agronomist before spraying.`
        : `Suspected visual similarity to ${disease} on ${crop}. Prescriptions are withheld until verified by a local agronomist.`,
      treatment: { organic: [], chemical: [] },
      prevention: [
        'Inspect adjacent plants for similar leaf lesions and isolate heavily infected foliage.',
        'Avoid overhead irrigation during humid evenings to reduce fungal/bacterial spread.',
        'Verify diagnosis with your nearest Krishi Vigyan Kendra (KVK) before chemical application.',
      ],
      advisorySource: 'local_reference',
      aiAvailable: false,
      serviceStatus: classified.code,
      aiMessage: classified.userMessage,
      requestId: classified.requestId,
    };
  }
}
