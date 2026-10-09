import { Language, LoanAdvisorResult } from "./types";

const getLangName = (lang: Language) => {
  const map: Record<string, string> = {
    en: "English",
    hi: "Hindi (Devanagari script)",
    mr: "Marathi (Devanagari script)",
    pa: "Punjabi (Gurmukhi script)",
    sa: "Sanskrit (Devanagari script)",
  };
  return map[lang] || "English";
};

// ✅ Common error message (shown in UI)
const DEFAULT_AI_ERROR = "AI service is temporarily unavailable";

// ✅ Convert Gemini error to clean UI message
const normalizeGeminiError = (raw: any): string => {
  const msg =
    raw?.error?.message ||
    raw?.message ||
    (typeof raw === "string" ? raw : "") ||
    DEFAULT_AI_ERROR;

  // Helpful mappings (more user-friendly)
  if (msg.toLowerCase().includes("api key")) return "AI key is missing or invalid. Please check server settings.";
  if (msg.toLowerCase().includes("permission")) return "AI permission issue. Please verify API access.";
  if (msg.toLowerCase().includes("quota")) return "AI quota exceeded. Please try again later.";
  if (msg.toLowerCase().includes("not found") && msg.toLowerCase().includes("model"))
    return "AI model not available. Please update model name in server.";
  if (msg.toLowerCase().includes("method not allowed"))
    return "AI endpoint expects POST request. Please try again.";

  return msg || DEFAULT_AI_ERROR;
};

// ✅ Safe extraction of Gemini response text
const extractGeminiText = (data: any): string => {
  const text =
    data?.candidates?.[0]?.content?.parts?.map((p: any) => p?.text || "").join("") ||
    data?.text || // in case we return {text:"..."} later
    "";

  return (text || "").trim();
};

// ✅ Safe JSON parsing with fallback
const safeParseJson = (text: string) => {
  try {
    return JSON.parse(text);
  } catch (err) {
    // Sometimes Gemini returns JSON inside ```json ... ```
    const cleaned = text
      .replace(/```json/gi, "")
      .replace(/```/g, "")
      .trim();

    try {
      return JSON.parse(cleaned);
    } catch {
      throw new Error("AI returned invalid JSON. Please try again.");
    }
  }
};

// ✅ Central Gemini Caller (SO IMPORTANT)
const callGemini = async (payload: Record<string, any>) => {
  const res = await fetch("/api/gemini", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });

  let data: any = null;

  try {
    data = await res.json();
  } catch (e) {
    // Response not JSON (rare)
    throw new Error(DEFAULT_AI_ERROR);
  }

  // If Gemini server returned error status, show clean message
  if (!res.ok) {
    throw new Error(normalizeGeminiError(data));
  }

  // If Gemini returned {error:{...}} with 200 (also possible)
  if (data?.error) {
    throw new Error(normalizeGeminiError(data));
  }

  return data;
};

/**
 * ✅ CROP DISEASE ANALYSIS (Image + JSON)
 */
export const analyzeCropDisease = async (
  imageBase64: string,
  location: string,
  soilType: string,
  language: Language = "en"
) => {
  const targetLang = getLangName(language);

  const prompt = `Act as an expert Plant Pathologist. Analyze this crop image from ${location} with ${soilType} soil.
Identify crop type and disease.

MANDATORY: REPLY ENTIRELY IN ${targetLang}.
STRICT RULE: Do not use Markdown in text fields. Use plain text only.

Return JSON exactly:
{
  "cropName": string,
  "diseaseName": string,
  "confidence": number,
  "summary": string,
  "severity": "Low" | "Medium" | "High",
  "treatment": { "organic": string[], "chemical": string[] },
  "prevention": string[],
  "source": "gemini"
}`;

  const data = await callGemini({
    prompt,
    imageBase64,
    mimeType: "image/jpeg",
    responseMimeType: "application/json",
    generationConfig: { temperature: 0.1 },
  });

  const text = extractGeminiText(data);
  if (!text) throw new Error(DEFAULT_AI_ERROR);

  return safeParseJson(text);
};

/**
 * ✅ LIVESTOCK ANALYSIS (Image + JSON)
 */
export const analyzeLivestockDisease = async (
  imageBase64: string,
  animalType: string,
  language: Language = "en"
) => {
  const targetLang = getLangName(language);

  const prompt = `Veterinary Expert. Analyze this ${animalType} health.

MANDATORY: REPLY ENTIRELY IN ${targetLang}.
STRICT RULE: Do not use Markdown in text fields. Use plain text only.

Return JSON:
{
  "conditionName": string,
  "confidence": string,
  "summary": string,
  "symptoms": string[],
  "treatment": string[],
  "prevention": string[],
  "vetAdvice": string
}`;

  const data = await callGemini({
    prompt,
    imageBase64,
    mimeType: "image/jpeg",
    responseMimeType: "application/json",
    generationConfig: { temperature: 0.1 },
  });

  const text = extractGeminiText(data);
  if (!text) throw new Error(DEFAULT_AI_ERROR);

  return safeParseJson(text);
};

/**
 * ✅ CHATBOT ASSISTANT (Text)
 */
