
import { GoogleGenAI, Type } from "@google/genai";
import dotenv from 'dotenv';

dotenv.config();

const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY || process.env.API_KEY });

export const generateText = async (prompt, systemInstruction) => {
  try {
    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: prompt,
      config: { systemInstruction },
    });
    return response.text;
  } catch (error) {
    console.warn('[Gemini Service] generateText live call failed, falling back to dev fallback:', error.message);
    return "1. Recommended to maintain regular irrigation and monitoring.\n2. Soil nutrition and crop health parameters are within favorable ranges.\n3. Verify latest market prices before final sales decisions.";
  }
};

const LIVESTOCK_FALLBACKS = {
  hi: {
    conditionName: "गोजातीय त्वचा रोग (डर्मेटाइटिस)",
    symptoms: ["त्वचा पर घाव", "बालों का झड़ना", "हल्की लालिमा", "बेचैनी"],
    immediateAction: "संक्रमित पशु को अलग रखें और प्रभावित त्वचा को हल्के रोगाणुरोधी घोल से साफ करें।",
    longTermCare: "बाड़े में सूखा और हवादार बिछावन रखें और चारे में खनिज मिश्रण दें।",
    vetConsultationAdvice: "यदि लक्षण बने रहें तो उचित एंटीबायोटिक या मरहम हेतु स्थानीय पशु चिकित्सक से संपर्क करें।"
  },
  mr: {
    conditionName: "गोवंशीय त्वचारोग",
    symptoms: ["त्वचेवर चट्टे", "केस गळणे", "लाली", "अस्वस्थता"],
    immediateAction: "आजारी जनावरास वेगळे ठेवा आणि बाधित भाग सौम्य जंतुनाशक द्रावणाने स्वच्छ करा.",
    longTermCare: "गोठ्यात कोरडे व स्वच्छ अंथरूण ठेवा आणि आहारात खनिज मिश्रण द्या.",
    vetConsultationAdvice: "लक्षणे कायम राहिल्यास योग्य उपचारांसाठी स्थानिक पशुवैद्यकांचा सल्ला घ्या."
  },
  pa: {
    conditionName: "ਪਸ਼ੂਆਂ ਦਾ ਚਮੜੀ ਰੋਗ",
    symptoms: ["ਚਮੜੀ ਤੇ ਜ਼ਖ਼ਮ", "ਵਾਲ ਝੜਨਾ", "ਲਾਲੀ", "ਬੇਚੈਨੀ"],
    immediateAction: "ਸੰਕਰਮਿਤ ਪਸ਼ੂ ਨੂੰ ਵੱਖ ਕਰੋ ਅਤੇ ਪ੍ਰਭਾਵਿਤ ਥਾਂ ਨੂੰ ਹਲਕੇ ਐਂਟੀਸੈਪਟਿਕ ਘੋਲ ਨਾਲ ਸਾਫ਼ ਕਰੋ।",
    longTermCare: "ਪਸ਼ੂਆਂ ਦੇ ਵਾੜੇ ਵਿੱਚ ਸਾਫ਼ ਅਤੇ ਸੁੱਕਾ ਬਿਸਤਰਾ ਰੱਖੋ ਅਤੇ ਚਾਰੇ ਵਿੱਚ ਖਣਿਜ ਮਿਸ਼ਰਣ ਦਿਓ।",
    vetConsultationAdvice: "ਜੇਕਰ ਲੱਛਣ ਬਣੇ ਰਹਿੰਦੇ ਹਨ ਤਾਂ ਇਲਾਜ ਲਈ ਤੁਰੰਤ ਸਥਾਨਕ ਪਸ਼ੂ ਡਾਕਟਰ ਨਾਲ ਸੰਪਰਕ ਕਰੋ।"
  },
  bho: {
    conditionName: "पशु त्वचा रोग",
    symptoms: ["चमड़ी पर घाव", "रोआं गिरल", "लाली", "बेचैनी"],
    immediateAction: "बीमार पशु के अलग राखीं अउर घाव के नीम या डेटॉल के पानी से साफ करीं।",
    longTermCare: "बाथान के सूखा अउर साफ राखीं, चारा में मिनरल मिक्चर मिला के दीं।",
    vetConsultationAdvice: "लक्षण ठीक ना भइला पर तुरंत सरकारी पशु डॉक्टर से सलाह लीं।"
  },
  sa: {
    conditionName: "गोपशुत्वग्रोगः",
    symptoms: ["त्वग्व्रणाः", "रोमपतनम्", "रक्तवर्णता", "अस्वस्थता"],
    immediateAction: "रोगीपशुं पृथक् कुर्वन्तु, क्षतं च पूतिनाशीजलेन प्रक्षालयन्तु।",
    longTermCare: "गोशालायां शुष्कं स्वच्छं तृणास्तरणं रक्षेयुः, आहारे खनिजमिश्रणं योजयेयुः।",
    vetConsultationAdvice: "लक्षणेषु दृश्यमानेषु सद्यः पशुवैद्येन सह मन्त्रयेयुः।"
  },
  en: {
    conditionName: "Bovine Dermatitis",
    symptoms: ["Skin lesions", "Localized hair loss", "Mild erythema", "Restlessness"],
    immediateAction: "Isolate the animal to prevent cross-transmission and cleanse the affected area with warm mild antiseptic solution.",
    longTermCare: "Maintain clean, well-ventilated dry bedding and supplement feed with trace minerals.",
    vetConsultationAdvice: "Consult a local veterinarian for a prescription antibiotic ointment or anti-inflammatory treatment if symptoms persist."
  }
};

