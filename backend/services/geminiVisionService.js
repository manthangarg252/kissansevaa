
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
  bho: 'Bhojpuri (Devanagari script)',
  mr: 'Marathi (Devanagari script)',
  pa: 'Punjabi (Gurmukhi script)',
  sa: 'Sanskrit (Devanagari script)'
};

const withTimeout = (promise, ms = 6000) => {
  return Promise.race([
    promise,
    new Promise((_, reject) => setTimeout(() => reject(new Error(`Timeout after ${ms}ms`)), ms))
  ]);
};

const CROP_FALLBACKS = {
  hi: {
    cropDefault: "गेहूं",
    diseaseDefault: "पीला रतुआ",
    summary: (d, c, loc) => `${loc || 'खेत'} पर ${c} में ${d} के लक्षण पाए गए हैं। समय पर उचित प्रबंधन और उपचार की सलाह दी जाती है।`,
    severity: "Medium",
    organic: ["नीम के बीज के अर्क (5%) का छिड़काव करें", "ट्राइकोडर्मा विरिडे जैव-कवकनाशी 5 ग्राम/लीटर पानी में प्रयोग करें"],
    chemical: ["प्रोपिकोनाजोल 25% ईसी @ 1 मिली/लीटर पानी का छिड़काव करें", "मैंकोजेब 75% डब्ल्यूपी @ 2 ग्राम/लीटर का प्रयोग करें"],
    prevention: ["रोग प्रतिरोधी किस्मों की बुवाई करें", "खेत के किनारों को साफ रखें", "संतुलित एनपीके उर्वरक का उपयोग करें"]
  },
  mr: {
    cropDefault: "गहू",
    diseaseDefault: "पिवळा तांबेरा",
    summary: (d, c, loc) => `${loc || 'शेतावर'} ${c} पिकामध्ये ${d} रोगाची लक्षणे आढळली आहेत. योग्य व्यवस्थापनाचा सल्ला दिला जातो.`,
    severity: "Medium",
    organic: ["कडुलिंब अर्क (5%) फवारणी करा", "ट्रायकोडर्मा व्हिरिडी 5 ग्रॅम/लिटर पाण्यात वापरा"],
    chemical: ["प्रोपिकोनाझोल 25% ईसी @ 1 मिली/लिटर पाण्यात फवारणी करा", "मॅन्कोझेब 75% डब्ल्यूपी @ 2 ग्रॅम/लिटर वापरा"],
    prevention: ["रोगप्रतिकारक वाणांचा वापर करा", "शेतातील स्वच्छता राखा", "संतुलित खतांचा वापर करा"]
  },
  pa: {
    cropDefault: "ਕਣਕ",
    diseaseDefault: "ਪੀਲੀ ਕੁੰਗੀ",
    summary: (d, c, loc) => `${loc || 'ਖੇਤ'} ਵਿੱਚ ${c} ਦੀ ਫ਼ਸਲ ਤੇ ${d} ਦੇ ਲੱਛਣ ਮਿਲੇ ਹਨ। ਢੁਕਵੇਂ ਪ੍ਰਬੰਧਨ ਦੀ ਸਲਾਹ ਦਿੱਤੀ ਜਾਂਦੀ ਹੈ।`,
    severity: "Medium",
    organic: ["ਨੀਮ ਦੇ ਬੀਜਾਂ ਦੇ ਅਰਕ (5%) ਦਾ ਛਿੜਕਾਅ ਕਰੋ", "ਟ੍ਰਾਈਕੋਡਰਮਾ ਵਿਰਿਡੇ 5 ਗ੍ਰਾਮ/ਲੀਟਰ ਪਾਣੀ ਵਿੱਚ ਵਰਤੋ"],
    chemical: ["ਪ੍ਰੋਪੀਕੋਨਾਜ਼ੋਲ 25% ਈਸੀ @ 1 ਮਿ.ਲੀ./ਲੀਟਰ ਪਾਣੀ ਵਿੱਚ ਛਿੜਕਾਅ ਕਰੋ", "ਮੈਨਕੋਜ਼ੇਬ 75% ਡਬਲਯੂਪੀ @ 2 ਗ੍ਰਾਮ/ਲੀਟਰ ਵਰਤੋ"],
    prevention: ["ਬਿਮਾਰੀ ਰੋਧਕ ਕਿਸਮਾਂ ਦੀ ਬਿਜਾਈ ਕਰੋ", "ਖੇਤ ਦੀ ਸਫ਼ਾਈ ਰੱਖੋ", "ਸੰਤੁਲਿਤ ਖਾਦਾਂ ਦੀ ਵਰਤੋਂ ਕਰੋ"]
  },
  bho: {
    cropDefault: "गेहूं",
    diseaseDefault: "पीयर रतुआ",
    summary: (d, c, loc) => `${loc || 'खेत'} में ${c} के फसल पर ${d} के लक्षण मिलल बा। उचित देखभाल के सलाह दिहल जाता।`,
    severity: "Medium",
    organic: ["नीम के पानी (5%) के छिड़काव करीं", "ट्राइकोडर्मा विरिडे 5 ग्राम/लीटर पानी में डाल के छिड़कीं"],
    chemical: ["प्रोपिकोनाजोल 25% ईसी @ 1 मिली/लीटर पानी के छिड़काव करीं", "मैंकोजेब 75% डब्ल्यूपी @ 2 ग्राम/लीटर छिड़कीं"],
    prevention: ["रोग प्रतिरोधी बीज बोईं", "खेत के मेड़ साफ राखीं", "संतुलित खाद के इस्तेमाल करीं"]
  },
  sa: {
    cropDefault: "गोधूमः",
    diseaseDefault: "पीतरोगः",
    summary: (d, c, loc) => `${loc || 'क्षेत्रे'} ${c} सस्ये ${d} रोगस्य लक्षणानि दृष्टानि। समुचितोपचारस्य परामर्शः दीयते।`,
    severity: "Medium",
    organic: ["निम्बबीजकषायस्य (५%) सेचनं कुर्वन्तु", "ट्राइकोडर्मा विरिडे ५ ग्रा/ली जले योजयन्तु"],
    chemical: ["प्रोपिकोनाजोल २५% ईसी १ मि.ली/ली जले सेचयेत्", "मैन्कोजेब ७५% डब्ल्यूपी २ ग्रा/ली प्रयुञ्जीत"],
    prevention: ["रोगप्रतिरोधिनां बीजानां वपनं कुर्वन्तु", "क्षेत्रस्वच्छतां रक्षन्तु", "सन्तुलितोर्वरकाणां प्रयोगं कुर्वन्तु"]
  },
  en: {
    cropDefault: "Wheat",
    diseaseDefault: "Yellow Rust",
    summary: (d, c, loc) => `Detected symptoms of ${d} on ${c} at ${loc || 'Farm'}. Immediate balanced management advised.`,
    severity: "Medium",
    organic: ["Spray neem seed kernel extract (5%)", "Apply Trichoderma viride bio-fungicide @ 5g/L"],
    chemical: ["Spray Propiconazole 25% EC @ 1ml/L water", "Apply Mancozeb 75% WP @ 2g/L"],
    prevention: ["Plant resistant cultivars", "Maintain clean field borders", "Apply balanced NPK fertilization"]
  }
};

