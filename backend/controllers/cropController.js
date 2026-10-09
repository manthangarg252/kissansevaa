
import fs from 'fs';
import { predictCropFromModel } from '../services/modelService.js';
import { fallbackToGeminiVision, getTreatmentDetails } from '../services/geminiVisionService.js';
import CropAnalysis from '../models/CropAnalysis.js';

export const analyzeCrop = async (req, res) => {
  try {
    const { location, soilType, language } = req.body;
    const file = req.file;

    if (!file) return res.status(400).json({ error: 'No image provided' });

    // STEP 1: Call Local ML Service (Inference)
    const imageBuffer = fs.readFileSync(file.path);
    const mlResult = await predictCropFromModel(imageBuffer, file.originalname);
    
    let result;
    let source = 'model';

    // STEP 2: Confidence Check & Multilingual Enrichment
    if (mlResult.is_unknown || mlResult.confidence < 0.75) {
      console.log('Model uncertain or unknown. Falling back to Gemini Vision...');
      result = await fallbackToGeminiVision(imageBuffer, file.mimetype, location, soilType, language);
      source = result.source || 'gemini';
    } else {
      console.log('Model confident. Enriching with multilingual advice and crop detection...');
      // Use Gemini to detect the crop name and provide details for the ML prediction class
      const enrichedData = await getTreatmentDetails(mlResult.predicted_class, location, soilType, language);
      result = {
        ...enrichedData,
        confidence: mlResult.confidence // Keep the ML model's original confidence
      };
    }

    // STEP 3: Save to Database
    const savedAnalysis = await CropAnalysis.create({
      userId: req.user?._id || 'anonymous',
      cropName: result.cropName,
      diseaseName: result.diseaseName,
      confidence: result.confidence,
      severity: result.severity,
      summary: result.summary,
      treatment: result.treatment,
      prevention: result.prevention,
      imagePath: file.path || 'temp_path',
      source
    });

    res.json({ 
      source, 
      ...result, 
      language,
      _id: savedAnalysis._id,
      id: savedAnalysis._id 
    });

  } catch (error) {
    console.error('Crop Analysis Flow Error:', error);
    res.status(500).json({ error: 'Internal server error' });
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
