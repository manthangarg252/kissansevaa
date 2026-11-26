import fs from 'fs';
import { predictCropFromModel, checkModelHealth } from '../services/modelService.js';
import { analyzeCropWithGeminiVision, getTreatmentDetails } from '../services/geminiVisionService.js';
import CropAnalysis from '../models/CropAnalysis.js';

const WITHHELD_NOTICES = {
  hi: 'अनिश्चित निदान: विशेषज्ञ या कृषि विज्ञान केंद्र (KVK) द्वारा पुष्टि होने तक जैविक और रासायनिक दोनों उपचार रोक दिए गए हैं।',
  mr: 'अनिश्चित निदान: तज्ज्ञ किंवा कृषी विज्ञान केंद्र (KVK) पडताळणीपर्यंत सेंद्रिय आणि रासायनिक दोन्ही उपचार रोखले आहेत।',
  pa: 'ਗੈਰ-ਯਕੀਨੀ ਪਛਾਣ: ਮਾਹਿਰਾਂ ਜਾਂ ਕੇਵੀਕੇ (KVK) ਦੀ ਪੁਸ਼ਟੀ ਤੱਕ ਜੈਵਿਕ ਅਤੇ ਰਸਾਇਣਕ ਦੋਵੇਂ ਇਲਾਜ ਰੋਕੇ ਗਏ ਹਨ।',
  bho: 'अनिश्चित जांच: कृषि विज्ञान केंद्र (KVK) से पक्का पुष्टि होखे तक जैविक अउर रासायनिक दूनो उपचार रोकल गइल बा।',
  sa: 'अनिश्चितनिदानम्: विशेषज्ञ-KVK पुष्टिं यावत् जैविक-रासायनिकौ द्वौ अपि उपचारौ स्थगितौ स्तः।',
  en: 'Uncertain prediction: all treatment prescriptions (both organic and chemical) are withheld pending verification by a local Krishi Vigyan Kendra (KVK) or agronomist.',
};

function normalizeTokens(text = '') {
  return String(text)
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .split(/\s+/)
    .filter((w) => w.length > 2 && !['leaf', 'crop', 'spot', 'disease', 'blight'].includes(w));
}

export function reconcileCropPredictions(primaryCrop, primaryDisease, secondOpinion) {
  if (!secondOpinion || secondOpinion.ok === false) {
    return {
      status: 'ai_unavailable',
      note: 'Gemini second opinion could not be completed. Original DINOv2 result remains preserved.',
    };
  }
  if (secondOpinion.isPlantLeaf === false) {
    return {
      status: 'unsupported_or_non_leaf',
      note: 'Gemini Vision flagged this image as non-leaf or unsupported crop imagery. Treatments remain withheld.',
    };
  }

  const pCrop = String(primaryCrop || '').toLowerCase().trim();
  const sCrop = String(secondOpinion.cropName || '').toLowerCase().trim();
  const cropMatch = Boolean(pCrop && sCrop && (pCrop.includes(sCrop) || sCrop.includes(pCrop)));

  const pTokens = new Set(normalizeTokens(primaryDisease));
  const sTokens = normalizeTokens(secondOpinion.diseaseName);
  const overlap = sTokens.filter((t) => pTokens.has(t)).length;
  const bothHealthy = String(primaryDisease || '').toLowerCase().includes('healthy')
    && String(secondOpinion.diseaseName || '').toLowerCase().includes('healthy');

  if ((cropMatch && (overlap > 0 || bothHealthy)) || (!pCrop && overlap > 0)) {
    return {
      status: 'agreed',
      note: `Both DINOv2 and Gemini second opinion align on ${secondOpinion.cropName || primaryCrop} (${secondOpinion.diseaseName}).`,
    };
  }

  return {
    status: 'disagreed',
    note: `Models disagree: DINOv2 matched "${primaryCrop || 'Unknown'} / ${primaryDisease}" while Gemini Vision suggested "${secondOpinion.cropName || 'Unknown'} / ${secondOpinion.diseaseName}". Prescriptions withheld until KVK check.`,
  };
}