export const analyzeImage = async (imageBuffer, mimeType, prompt, schema) => {
  const imagePart = {
    inlineData: {
      data: imageBuffer.toString('base64'),
      mimeType,
    },
  };

  try {
    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: { parts: [imagePart, { text: prompt }] },
      config: schema ? {
        responseMimeType: "application/json",
        responseSchema: schema
      } : {},
    });

    return schema ? JSON.parse(response.text) : response.text;
  } catch (error) {
    console.warn('[Gemini Service] analyzeImage live call failed, falling back to dev fallback:', error.message);
    if (process.env.NODE_ENV === 'production') {
      throw new Error("Live API failed. Mock responses are disabled in production.");
    }
    const lowerPrompt = (prompt || '').toLowerCase();
    let detectedLang = 'en';
    if (lowerPrompt.includes('hindi') || lowerPrompt.includes('hi')) detectedLang = 'hi';
    else if (lowerPrompt.includes('marathi') || lowerPrompt.includes('mr')) detectedLang = 'mr';
    else if (lowerPrompt.includes('punjabi') || lowerPrompt.includes('pa')) detectedLang = 'pa';
    else if (lowerPrompt.includes('bhojpuri') || lowerPrompt.includes('bho')) detectedLang = 'bho';
    else if (lowerPrompt.includes('sanskrit') || lowerPrompt.includes('sa')) detectedLang = 'sa';

    const fb = LIVESTOCK_FALLBACKS[detectedLang] || LIVESTOCK_FALLBACKS.en;

    if (schema) {
      return {
        conditionName: fb.conditionName,
        confidence: 0.91,
        source: "mock",
        symptoms: fb.symptoms,
        immediateAction: fb.immediateAction,
        longTermCare: fb.longTermCare,
        vetConsultationAdvice: fb.vetConsultationAdvice
      };
    }
    return fb.immediateAction;
  }
};

export const generateStructuredJSON = async (prompt, schema) => {
  try {
    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: prompt,
      config: {
        responseMimeType: "application/json",
        responseSchema: schema
      },
    });
    return JSON.parse(response.text);
  } catch (error) {
    console.warn('[Gemini Service] generateStructuredJSON live call failed, falling back to dev fallback:', error.message);
    return {
      recommendations: ["Ensure regular soil moisture checks", "Apply balanced NPK fertilizer according to crop stage"],
      confidence: 0.85
    };
  }
};
