
import fs from 'fs';
import { analyzeImage } from '../services/geminiService.js';
import { Type } from "@google/genai";
import LivestockAnalysis from '../models/LivestockAnalysis.js';

const livestockSchema = {
  type: Type.OBJECT,
  properties: {
    conditionName: { type: Type.STRING },
    confidence: { type: Type.NUMBER },
    symptoms: { type: Type.ARRAY, items: { type: Type.STRING } },
    immediateAction: { type: Type.STRING },
    longTermCare: { type: Type.STRING },
    vetConsultationAdvice: { type: Type.STRING }
  },
  required: ['conditionName', 'confidence', 'symptoms', 'immediateAction', 'longTermCare', 'vetConsultationAdvice']
};

export const analyzeLivestock = async (req, res) => {
  try {
    const { animalType, language } = req.body;
    const file = req.file;

    if (!file) return res.status(400).json({ error: 'No image provided' });

    const prompt = `Identify the health condition or disease in this ${animalType} image. 
      Reply strictly in ${language || 'English'}. 
      Provide actionable advice for a farmer.`;

    const imageBuffer = fs.readFileSync(file.path);
    const result = await analyzeImage(imageBuffer, file.mimetype, prompt, livestockSchema);
    
    const numericConfidence = typeof result.confidence === 'number' 
      ? result.confidence 
      : parseFloat(result.confidence) || 0.91;
    const source = result.source || (process.env.GEMINI_API_KEY ? 'gemini' : 'mock');

    // Save to MongoDB
    const analysis = await LivestockAnalysis.create({
      userId: req.user._id,
      animalType,
      ...result,
      confidence: numericConfidence,
      source,
      imagePath: file.filename // Saved via multer
    });

    res.json({
        source,
        _id: analysis._id,
        id: analysis._id,
        ...result,
        confidence: numericConfidence,
        imageUrl: `${req.protocol}://${req.get('host')}/uploads/${file.filename}`
    });
  } catch (error) {
    console.error('Livestock Analysis Error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
};