export const analyzeCrop = async (req, res) => {
  try {
    const { location, soilType, language, clientRequestId, cropHint = '', symptomDescription = '' } = req.body;
    const file = req.file;

    if (!file) {
      return res.status(400).json({ error: 'No image provided' });
    }

    const dbReady = CropAnalysis.db?.readyState === 1;
    if (dbReady && clientRequestId && req.user?._id) {
      const existing = await CropAnalysis.findOne({ userId: req.user._id, clientRequestId }).lean();
      if (existing) {
        return res.json({ ...existing, idempotentReplay: true });
      }
    }

    const lang = (language || 'en').toLowerCase().trim();
    const imageBuffer = fs.readFileSync(file.path);

    // STEP 1: Query local DINOv2 ML Service (V1 production active)
    const mlResult = await predictCropFromModel(imageBuffer, file.originalname);

    let result;
    let source = 'dinov2';

    // STEP 2: Keep DINOv2 and Gemini separately attributed; NEVER silently overwrite DINOv2
    if (mlResult.unavailable || mlResult.error || !mlResult.predicted_class) {
      console.warn(`DINOv2 service unavailable (${mlResult.error || mlResult.source}). Requesting explicit Gemini Vision fallback...`);
      const geminiResult = await analyzeCropWithGeminiVision(
        imageBuffer,
        file.mimetype,
        location,
        soilType,
        language,
        { userId: req.user?._id, includePrescriptions: true },
      );

      if (!geminiResult.ok) {
        return res.status(geminiResult.statusCode || 503).json({
          error: geminiResult.error || 'Both local DINOv2 ML service and Gemini Vision are currently unavailable.',
          source: 'service_unavailable',
          dinov2Status: mlResult.source || 'unavailable',
          aiAvailable: false,
          serviceStatus: geminiResult.serviceStatus || 'upstream_unavailable',
          requestId: geminiResult.requestId || null,
          needs_review: true,
          treatmentWithheld: true,
          treatment: { organic: [], chemical: [] },
        });
      }

      const geminiConf = typeof geminiResult.confidence === 'number' ? geminiResult.confidence : null;
      const isUncertain = Boolean(
        geminiResult.needs_review
        || geminiResult.isPlantLeaf === false
        || (geminiConf !== null && geminiConf < 0.60),
      );

      source = 'gemini';
      result = {
        ...geminiResult,
        source: 'gemini',
        predictionSource: 'gemini',
        advisorySource: 'gemini',
        dinov2Status: mlResult.source || 'unavailable',
        confidence: geminiConf,
        top_cosine_similarity: null,
        neighbor_agreement: null,
        needs_review: isUncertain,
        treatmentWithheld: isUncertain,
        secondOpinionEligible: false,
        reconciliationStatus: geminiResult.isPlantLeaf === false ? 'unsupported_or_non_leaf' : 'primary_only',
        treatment: isUncertain
          ? { organic: [], chemical: [] }
          : (geminiResult.treatment || { organic: [], chemical: [] }),
        warning: isUncertain
          ? (WITHHELD_NOTICES[lang] || WITHHELD_NOTICES.en)
          : 'Prediction generated directly by Gemini Vision because the local DINOv2 service was unavailable.',
      };
    } else {
      console.log(
        `DINOv2 prediction: "${mlResult.predicted_class}" | sim=${mlResult.top_cosine_similarity} | agree=${mlResult.neighbor_agreement} | needs_review=${mlResult.needs_review}`,
      );
      source = 'dinov2';
      const isUncertain = Boolean(mlResult.needs_review);

      const enrichedData = await getTreatmentDetails(
        mlResult.predicted_class,
        location,
        soilType,
        language,
        { includePrescriptions: !isUncertain, userId: req.user?._id },
      );

      const treatment = isUncertain
        ? { organic: [], chemical: [] }
        : (enrichedData.treatment || { organic: [], chemical: [] });

      const baseWarning = mlResult.warning || '';
      const withheldNote = WITHHELD_NOTICES[lang] || WITHHELD_NOTICES.en;
      const combinedWarning = isUncertain
        ? (baseWarning ? `${withheldNote} (${baseWarning})` : withheldNote)
        : baseWarning;

      result = {
        ...enrichedData,
        cropName: enrichedData.cropName || mlResult.crop,
        diseaseName: enrichedData.diseaseName || (mlResult.condition || mlResult.predicted_class).replace(/_/g, ' '),
        confidence: null, // Never map similarity or neighbor agreement into confidence
        top_cosine_similarity: mlResult.top_cosine_similarity,
        neighbor_agreement: mlResult.neighbor_agreement,
        needs_review: isUncertain,
        treatmentWithheld: isUncertain,
        secondOpinionEligible: isUncertain,
        matches: mlResult.matches || [],
        warning: combinedWarning,
        confidence_type: mlResult.confidence_type || 'retrieval_similarity_and_neighbor_consensus_not_calibrated_probability',
        treatment,
        source: 'dinov2',
        predictionSource: 'dinov2',
        advisorySource: enrichedData.advisorySource || 'local_reference',
        reconciliationStatus: 'primary_only',
      };
    }

    let savedAnalysis = null;
    if (dbReady) {
      try {
        savedAnalysis = await CropAnalysis.create({
          userId: req.user?._id || 'anonymous',
          clientRequestId: clientRequestId || undefined,
          cropName: result.cropName,
          diseaseName: result.diseaseName,
          confidence: result.confidence ?? null,
          top_cosine_similarity: result.top_cosine_similarity,
          neighbor_agreement: result.neighbor_agreement,
          needs_review: Boolean(result.needs_review),
          treatmentWithheld: Boolean(result.treatmentWithheld),
          confidence_type: result.confidence_type,
          predictionSource: result.predictionSource,
          advisorySource: result.advisorySource,
          matches: result.matches || [],
          warning: result.warning,
          severity: result.severity,
          summary: result.summary,
          treatment: result.treatment,
          prevention: result.prevention,
          imagePath: file.path || 'temp_path',
          cropHint: String(cropHint || '').trim(),
          symptomDescription: String(symptomDescription || '').trim(),
          source,
          reconciliationStatus: result.reconciliationStatus || 'primary_only',
        });
      } catch (dbErr) {
        console.warn('Could not persist crop analysis to database:', dbErr.message);
      }
    }

    res.json({
      source,
      ...result,
      cropHint: String(cropHint || '').trim() || undefined,
      symptomDescription: String(symptomDescription || '').trim() || undefined,
      language,
      _id: savedAnalysis?._id || `temp_${Date.now()}`,
      id: savedAnalysis?._id || `temp_${Date.now()}`,
    });
  } catch (error) {
    console.error('Crop Analysis Flow Error:', error);
    res.status(500).json({ error: 'Internal server error during crop analysis' });
  }
};

