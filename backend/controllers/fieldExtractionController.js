import { callGeminiStructured, sendAiErrorResponse } from '../services/geminiService.js';
import { Type } from '@google/genai';

const langMap = {
  en: 'English',
  hi: 'Hindi',
  bho: 'Bhojpuri',
  mr: 'Marathi',
  pa: 'Punjabi',
  sa: 'Sanskrit',
};

export const extractFields = async (req, res) => {
  try {
    const { text, fields, language = 'en' } = req.body || {};

    if (!text || !fields || !Array.isArray(fields) || fields.length === 0) {
      return res.status(400).json({ error: 'text (string) and fields (non-empty string array) are required' });
    }

    const properties = {};
    for (const field of fields) {
      properties[field] = { type: Type.STRING, nullable: true };
    }

    const schema = {
      type: Type.OBJECT,
      properties,
      required: fields,
    };

    const langName = langMap[(language || 'en').toLowerCase().trim()] || 'English';
    const prompt = `You are a data extractor. Extract values for these keys from the spoken text below.
Keys: [${fields.join(', ')}]
Spoken text: "${text}"
Language context: ${langName}
Rules:
- If a value is missing or unclear, return null for that key
- If the key is soilType, normalize to one of: "Sandy", "Loamy", "Clay", "Silty", "Black"
- Return ONLY the requested keys with extracted values`;

    const aiRes = await callGeminiStructured({
      prompt,
      schema,
      userId: req.user?._id,
      feature: 'voice_field_extraction',
    });

    res.json({
      extractedFields: aiRes.data,
      aiAvailable: true,
      requestId: aiRes.requestId,
    });
  } catch (error) {
    return sendAiErrorResponse(res, error);
  }
};
