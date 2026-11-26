import fs from 'fs';
import { Type } from '@google/genai';
import {
  callGeminiVision,
  callGeminiStructured,
  classifyGeminiError,
} from '../services/geminiService.js';
import LivestockAnalysis from '../models/LivestockAnalysis.js';

const SUPPORTED_SPECIES = ['Cattle', 'Buffalo', 'Goat', 'Sheep', 'Poultry', 'Pig', 'Other'];

export const SAFETY_NOTICE =
  'Veterinary Safety Notice: Prescription antibiotics, steroids, or injectable dosage schedules are intentionally withheld. Consult a registered veterinarian (BVSc) or call Pashu helpline 1962 before medicating.';

const livestockSchema = {
  type: Type.OBJECT,
  properties: {
    conditionName: { type: Type.STRING },
    confidence: { type: Type.NUMBER },
    severity: { type: Type.STRING, enum: ['Low', 'Medium', 'High', 'Critical', 'Unknown'] },
    symptoms: { type: Type.ARRAY, items: { type: Type.STRING } },
    immediateAction: { type: Type.STRING },
    longTermCare: { type: Type.STRING },
    vetConsultationAdvice: { type: Type.STRING },
    urgentVetEscalation: { type: Type.BOOLEAN },
    escalationReason: { type: Type.STRING },
  },
  required: [
    'conditionName',
    'confidence',
    'severity',
    'symptoms',
    'immediateAction',
    'longTermCare',
    'vetConsultationAdvice',
    'urgentVetEscalation',
  ],
};

function parseBool(v) {
  return v === true || v === 'true' || v === '1';
}

export function normalizeAnimalType(raw = 'Cattle') {
  const s = String(raw || '').trim().toLowerCase();
  for (const sp of SUPPORTED_SPECIES) {
    if (s.includes(sp.toLowerCase())) return sp;
  }
  if (/cow|bull|calf|gai|गाय/.test(s)) return 'Cattle';
  if (/bhains|भैंस/.test(s)) return 'Buffalo';
  if (/bakri|बकरी/.test(s)) return 'Goat';
  if (/bhed|भेड़/.test(s)) return 'Sheep';
  if (/chicken|hen|murgi|मुर्गी|duck/.test(s)) return 'Poultry';
  return raw ? String(raw).trim() : 'Other';
}

export function evaluateLivestockRedFlags({
  animalType,
  feverStatus,
  appetiteStatus,
  milkYieldChange,
  lesionsOrUlcers,
  symptomsNotes,
  symptomDuration,
}) {
  const reasons = [];
  const notes = String(symptomsNotes || '').toLowerCase();
  const hasFever = String(feverStatus || '').toLowerCase() === 'yes';
  const noAppetite = String(appetiteStatus || '').toLowerCase() === 'none';
  const severeMilkLoss = ['dropped', 'stopped'].includes(String(milkYieldChange || '').toLowerCase());

  if (lesionsOrUlcers && hasFever) {
    reasons.push('Fever combined with blisters/lesions/ulcers (possible FMD or Lumpy Skin Disease red flag).');
  } else if (lesionsOrUlcers) {
    reasons.push('Visible ulcers, nodules, or lesions reported — isolate animal and arrange veterinary exam.');
  }

  if (hasFever && (noAppetite || severeMilkLoss)) {
    reasons.push('Fever accompanied by appetite loss or sudden milk drop requires prompt veterinary examination.');
  }

  if (animalType === 'Poultry' && /mortality|died|gasping|twisted neck|diarrh|paralysis|सूजन|मृत्यु/.test(notes)) {
    reasons.push('High-risk poultry respiratory or mortality symptoms — isolate flock and notify poultry vet immediately.');
  }

  if (/bleeding|blood|seizure|cannot stand|downer|bloat|choking|convulsion|respiratory distress|unconscious|खून|बेहोश/.test(notes)) {
    reasons.push('Critical acute distress indicator reported in clinical notes.');
  }

  if (/3\+|week|more than 3|4 day|5 day|7 day/i.test(String(symptomDuration || '')) && (hasFever || noAppetite)) {
    reasons.push('Prolonged symptomatic duration (>3 days) with systemic signs.');
  }

  return {
    urgentVetEscalation: reasons.length > 0,
    escalationReason: reasons.join(' '),
  };
}

