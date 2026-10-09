
import { generateStructuredJSON } from '../services/geminiService.js';
import { Type } from "@google/genai";

const langMap = {
  en: 'English',
  hi: 'Hindi',
  mr: 'Marathi',
  pa: 'Punjabi',
  sa: 'Sanskrit'
};

export const extractFields = async (req, res) => {
  try {
    const { text, fields, language } = req.body;

    if (!text || !fields || !Array.isArray(fields) || fields.length === 0) {
      return res.status(400).json({ error: 'text (string) and fields (non-empty string array) are required' });
    }

    // Build dynamic schema from requested fields
    const properties = {};
    for (const field of fields) {
      properties[field] = { type: Type.STRING, nullable: true };
    }

    const schema = {
      type: Type.OBJECT,
      properties,
      required: fields
    };

    const langName = langMap[language] || 'English';

    const prompt = `You are a data extractor. Extract values for these keys from the spoken text below.
Keys: [${fields.join(', ')}]
Spoken text: "${text}"
Language context: ${langName}
Rules:
- If a value is missing or unclear, return null for that key
- If the key is soil type, normalize to one of: "Sandy", "Loamy", "Clay", "Silty"
- Return ONLY the requested keys with extracted values`;

    const result = await generateStructuredJSON(prompt, schema);
    res.json({ extractedFields: result });
  } catch (error) {
    console.error('Field Extraction Error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
};