/**
 * Explicit farmer-consented Gemini Vision second opinion.
 * Preserves the original DINOv2 prediction and reconciles agreement/disagreement cleanly.
 */
export const requestCropSecondOpinion = async (req, res) => {
  try {
    const {
      analysisId,
      farmerConsented,
      location,
      soilType,
      language = 'en',
      primaryCrop,
      primaryDisease,
    } = req.body || {};

    const consented = farmerConsented === true || farmerConsented === 'true' || farmerConsented === '1';
    if (!consented) {
      return res.status(400).json({
        error: 'Explicit farmer consent (farmerConsented=true) is required before requesting a Gemini second opinion.',
      });
    }

    let existingDoc = null;
    if (analysisId && CropAnalysis.db?.readyState === 1) {
      existingDoc = await CropAnalysis.findOne({ _id: analysisId, userId: req.user._id });
    }

    let imageBuffer = null;
    let mimeType = 'image/jpeg';
    if (req.file) {
      imageBuffer = fs.readFileSync(req.file.path);
      mimeType = req.file.mimetype || 'image/jpeg';
    } else if (existingDoc?.imagePath && fs.existsSync(existingDoc.imagePath)) {
      imageBuffer = fs.readFileSync(existingDoc.imagePath);
    }

    if (!imageBuffer) {
      return res.status(400).json({ error: 'Leaf image file or valid analysisId with saved image is required.' });
    }

    const baseCrop = primaryCrop || existingDoc?.cropName || '';
    const baseDisease = primaryDisease || existingDoc?.diseaseName || '';
    const primaryLabel = [baseCrop, baseDisease].filter(Boolean).join(' / ');

    const sec = await analyzeCropWithGeminiVision(
      imageBuffer,
      mimeType,
      location,
      soilType,
      language,
      { userId: req.user?._id, primaryPrediction: primaryLabel, includePrescriptions: true },
    );

    const recon = reconcileCropPredictions(baseCrop, baseDisease, sec);
    const consentTimestamp = new Date();

    if (!sec.ok) {
      const secRecord = {
        requested: true,
        farmerConsented: true,
        consentTimestamp,
        source: 'service_unavailable',
        serviceStatus: sec.serviceStatus,
        warning: sec.error,
        requestId: sec.requestId,
      };
      if (existingDoc) {
        existingDoc.secondOpinion = secRecord;
        existingDoc.reconciliationStatus = 'ai_unavailable';
        existingDoc.reconciliationNote = recon.note;
        await existingDoc.save();
      }
      return res.status(sec.statusCode || 503).json({
        ok: false,
        aiAvailable: false,
        serviceStatus: sec.serviceStatus,
        error: sec.error,
        requestId: sec.requestId,
        secondOpinion: secRecord,
        reconciliationStatus: 'ai_unavailable',
        reconciliationNote: recon.note,
      });
    }

    const keepWithheld = sec.isPlantLeaf === false || sec.needs_review || recon.status === 'disagreed';
    const secRecord = {
      requested: true,
      farmerConsented: true,
      consentTimestamp,
      source: 'gemini',
      cropName: sec.cropName,
      diseaseName: sec.diseaseName,
      confidence: sec.confidence,
      isPlantLeaf: sec.isPlantLeaf,
      summary: sec.summary,
      severity: sec.severity,
      treatment: keepWithheld ? { organic: [], chemical: [] } : sec.treatment,
      prevention: sec.prevention || [],
      warning: keepWithheld ? recon.note : 'Verified via farmer-consented Gemini Vision second opinion.',
      requestId: sec.requestId,
      serviceStatus: 'ok',
    };

    if (existingDoc) {
      existingDoc.secondOpinion = secRecord;
      existingDoc.reconciliationStatus = recon.status;
      existingDoc.reconciliationNote = recon.note;
      await existingDoc.save();
    }

    res.json({
      ok: true,
      aiAvailable: true,
      analysisId: existingDoc?._id || analysisId || null,
      primaryPreserved: true,
      primarySource: existingDoc?.predictionSource || existingDoc?.source || 'dinov2',
      primaryCrop: baseCrop,
      primaryDisease: baseDisease,
      secondOpinion: secRecord,
      reconciliationStatus: recon.status,
      reconciliationNote: recon.note,
      treatmentWithheld: keepWithheld,
    });
  } catch (error) {
    console.error('Second Opinion Error:', error);
    res.status(500).json({ error: 'Failed to process Gemini second opinion request' });
  }
};

export const getHistory = async (req, res) => {
  try {
    const history = await CropAnalysis.find({ userId: req.user._id }).sort({ createdAt: -1 });
    res.json(history);
  } catch (err) {
    res.status(500).json({ error: 'History fetch failed' });
  }
};

export const getModelStatus = async (req, res) => {
  try {
    const status = await checkModelHealth();
    res.json(status);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
};