export const getGeminiChatResponse = async (
  message: string,
  mode: string,
  language: Language
) => {
  const targetLang = getLangName(language);

  const systemInstruction = `Agriculture AI Assistant KissanSevaAI.
Mode: ${mode}.

MANDATORY: REPLY ONLY IN ${targetLang}.
STRICT RULES:
- DO NOT use Markdown formatting
- No bold, no italics, no bullet stars, no hashtags
- Use simple numbering (1., 1.1) and colons for headings
- Output plain text only`;

  const data = await callGemini({
    prompt: message,
    systemInstruction,
  });

  const reply = extractGeminiText(data);

  if (!reply) throw new Error(DEFAULT_AI_ERROR);
  return reply;
};

/**
 * ✅ MARKET RECOMMENDATIONS (Text)
 */
export const getMarketRecommendations = async (form: any, language: Language) => {
  const targetLang = getLangName(language);

  const prompt = `Market Strategist. Analyze farm:
${JSON.stringify(form)}

MANDATORY: REPLY ENTIRELY IN ${targetLang}.
STRICT RULES: DO NOT use Markdown formatting. No **, no *, no bullet stars, no # headings.
Use plain text only. Use simple numbering like 1., 1.1 and colons for headings.`;

  const data = await callGemini({ prompt });

  const reply = extractGeminiText(data);
  if (!reply) throw new Error(DEFAULT_AI_ERROR);

  return reply;
};

/**
 * ✅ TRADER & BUYERS ADVISORY (Text)
 */
export const getTraderAdvisory = async (query: string, language: Language) => {
  const targetLang = getLangName(language);

  const prompt = `As a Trading Expert for Indian Farmers, respond to:
"${query}"

Focus on market trends, reliable buyer practices, and negotiation strategies.
MANDATORY: REPLY ENTIRELY IN ${targetLang}.
STRICT RULES: NO Markdown. Plain text. Simple numbering only.`;

  const data = await callGemini({ prompt });

  const reply = extractGeminiText(data);
  if (!reply) throw new Error(DEFAULT_AI_ERROR);

  return reply;
};

/**
 * ✅ IOT DATA INTERPRETATION (Text)
 */
export const getIoTInsights = async (sensorData: any, language: Language) => {
  const targetLang = getLangName(language);

  const prompt = `Interpret these farm sensor readings:
${JSON.stringify(sensorData)}

Give 3 concise actionable points.
MANDATORY: REPLY ENTIRELY IN ${targetLang}.
STRICT RULES: No Markdown. Use numbering (1., 2., 3.).`;

  const data = await callGemini({ prompt });

  const reply = extractGeminiText(data);
  if (!reply) throw new Error(DEFAULT_AI_ERROR);

  return reply;
};

/**
 * ✅ GOVERNMENT SCHEME RECOMMENDATIONS (Text)
 */
export const getSchemeRecommendations = async (form: any, language: Language) => {
  const targetLang = getLangName(language);

  const prompt = `Gov Schemes Expert. Recommend 3-5 schemes for:
${JSON.stringify(form)}

MANDATORY: REPLY ENTIRELY IN ${targetLang}.
STRICT RULES: No Markdown. Plain text. Simple numbering. Use colons for titles.`;

  const data = await callGemini({ prompt });

  const reply = extractGeminiText(data);
  if (!reply) throw new Error(DEFAULT_AI_ERROR);

  return reply;
};

/**
 * ✅ CARBON CREDIT ESTIMATION (Text)
 */
export const estimateCarbonCredits = async (form: any, language: Language) => {
  const targetLang = getLangName(language);

  const prompt = `Carbon Credit Consultant. Estimate credits for:
${JSON.stringify(form)}

MANDATORY: REPLY ENTIRELY IN ${targetLang}.
STRICT RULES: No Markdown. Plain text. Simple numbering.`;

  const data = await callGemini({ prompt });

  const reply = extractGeminiText(data);
  if (!reply) throw new Error(DEFAULT_AI_ERROR);

  return reply;
};

/**
 * ✅ LOAN ADVISOR (JSON)
 */
export const getLoanAdvice = async (
  form: any,
  language: Language
): Promise<LoanAdvisorResult> => {
  const targetLang = getLangName(language);

  const prompt = `Indian Agri Loan Advisor. Analyze profile:
${JSON.stringify(form)}

MANDATORY: ALL JSON VALUES IN ${targetLang}.
STRICT RULE: No Markdown in text strings.
Return JSON matching schema strictly.`;

  const data = await callGemini({
    prompt,
    responseMimeType: "application/json",
    generationConfig: { temperature: 0.1 },
  });

  const text = extractGeminiText(data);
  if (!text) throw new Error(DEFAULT_AI_ERROR);

  return safeParseJson(text) as LoanAdvisorResult;
};

/**
 * ✅ VOICE FIELD EXTRACTION (JSON)
 */
export const extractFieldsFromSpeech = async (
  text: string,
  fields: string[],
  language: Language
) => {
  const targetLang = getLangName(language);

  const prompt = `You are a data extractor.

Extract values for these keys:
[${fields.join(", ")}]

From this spoken text:
"${text}"

Language context: ${targetLang}

Instructions:
- If a value is missing, return null
- If soil type: normalize to one of: "Sandy", "Loamy", "Clay", "Silty"
- Return JSON object with ONLY these keys: ${fields.join(", ")}.`;

  const data = await callGemini({
    prompt,
    responseMimeType: "application/json",
    generationConfig: { temperature: 0.1 },
  });

  const replyText = extractGeminiText(data);
  if (!replyText) throw new Error(DEFAULT_AI_ERROR);

  return safeParseJson(replyText);
};
