import fs from 'fs';
import { predictCropFromModel, checkModelHealth } from '../services/modelService.js';
import { fallbackToGeminiVision, getTreatmentDetails } from '../services/geminiVisionService.js';
import CropAnalysis from '../models/CropAnalysis.js';

export const analyzeCrop = async (req, res) => {
  try {
    const { location, soilType, language } = req.body;
    const file = req.file;

    if (!file) {
      return res.status(400).json({ error: 'No image provided' });
    }

    const imageBuffer = fs.readFileSync(file.path);

    // STEP 1: Query local DINOv2 ML Service
    const mlResult = await predictCropFromModel(imageBuffer, file.originalname);
    
    let result;
    let source = 'gemini';

    // STEP 2: Evaluate Model Response and Apply Multilingual Enrichment / Fallback
    if (mlResult.is_unknown || mlResult.error || !mlResult.predicted_class) {
      console.log(`DINOv2 prediction unavailable or uncertain (${mlResult.error || 'low confidence'}). Falling back to Gemini Vision...`);
      const geminiResult = await fallbackToGeminiVision(imageBuffer, file.mimetype, location, soilType, language);
      source = 'gemini';
      result = {
        ...geminiResult,
        source: 'gemini',
        needs_review: false,
        warning: 'Analysis generated using cloud multimodal vision advisory.'
      };
    } else {
      console.log(`DINOv2 prediction verified: "${mlResult.predicted_class}" (${(mlResult.neighbor_agreement * 100).toFixed(1)}% agreement). Enriching with multilingual advice...`);
      source = 'dinov2';
      const enrichedData = await getTreatmentDetails(mlResult.predicted_class, location, soilType, language);
      result = {
        ...enrichedData,
        cropName: mlResult.crop || enrichedData.cropName,
        diseaseName: enrichedData.diseaseName || mlResult.condition,
        confidence: mlResult.confidence,
        top_cosine_similarity: mlResult.top_cosine_similarity,
        neighbor_agreement: mlResult.neighbor_agreement,
        needs_review: mlResult.needs_review,
        matches: mlResult.matches,
        warning: mlResult.warning,
        confidence_type: mlResult.confidence_type,
        source: 'dinov2',
      };
    }

    // STEP 3: Persist Analysis in MongoDB
    let savedAnalysis = null;
    try {
      savedAnalysis = await CropAnalysis.create({
        userId: req.user?._id || 'anonymous',
        cropName: result.cropName,
        diseaseName: result.diseaseName,
        confidence: result.confidence,
        top_cosine_similarity: result.top_cosine_similarity,
        neighbor_agreement: result.neighbor_agreement,
        needs_review: Boolean(result.needs_review),
        matches: result.matches || [],
        warning: result.warning,
        severity: result.severity,
        summary: result.summary,
        treatment: result.treatment,
        prevention: result.prevention,
        imagePath: file.path || 'temp_path',
        source
      });
    } catch (dbErr) {
      console.warn('Could not persist crop analysis to database:', dbErr.message);
    }

    res.json({ 
      source, 
      ...result, 
      language,
      _id: savedAnalysis?._id || `temp_${Date.now()}`,
      id: savedAnalysis?._id || `temp_${Date.now()}` 
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