export const fallbackToGeminiVision = async (imageBuffer, mimeType, location, soilType, language) => {
  const normLang = (language || 'en').toLowerCase().trim();
  const targetLang = langMap[normLang] || 'English';
  const fb = CROP_FALLBACKS[normLang] || CROP_FALLBACKS.en;
  
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
    const response = await withTimeout(ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: { parts: [imagePart, { text: prompt }] },
      config: {
        responseMimeType: "application/json",
        responseSchema: cropResponseSchema,
        temperature: 0.1
      },
    }), 6000);

    return {
      ...JSON.parse(response.text),
      source: 'gemini',
    };
  } catch (error) {
    console.warn('[Gemini Vision] Live API call failed, falling back to dev fallback:', error.message);
    if (process.env.NODE_ENV === 'production') {
      throw new Error("Live API failed. Mock responses are disabled in production.");
    }
    return {
      cropName: fb.cropDefault,
      diseaseName: fb.diseaseDefault,
      confidence: null,
      needs_review: true,
      source: "mock",
      summary: fb.summary(fb.diseaseDefault, fb.cropDefault, location),
      severity: fb.severity,
      treatment: {
        organic: [],
        chemical: []
      },
      prevention: fb.prevention
    };
  }
};

export const getTreatmentDetails = async (predictedClass, location, soilType, language, options = {}) => {
  const { includePrescriptions = true } = options;
  const normLang = (language || 'en').toLowerCase().trim();
  const targetLang = langMap[normLang] || 'English';
  const fb = CROP_FALLBACKS[normLang] || CROP_FALLBACKS.en;

  const parts = predictedClass.includes('/') ? predictedClass.split('/') : predictedClass.split('___');
  const rawCrop = parts[0] || 'Crop';
  const rawDisease = (parts[1] || parts[0]).replace(/_/g, ' ');

  const prompt = `The local ML model detected a condition: "${predictedClass}".
    As a farming expert, identify the crop type and the disease from this name.
    ${includePrescriptions
      ? 'Provide a summary, severity, organic/chemical treatments, and prevention steps for this condition.'
      : 'Diagnosis is UNCERTAIN / UNVERIFIED. Provide ONLY a brief educational summary, possible severity, and non-prescriptive general preventive hygiene (such as consulting KVK or retaking a clear photo). Return empty arrays [] for both organic and chemical treatments.'}

    Location: ${location || 'Farm'}, Soil: ${soilType || 'Not specified'}.

    ### LANGUAGE REQUIREMENT (CRITICAL)
    YOU MUST GENERATE ALL TEXT CONTENT IN ${targetLang}.
    DO NOT USE ENGLISH.
    TRANSLATE THE CROP NAME AND THE DISEASE NAME INTO ${targetLang} AS WELL.

    Return JSON only using the specified schema.`;

  try {
    const response = await withTimeout(ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: prompt,
      config: {
        responseMimeType: "application/json",
        responseSchema: cropResponseSchema,
        temperature: 0.1
      },
    }), 6000);

    const parsed = JSON.parse(response.text);
    return {
      ...parsed,
      confidence: null, // Never overwrite DINOv2 attribution with synthetic advisory confidence
      advisorySource: 'gemini',
      treatment: includePrescriptions ? (parsed.treatment || { organic: [], chemical: [] }) : { organic: [], chemical: [] },
    };
  } catch (error) {
    console.warn('[Gemini Vision] getTreatmentDetails live call failed, falling back to local template:', error.message);
    return {
      cropName: rawCrop,
      diseaseName: rawDisease,
      confidence: null,
      advisorySource: 'local_fallback',
      summary: fb.summary(rawDisease, rawCrop, location),
      severity: fb.severity,
      treatment: includePrescriptions
        ? { organic: fb.organic, chemical: fb.chemical }
        : { organic: [], chemical: [] },
      prevention: fb.prevention
    };
  }
};
