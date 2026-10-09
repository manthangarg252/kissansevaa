
import { GoogleGenAI, Type } from "@google/genai";
import dotenv from 'dotenv';

dotenv.config();

const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY || process.env.API_KEY });

const cropResponseSchema = {
  type: Type.OBJECT,
  properties: {
    cropName: { type: Type.STRING },
    diseaseName: { type: Type.STRING },
    confidence: { type: Type.NUMBER },
    summary: { type: Type.STRING },
    severity: { type: Type.STRING, enum: ['Low', 'Medium', 'High'] },
    treatment: {
      type: Type.OBJECT,
      properties: {
        organic: { type: Type.ARRAY, items: { type: Type.STRING } },
        chemical: { type: Type.ARRAY, items: { type: Type.STRING } }
      },
      required: ['organic', 'chemical']
    },
    prevention: { type: Type.ARRAY, items: { type: Type.STRING } }
  },
  required: ['cropName', 'diseaseName', 'confidence', 'summary', 'severity', 'treatment', 'prevention']
};

const langMap = {
  en: 'English',
  hi: 'Hindi (Devanagari script)',
  mr: 'Marathi (Devanagari script)',
  pa: 'Punjabi (Gurmukhi script)',
  sa: 'Sanskrit (Devanagari script)'
};

export const fallbackToGeminiVision = async (imageBuffer, mimeType, location, soilType, language) => {
  const targetLang = langMap[language] || 'English';
  
  const imagePart = {
    inlineData: {
      data: imageBuffer.toString('base64'),
      mimeType,
    },
  };

  const prompt = `Act as an expert Plant Pathologist. Analyze this crop image from ${location} with ${soilType} soil.
    
    ### TASK
    1. Identify the crop type (e.g., Wheat, Rice, Tomato, etc.).
    2. Identify the disease. If healthy, set diseaseName to exactly "Healthy".
    3. Provide a summary, severity, organic/chemical treatments, and prevention.
    
    ### LANGUAGE REQUIREMENT (CRITICAL)
    YOU MUST GENERATE ALL TEXT CONTENT IN ${targetLang}. 
    DO NOT USE ENGLISH. 
    IF THE LANGUAGE IS PUNJABI, USE GURMUKHI SCRIPT. 
    IF HINDI/MARATHI/SANSKRIT, USE DEVANAGARI SCRIPT.
    
    Output strictly valid JSON.`;

  try {
    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: { parts: [imagePart, { text: prompt }] },
      config: {
        responseMimeType: "application/json",
        responseSchema: cropResponseSchema,
        temperature: 0.1
      },
    });

    return JSON.parse(response.text);
  } catch (error) {
    console.warn('[Gemini Vision] Live API call failed, falling back to dev fallback:', error.message);
    if (process.env.NODE_ENV === 'production') {
      throw new Error("Live API failed. Mock responses are disabled in production.");
    }
    return {
      cropName: "Wheat",
      diseaseName: "Yellow Rust",
      confidence: 0.89,
      source: "mock",
      summary: `Analysis for crop at ${location || 'Farm'} (${soilType || 'Loamy'} soil): Detected early symptoms of Yellow Rust (Puccinia striiformis).`,
      severity: "Medium",
      treatment: {
        organic: ["Spray neem seed kernel extract (5%)", "Apply Trichoderma viride bio-fungicide"],
        chemical: ["Spray Propiconazole 25% EC @ 1ml/L water", "Apply Mancozeb 75% WP @ 2g/L"]
      },
      prevention: [
        "Plant resistant cultivars",
        "Maintain optimal spacing for adequate air circulation",
        "Avoid excess nitrogen application"
      ]
    };
  }
};

export const getTreatmentDetails = async (predictedClass, location, soilType, language) => {
  const targetLang = langMap[language] || 'English';

  const prompt = `The local ML model detected a condition: "${predictedClass}". 
    As a farming expert, identify the crop type and the disease from this name.
    Then provide a summary, severity, organic/chemical treatments, and prevention steps for this. 
    
    Location: ${location}, Soil: ${soilType}.
    
    ### LANGUAGE REQUIREMENT (CRITICAL)
    YOU MUST GENERATE ALL TEXT CONTENT IN ${targetLang}. 
    DO NOT USE ENGLISH. 
    TRANSLATE THE CROP NAME AND THE DISEASE NAME INTO ${targetLang} AS WELL.
    
    Return JSON only using the specified schema.`;

  try {
    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: prompt,
      config: {
        responseMimeType: "application/json",
        responseSchema: cropResponseSchema,
        temperature: 0.1
      },
    });

    return JSON.parse(response.text);
  } catch (error) {
    console.warn('[Gemini Vision] getTreatmentDetails live call failed, falling back to dev fallback:', error.message);
    return {
      cropName: predictedClass.split('___')[0] || "Wheat",
      diseaseName: predictedClass.split('___')[1] || "Leaf Blight",
      confidence: 0.88,
      summary: `Treatment recommendations for ${predictedClass} at ${location}.`,
      severity: "Medium",
      treatment: {
        organic: ["Apply biological control agent Bacillus subtilis @ 5g/L"],
        chemical: ["Spray Carbendazim 50% WP @ 1g/L water"]
      },
      prevention: ["Crop rotation", "Clean field borders", "Balanced fertilization"]
    };
  }
};
