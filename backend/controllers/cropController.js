import fs from 'fs';
import { predictCropFromModel, checkModelHealth } from '../services/modelService.js';
import { fallbackToGeminiVision, getTreatmentDetails } from '../services/geminiVisionService.js';
import CropAnalysis from '../models/CropAnalysis.js';

const WITHHELD_NOTICES = {
  hi: 'अनिश्चित निदान: विशेषज्ञ या कृषि विज्ञान केंद्र (KVK) द्वारा पुष्टि होने तक जैविक और रासायनिक दोनों उपचार रोक दिए गए हैं।',
  mr: 'अनिश्चित निदान: तज्ज्ञ किंवा कृषी विज्ञान केंद्र (KVK) पडताळणीपर्यंत सेंद्रिय आणि रासायनिक दोन्ही उपचार रोखले आहेत।',
  pa: 'ਗੈਰ-ਯਕੀਨੀ ਪਛਾਣ: ਮਾਹਿਰਾਂ ਜਾਂ ਕੇਵੀਕੇ (KVK) ਦੀ ਪੁਸ਼ਟੀ ਤੱਕ ਜੈਵਿਕ ਅਤੇ ਰਸਾਇਣਕ ਦੋਵੇਂ ਇਲਾਜ ਰੋਕੇ ਗਏ ਹਨ।',
  bho: 'अनिश्चित जांच: कृषि विज्ञान केंद्र (KVK) से पक्का पुष्टि होखे तक जैविक अउर रासायनिक दूनो उपचार रोकल गइल बा।',
  sa: 'अनिश्चितनिदानम्: विशेषज्ञ-KVK पुष्टिं यावत् जैविक-रासायनिकौ द्वौ अपि उपचारौ स्थगितौ स्तः।',
  en: 'Uncertain prediction: all treatment prescriptions (both organic and chemical) are withheld pending verification by a local Krishi Vigyan Kendra (KVK) or agronomist.'
};

export const analyzeCrop = async (req, res) => {
  try {
    const { location, soilType, language } = req.body;
    const file = req.file;

    if (!file) {
      return res.status(400).json({ error: 'No image provided' });
    }

    const lang = (language || 'en').toLowerCase().trim();
    const imageBuffer = fs.readFileSync(file.path);

    // STEP 1: Query local DINOv2 ML Service
    const mlResult = await predictCropFromModel(imageBuffer, file.originalname);

    let result;
    let source = 'dinov2';

    // STEP 2: Keep DINOv2 and Gemini separately attributed; NEVER silently overwrite a DINOv2 result with Gemini
    if (mlResult.unavailable || mlResult.error || !mlResult.predicted_class) {
      console.warn(`DINOv2 service unavailable (${mlResult.error || mlResult.source}). Explicitly attributing fallback to Gemini Vision...`);
      const geminiResult = await fallbackToGeminiVision(imageBuffer, file.mimetype, location, soilType, language);
      const isMockFallback = geminiResult.source === 'mock';
      const geminiConf = typeof geminiResult.confidence === 'number' ? geminiResult.confidence : null;
      const isUncertain = Boolean(
        geminiResult.needs_review
        || isMockFallback
        || (geminiConf !== null && geminiConf < 0.60)
        || /unknown|पछ|अज्ञात|पहिचान/i.test(geminiResult.diseaseName || '')
      );

      source = isMockFallback ? 'mock' : 'gemini';
      result = {
        ...geminiResult,
        source,
        predictionSource: source,
        advisorySource: source,
        dinov2Status: mlResult.source || 'unavailable',
        confidence: geminiConf,
        top_cosine_similarity: null,
        neighbor_agreement: null,
        needs_review: isUncertain,
        treatmentWithheld: isUncertain,
        treatment: isUncertain
          ? { organic: [], chemical: [] }
          : (geminiResult.treatment || { organic: [], chemical: [] }),
        warning: isUncertain
          ? (WITHHELD_NOTICES[lang] || WITHHELD_NOTICES.en)
          : 'Prediction generated directly by Gemini Vision because the local DINOv2 service was unavailable.',
      };
    } else {
      console.log(
        `DINOv2 prediction: "${mlResult.predicted_class}" | sim=${mlResult.top_cosine_similarity} | agree=${mlResult.neighbor_agreement} | needs_review=${mlResult.needs_review}`
      );
      source = 'dinov2';
      const isUncertain = Boolean(mlResult.needs_review);

      const enrichedData = await getTreatmentDetails(
        mlResult.predicted_class,
        location,
        soilType,
        language,
        { includePrescriptions: !isUncertain }
      );

      // Withhold BOTH organic and chemical treatment prescriptions whenever uncertain
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
        // Never map neighbor agreement or cosine similarity into confidence
        confidence: null,
        top_cosine_similarity: mlResult.top_cosine_similarity,
        neighbor_agreement: mlResult.neighbor_agreement,
        needs_review: isUncertain,
        treatmentWithheld: isUncertain,
        matches: mlResult.matches || [],
        warning: combinedWarning,
        confidence_type: mlResult.confidence_type || 'retrieval_similarity_and_neighbor_consensus_not_calibrated_probability',
        treatment,
        source: 'dinov2',
        predictionSource: 'dinov2',
        advisorySource: enrichedData.advisorySource || 'gemini',
      };
    }

    // STEP 3: Persist Analysis in MongoDB (skip cleanly if DB is not connected, e.g. unit tests)
    let savedAnalysis = null;
    if (CropAnalysis.db?.readyState === 1) {
      try {
        savedAnalysis = await CropAnalysis.create({
          userId: req.user?._id || 'anonymous',
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
          source,
        });
      } catch (dbErr) {
        console.warn('Could not persist crop analysis to database:', dbErr.message);
      }
    }

    res.json({
      source,
      ...result,
      language,
      _id: savedAnalysis?._id || `temp_${Date.now()}`,
      id: savedAnalysis?._id || `temp_${Date.now()}`,
    });
  } catch (error) {
    console.error('Crop Analysis Flow Error:', error);
    res.status(500).json({ error: 'Internal server error during crop analysis' });
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