export const analyzeLivestock = async (req, res) => {
  try {
    const {
      animalType: rawAnimalType = 'Cattle',
      animalAge = '',
      symptomDuration = '',
      feverStatus = 'unknown',
      appetiteStatus = 'unknown',
      milkYieldChange = 'unknown',
      lesionsOrUlcers: rawLesions = false,
      isolatedFromHerd: rawIsolated = false,
      symptomsNotes = '',
      language = 'en',
      clientRequestId = null,
    } = req.body || {};

    const file = req.file;
    const lesionsOrUlcers = parseBool(rawLesions);
    const isolatedFromHerd = parseBool(rawIsolated);
    const animalType = normalizeAnimalType(rawAnimalType);

    if (!file && !String(symptomsNotes || '').trim() && !lesionsOrUlcers && feverStatus === 'unknown') {
      return res.status(400).json({
        error: 'Provide either a livestock image or clinical symptom details for veterinary triage.',
      });
    }

    const dbReady = LivestockAnalysis.db?.readyState === 1;
    if (dbReady && clientRequestId && req.user?._id) {
      const existing = await LivestockAnalysis.findOne({ userId: req.user._id, clientRequestId }).lean();
      if (existing) {
        return res.json({ ...existing, idempotentReplay: true });
      }
    }

    const redFlags = evaluateLivestockRedFlags({
      animalType,
      feverStatus,
      appetiteStatus,
      milkYieldChange,
      lesionsOrUlcers,
      symptomsNotes,
      symptomDuration,
    });

    const clinicalSummary = [
      `Species: ${animalType}`,
      animalAge ? `Age: ${animalAge}` : null,
      symptomDuration ? `Duration: ${symptomDuration}` : null,
      `Fever: ${feverStatus}`,
      `Appetite: ${appetiteStatus}`,
      `Milk yield: ${milkYieldChange}`,
      `Lesions/ulcers: ${lesionsOrUlcers ? 'Yes' : 'No'}`,
      `Isolated from herd: ${isolatedFromHerd ? 'Yes' : 'No'}`,
      symptomsNotes ? `Farmer notes: ${symptomsNotes}` : null,
    ]
      .filter(Boolean)
      .join(' | ');

    const prompt = `You are a Veterinary Triage Assistant for Indian livestock farmers.
Evaluate this case (${clinicalSummary}) in language "${language}".
Strict Safety Policy:
1. Do NOT prescribe prescription antibiotics, systemic drugs, or mg/ml injectable dosages.
2. Provide safe first-aid/isolation hygiene, supportive hydration/nutrition guidance, and when to call a licensed vet (1962 / BVSc).
3. Flag urgentVetEscalation=true if contagious disease signs (FMD, LSD, HS, Anthrax, avian flu, acute bloat, high fever + lesions) are possible.
4. Set confidence honestly in [0.0, 1.0]. Never exaggerate certainty from visual photos alone.`;

    try {
      let aiRes;
      if (file) {
        const imageBuffer = fs.readFileSync(file.path);
        aiRes = await callGeminiVision({
          prompt,
          imageBuffer,
          mimeType: file.mimetype || 'image/jpeg',
          schema: livestockSchema,
          userId: req.user?._id,
          feature: 'livestock_triage',
        });
      } else {
        aiRes = await callGeminiStructured({
          prompt,
          schema: livestockSchema,
          userId: req.user?._id,
          feature: 'livestock_triage',
        });
      }

      const data = aiRes.data;
      const urgent = Boolean(redFlags.urgentVetEscalation || data.urgentVetEscalation);
      const combinedReason = [redFlags.escalationReason, data.escalationReason]
        .filter(Boolean)
        .join(' ')
        .trim();

      const payload = {
        userId: req.user?._id,
        clientRequestId: clientRequestId || undefined,
        animalType,
        animalAge,
        symptomDuration,
        feverStatus,
        appetiteStatus,
        milkYieldChange,
        lesionsOrUlcers,
        isolatedFromHerd,
        symptomsNotes,
        language,
        conditionName: data.conditionName,
        confidence: typeof data.confidence === 'number' ? data.confidence : null,
        severity: urgent && data.severity === 'Low' ? 'High' : data.severity,
        urgentVetEscalation: urgent,
        escalationReason: combinedReason,
        prescriptionsWithheld: true,
        safetyNotice: SAFETY_NOTICE,
        source: 'gemini',
        aiAvailable: true,
        serviceStatus: 'ok',
        requestId: aiRes.requestId,
        symptoms: data.symptoms || [],
        immediateAction: data.immediateAction,
        longTermCare: data.longTermCare,
        vetConsultationAdvice: data.vetConsultationAdvice,
        imagePath: file?.path || null,
      };

      let saved = null;
      if (dbReady && req.user?._id) {
        saved = await LivestockAnalysis.create(payload);
      }

      return res.json({
        ...payload,
        _id: saved?._id || `temp_${Date.now()}`,
        id: saved?._id || `temp_${Date.now()}`,
        imageUrl: file ? `/uploads/${file.filename}` : null,
        createdAt: saved?.createdAt || new Date().toISOString(),
      });
    } catch (aiErr) {
      const classified = classifyGeminiError(aiErr);
      const degradedCondition = redFlags.urgentVetEscalation
        ? 'Urgent Veterinary Escalation Flagged (AI Unavailable)'
        : 'Symptomatic Intake Logged (AI Unavailable)';
      const degradedImmediateAction = redFlags.urgentVetEscalation
        ? 'URGENT: Isolate affected animal from the herd immediately, provide clean water and shade, and contact a registered veterinarian or call 1962.'
        : 'Isolate symptomatic animal if contagious signs appear, ensure clean drinking water, and consult your local veterinary officer.';
      const degradedConsultAdvice =
        'AI image triage is currently unavailable. Call state veterinary helpline 1962 or visit the nearest veterinary dispensary.';

      let saved = null;
      if (dbReady && req.user?._id) {
        try {
          saved = await LivestockAnalysis.create({
            userId: req.user._id,
            clientRequestId: clientRequestId || undefined,
            animalType,
            animalAge,
            symptomDuration,
            feverStatus,
            appetiteStatus,
            milkYieldChange,
            lesionsOrUlcers,
            isolatedFromHerd,
            symptomsNotes,
            language,
            conditionName: degradedCondition,
            confidence: null,
            severity: redFlags.urgentVetEscalation ? 'High' : 'Unknown',
            urgentVetEscalation: redFlags.urgentVetEscalation,
            escalationReason: redFlags.escalationReason,
            prescriptionsWithheld: true,
            safetyNotice: SAFETY_NOTICE,
            source: 'service_unavailable',
            aiAvailable: false,
            serviceStatus: classified.code,
            requestId: classified.requestId,
            symptoms: [],
            immediateAction: degradedImmediateAction,
            longTermCare: '',
            vetConsultationAdvice: degradedConsultAdvice,
            imagePath: file?.path || null,
          });
        } catch (dbErr) {
          console.warn('Could not persist degraded livestock triage:', dbErr.message);
        }
      }

      // Do NOT invent fake confidence (e.g. 0.91). Return honest unavailable status + safety triage flags!
      return res.status(classified.statusCode || 503).json({
        _id: saved?._id || undefined,
        id: saved?._id || undefined,
        error: classified.userMessage,
        aiAvailable: false,
        serviceStatus: classified.code,
        requestId: classified.requestId,
        source: 'service_unavailable',
        animalType,
        conditionName: null,
        confidence: null,
        urgentVetEscalation: redFlags.urgentVetEscalation,
        escalationReason: redFlags.escalationReason,
        prescriptionsWithheld: true,
        safetyNotice: SAFETY_NOTICE,
        immediateAction: degradedImmediateAction,
        vetConsultationAdvice: degradedConsultAdvice,
        createdAt: saved?.createdAt || new Date().toISOString(),
      });
    }
  } catch (error) {
    console.error('Livestock Analysis Error:', error);
    res.status(500).json({ error: 'Internal server error during livestock triage' });
  }
};

export const getLivestockHistory = async (req, res) => {
  try {
    const history = await LivestockAnalysis.find({ userId: req.user._id })
      .sort({ createdAt: -1 })
      .limit(50)
      .lean();
    res.json(history);
  } catch (error) {
    res.status(500).json({ error: 'Failed to load livestock triage history' });
  }
};
