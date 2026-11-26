import crypto from 'node:crypto';
import mongoose from 'mongoose';
import User from '../models/User.js';
import CropAnalysis from '../models/CropAnalysis.js';
import CropPlan from '../models/CropPlan.js';
import LivestockAnalysis from '../models/LivestockAnalysis.js';
import { getWeatherForecast } from './weatherService.js';
import { marketService } from './marketDataService.js';
import {
  searchSchemesCatalog,
  evaluateSchemeEligibility,
} from './schemeCatalogService.js';
import {
  normalizeAnimalType,
  evaluateLivestockRedFlags,
  SAFETY_NOTICE,
} from '../controllers/livestockController.js';
import { gatherToolGrounding } from '../controllers/chatController.js';
import { callGeminiText, classifyGeminiError } from './geminiService.js';

export const CANONICAL_TOOLS = [
  'crop_assistance',
  'livestock_questions',
  'weather_forecasts',
  'mandi_prices',
  'government_schemes',
  'general_farming_advice',
];

const TOOL_ALIASES = {
  crop_assistance: 'crop_assistance',
  crop: 'crop_assistance',
  crop_help: 'crop_assistance',
  crop_diagnosis: 'crop_assistance',
  crop_status: 'crop_assistance',
  livestock_questions: 'livestock_questions',
  livestock: 'livestock_questions',
  livestock_triage: 'livestock_questions',
  veterinary: 'livestock_questions',
  animal_health: 'livestock_questions',
  weather_forecasts: 'weather_forecasts',
  weather_forecast: 'weather_forecasts',
  weather_spray_check: 'weather_forecasts',
  weather: 'weather_forecasts',
  mandi_prices: 'mandi_prices',
  mandi_rate_lookup: 'mandi_prices',
  mandi: 'mandi_prices',
  market_prices: 'mandi_prices',
  government_schemes: 'government_schemes',
  scheme_summary: 'government_schemes',
  scheme_eligibility: 'government_schemes',
  schemes: 'government_schemes',
  general_farming_advice: 'general_farming_advice',
  general_advice: 'general_farming_advice',
  general: 'general_farming_advice',
  advice: 'general_farming_advice',
};

const MAX_REPLAY_WINDOW_SECONDS = 300;

function timingSafeStringEqual(a = '', b = '') {
  const bufA = Buffer.from(String(a), 'utf8');
  const bufB = Buffer.from(String(b), 'utf8');
  if (bufA.length === 0 || bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

function normalizePhoneTail(rawPhone = '') {
  const digits = String(rawPhone || '').replace(/\D/g, '');
  if (digits.length < 10) return null;
  return digits.slice(-10);
}

function cleanTextParam(val, maxLen = 400) {
  if (val === null || val === undefined) return '';
  return String(val).trim().slice(0, maxLen);
}

export function normalizeToolName(raw = '') {
  const key = String(raw || '').trim().toLowerCase().replace(/[\s-]+/g, '_');
  return TOOL_ALIASES[key] || null;
}

/**
 * Verifies inbound telephony/Sarvam webhook requests via:
 * 1) HMAC-SHA256 signature header (`X-Sarvam-Signature` / `X-Call-Agent-Signature`) with replay protection, or
 * 2) Constant-time shared secret (`X-Call-Agent-Secret` / `X-Sarvam-Secret` / `Authorization: Bearer ...`).
 */
export function verifyCallAgentAuth(req) {
  const sharedSecret = (process.env.CALL_AGENT_WEBHOOK_SECRET || process.env.SARVAM_WEBHOOK_SECRET || '').trim();
  const signingSecret = (process.env.SARVAM_WEBHOOK_SIGNING_SECRET || sharedSecret).trim();

  if (!sharedSecret && !signingSecret) {
    return {
      ok: false,
      statusCode: 503,
      code: 'not_configured',
      error: 'External call-agent telephony secret (CALL_AGENT_WEBHOOK_SECRET or SARVAM_WEBHOOK_SIGNING_SECRET) is not configured.',
    };
  }

  const providedSignatureRaw =
    req.headers['x-sarvam-signature'] || req.headers['x-call-agent-signature'] || '';
  const timestampHeader =
    req.headers['x-sarvam-timestamp'] || req.headers['x-call-agent-timestamp'] || '';

  if (providedSignatureRaw && signingSecret) {
    const rawBodyStr = Buffer.isBuffer(req.rawBody)
      ? req.rawBody.toString('utf8')
      : JSON.stringify(req.body || {});

    let signedPayload = rawBodyStr;
    if (timestampHeader) {
      const tsNum = Number(timestampHeader);
      const tsMs = tsNum > 1e12 ? tsNum : tsNum * 1000;
      if (!Number.isFinite(tsMs) || Math.abs(Date.now() - tsMs) > MAX_REPLAY_WINDOW_SECONDS * 1000) {
        return {
          ok: false,
          statusCode: 401,
          code: 'expired_timestamp',
          error: 'Webhook request timestamp is invalid or outside the replay window.',
        };
      }
      signedPayload = `${timestampHeader}.${rawBodyStr}`;
    }

    const expectedDigestHex = crypto
      .createHmac('sha256', signingSecret)
      .update(signedPayload)
      .digest('hex');

    const providedHex = String(providedSignatureRaw).replace(/^sha256=/i, '').trim().toLowerCase();
    if (timingSafeStringEqual(providedHex, expectedDigestHex)) {
      return { ok: true, authMethod: 'hmac_sha256' };
    }
    return {
      ok: false,
      statusCode: 401,
      code: 'invalid_signature',
      error: 'Invalid webhook HMAC signature.',
    };
  }

  const authHeader = String(req.headers.authorization || '');
  const bearerToken = /^Bearer\s+(.+)$/i.test(authHeader) ? authHeader.replace(/^Bearer\s+/i, '').trim() : '';
  const providedSecret = String(
    req.headers['x-call-agent-secret'] || req.headers['x-sarvam-secret'] || bearerToken || '',
  ).trim();

  if (sharedSecret && providedSecret && timingSafeStringEqual(providedSecret, sharedSecret)) {
    return { ok: true, authMethod: 'shared_secret' };
  }

  return {
    ok: false,
    statusCode: 401,
    code: 'unauthorized',
    error: 'Missing or invalid call-agent authentication credentials.',
  };
}

async function resolveCallerUser({ callerPhone = '', farmerId = '' }) {
  if (User.db?.readyState !== 1) return null;

  if (farmerId && mongoose.Types.ObjectId.isValid(String(farmerId))) {
    const byId = await User.findById(farmerId)
      .select('name phone state district latitude longitude soilType irrigationType farmSizeAcres primaryCrops languagePreference')
      .lean();
    if (byId) return byId;
  }

  const tail = normalizePhoneTail(callerPhone);
  if (tail) {
    // Allow formatting separators (spaces, hyphens) inside stored E.164 phone numbers like "+91 98765 00000"
    const tailPattern = tail.split('').join('[\\s\\-]*');
    const safeRegex = new RegExp(`${tailPattern}$`);
    const matched = await User.findOne({ phone: { $regex: safeRegex } })
      .select('name phone state district latitude longitude soilType irrigationType farmSizeAcres primaryCrops languagePreference')
      .lean();
    if (matched) return matched;
  }

  return null;
}

export function validateToolPayload(body = {}, routeTool = null) {
  if (body !== null && (typeof body !== 'object' || Array.isArray(body))) {
    return { error: 'Request body must be a JSON object.' };
  }

  const nested =
    body?.parameters && typeof body.parameters === 'object' && !Array.isArray(body.parameters)
      ? body.parameters
      : body?.args && typeof body.args === 'object' && !Array.isArray(body.args)
        ? body.args
        : {};

  const merged = { ...body, ...nested };
  const rawTool = routeTool || merged.tool || merged.intent || merged.action || 'mandi_rate_lookup';
  const tool = normalizeToolName(rawTool);
  if (!tool) {
    return {
      error: `Unsupported call-agent tool "${String(rawTool).slice(0, 64)}". Supported tools: ${CANONICAL_TOOLS.join(', ')}`,
    };
  }

  return {
    tool,
    originalIntent: String(rawTool),
    params: {
      crop: cleanTextParam(merged.crop || merged.cropName || '', 120),
      disease: cleanTextParam(merged.disease || merged.diseaseName || '', 120),
      state: cleanTextParam(merged.state || '', 100),
      location: cleanTextParam(merged.location || merged.district || merged.city || '', 120),
      category: cleanTextParam(merged.category || '', 60),
      language: cleanTextParam(merged.language || 'en', 16).toLowerCase() || 'en',
      callerPhone: cleanTextParam(merged.callerPhone || merged.phone || merged.from || '', 32),
      farmerId: cleanTextParam(merged.farmerId || merged.userId || '', 64),
      animalType: cleanTextParam(merged.animalType || merged.species || '', 60),
      animalAge: cleanTextParam(merged.animalAge || '', 40),
      symptomDuration: cleanTextParam(merged.symptomDuration || merged.duration || '', 40),
      feverStatus: cleanTextParam(merged.feverStatus || merged.fever || 'unknown', 20),
      appetiteStatus: cleanTextParam(merged.appetiteStatus || merged.appetite || 'unknown', 20),
      milkYieldChange: cleanTextParam(merged.milkYieldChange || merged.milkYield || 'unknown', 20),
      lesionsOrUlcers: merged.lesionsOrUlcers === true || merged.lesionsOrUlcers === 'true' || merged.lesions === true,
      symptomsNotes: cleanTextParam(merged.symptomsNotes || merged.symptoms || '', 400),
      farmSize: merged.farmSize ?? merged.farmSizeAcres ?? '',
      soilType: cleanTextParam(merged.soilType || '', 60),
      landType: cleanTextParam(merged.landType || 'owned', 40),
      hasLivestock: Boolean(merged.hasLivestock),
      query: cleanTextParam(merged.query || merged.question || merged.message || '', 500),
    },
  };
}

async function handleCropAssistance(params, farmer) {
  const recentScans =
    farmer?._id && CropAnalysis.db?.readyState === 1
      ? await CropAnalysis.find({ userId: farmer._id }).sort({ createdAt: -1 }).limit(3).lean()
      : [];

  const activePlan =
    farmer?._id && CropPlan.db?.readyState === 1
      ? await CropPlan.findOne({ userId: farmer._id, status: 'active' }).sort({ updatedAt: -1 }).lean()
      : null;

  const targetScan = params.crop
    ? recentScans.find((s) => String(s.cropName || '').toLowerCase().includes(params.crop.toLowerCase())) || recentScans[0]
    : recentScans[0];

  if (targetScan) {
    // Strictly preserve needs_review / treatmentWithheld safety policy over voice telephony!
    const withheld = Boolean(targetScan.needs_review || targetScan.treatmentWithheld);
    const simNote =
      typeof targetScan.top_cosine_similarity === 'number'
        ? `Cosine similarity ${targetScan.top_cosine_similarity.toFixed(2)}.`
        : '';

    const spokenSummary = withheld
      ? `Latest ${targetScan.cropName || 'crop'} scan shows possible ${targetScan.diseaseName}, flagged for manual review (${simNote}). Treatment prescriptions are withheld for safety. Please consult your nearest Krishi Vigyan Kendra agronomist before spraying.`
      : `Latest ${targetScan.cropName || 'crop'} scan matched ${targetScan.diseaseName}. Recommended prevention: ${(targetScan.prevention || []).slice(0, 2).join('; ') || 'monitor field hygiene and verify dosage locally'}.`;

    return {
      tool: 'crop_assistance',
      farmerMatched: Boolean(farmer),
      activeModel: 'dinov2_v1',
      latestScan: {
        cropName: targetScan.cropName,
        diseaseName: targetScan.diseaseName,
        predictionSource: targetScan.predictionSource || targetScan.source || 'dinov2',
        top_cosine_similarity: targetScan.top_cosine_similarity ?? null,
        neighbor_agreement: targetScan.neighbor_agreement ?? null,
        needs_review: Boolean(targetScan.needs_review),
        treatmentWithheld: withheld,
        treatment: withheld ? { organic: [], chemical: [] } : targetScan.treatment,
        prevention: targetScan.prevention || [],
      },
      activePlanCrop: activePlan?.cropName || null,
      spokenSummary,
    };
  }

  const focusCrop = params.crop || farmer?.primaryCrops?.[0] || 'your crop';
  const focusIssue = params.disease || params.symptomsNotes || params.query;
  const spokenSummary = focusIssue
    ? `For ${focusCrop} (${focusIssue}), inspect the underside of affected leaves in daylight and capture a sharp close-up scan in the KissanSevaAI app, or consult your nearest Krishi Vigyan Kendra before applying chemical sprays.`
    : `No recent leaf scan found for ${focusCrop}. Open Crop Disease Detection in the KissanSevaAI app to scan a leaf, or tell me the specific spots, yellowing, or pest symptoms you see.`;

  return {
    tool: 'crop_assistance',
    farmerMatched: Boolean(farmer),
    activeModel: 'dinov2_v1',
    latestScan: null,
    treatmentWithheld: true,
    spokenSummary,
  };
}

async function handleLivestockQuestions(params, farmer) {
  const animalType = normalizeAnimalType(params.animalType || 'Cattle');
  const redFlags = evaluateLivestockRedFlags({
    animalType,
    feverStatus: params.feverStatus,
    appetiteStatus: params.appetiteStatus,
    milkYieldChange: params.milkYieldChange,
    lesionsOrUlcers: params.lesionsOrUlcers,
    symptomsNotes: params.symptomsNotes || params.query,
    symptomDuration: params.symptomDuration,
  });

  const recentCases =
    farmer?._id && LivestockAnalysis.db?.readyState === 1
      ? await LivestockAnalysis.find({ userId: farmer._id }).sort({ createdAt: -1 }).limit(2).lean()
      : [];

  const urgent = Boolean(redFlags.urgentVetEscalation || (!params.symptomsNotes && !params.lesionsOrUlcers && recentCases[0]?.urgentVetEscalation));
  const escalationReason = redFlags.escalationReason || (urgent ? recentCases[0]?.escalationReason || '' : '');

  const spokenSummary = urgent
    ? `Urgent veterinary alert for ${animalType}. ${escalationReason} Isolate the animal immediately, provide clean water and shade, and call Pashu helpline 1962 or a registered veterinarian right away. Prescription dosages are withheld for animal safety.`
    : recentCases.length && !params.symptomsNotes && !params.query
      ? `Latest logged livestock case for ${recentCases[0].animalType}: ${recentCases[0].conditionName || 'Logged for vet review'}. ${SAFETY_NOTICE}`
      : `For ${animalType}, ensure clean drinking water, separate any symptomatic animal from healthy stock, and call veterinary helpline 1962 or visit your nearest veterinary dispensary. Antibiotic or steroid dosages are not prescribed automatically.`;

  return {
    tool: 'livestock_questions',
    farmerMatched: Boolean(farmer),
    animalType,
    urgentVetEscalation: urgent,
    escalationReason,
    prescriptionsWithheld: true,
    helplineNumber: '1962',
    safetyNotice: SAFETY_NOTICE,
    recentCases: recentCases.map((c) => ({
      id: c._id,
      animalType: c.animalType,
      conditionName: c.conditionName,
      urgentVetEscalation: Boolean(c.urgentVetEscalation),
      source: c.source,
    })),
    spokenSummary,
  };
}

async function handleWeatherForecasts(params, farmer) {
  const location = params.location || params.state || farmer?.district || farmer?.state || 'New Delhi';
  const useFarmerCoords = !params.location && !params.state && farmer?.latitude && farmer?.longitude;
  const w = await getWeatherForecast({
    lat: useFarmerCoords ? farmer.latitude : undefined,
    lon: useFarmerCoords ? farmer.longitude : undefined,
    location,
    includeAi: false,
  });

  const topAdvisory = w.advisories?.[0]?.message || '';
  const tomorrow = w.daily?.[1];
  const tomorrowNote = tomorrow
    ? ` Tomorrow forecast: ${tomorrow.tempMinC} to ${tomorrow.tempMaxC} degrees Celsius, rain probability ${tomorrow.precipitationProbPct} percent.`
    : '';

  return {
    tool: 'weather_forecasts',
    farmerMatched: Boolean(farmer),
    locationName: w.locationName,
    source: w.source,
    current: w.current,
    sprayWindowSuitable: w.sprayWindowSuitable,
    advisories: w.advisories,
    spokenSummary: `Weather at ${w.locationName}: ${w.current.temperatureC} degrees Celsius, humidity ${w.current.humidityPct} percent, wind ${w.current.windSpeedKmh} kilometers per hour. Spraying window is ${w.sprayWindowSuitable ? 'suitable' : 'not recommended'}. ${topAdvisory}${tomorrowNote}`.trim(),
  };
}

function handleMandiPrices(params, farmer) {
  const effectiveState = params.state || '';
  let prices = marketService.getPrices({
    crop: params.crop,
    state: effectiveState,
    category: params.category,
  });
  if (!prices.length && effectiveState) {
    prices = marketService.getPrices({ crop: params.crop, category: params.category });
  }
  const topPrices = prices.slice(0, 3);
  const stats = marketService.getStats();
  const comparisons = params.crop
    ? marketService.compareMarkets({ crop: params.crop, category: params.category }).slice(0, 1)
    : [];

  const spreadNote = comparisons.length
    ? ` Highest spread on ${comparisons[0].crop}: ${comparisons[0].highestMandi.price} at ${comparisons[0].highestMandi.market} versus ${comparisons[0].lowestMandi.price} at ${comparisons[0].lowestMandi.market}.`
    : '';

  const spokenSummary = topPrices.length
    ? `${topPrices.map((p) => `${p.crop} in ${p.market}, ${p.state}: rupees ${p.modalPrice || p.price} per ${p.unit}`).join('. ')}.${spreadNote}`
    : `No matching mandi listings found right now${params.crop ? ` for ${params.crop}` : ''}.`;

  return {
    tool: 'mandi_prices',
    farmerMatched: Boolean(farmer),
    sourceStatus: stats.sourceStatus,
    lastUpdated: stats.lastUpdated,
    prices: topPrices,
    comparison: comparisons[0] || null,
    spokenSummary,
  };
}

function handleGovernmentSchemes(params, farmer) {
  const effectiveState = params.state || farmer?.state || '';
  const hasProfileCriteria = Boolean(params.farmSize || farmer?.farmSizeAcres || params.soilType || farmer?.soilType || params.crop);

  if (hasProfileCriteria) {
    const matches = evaluateSchemeEligibility({
      state: effectiveState,
      farmSize: params.farmSize || farmer?.farmSizeAcres || '',
      landType: params.landType || 'owned',
      crop: params.crop || farmer?.primaryCrops?.[0] || '',
      soilType: params.soilType || farmer?.soilType || '',
      hasLivestock: params.hasLivestock,
    }).slice(0, 3);

    return {
      tool: 'government_schemes',
      farmerMatched: Boolean(farmer),
      eligibilityEvaluated: true,
      schemes: matches.map((m) => ({
        id: m.scheme.id,
        name: m.scheme.name,
        eligibilityStatus: m.eligibilityStatus,
        benefits: m.scheme.benefits,
        officialPortal: m.scheme.officialPortal,
      })),
      spokenSummary: matches.length
        ? matches.map((m) => `${m.scheme.name} (${m.eligibilityStatus}): ${m.scheme.benefits}`).join('. ')
        : 'No matching verified government schemes found for those details.',
    };
  }

  const schemes = searchSchemesCatalog({
    state: effectiveState,
    query: params.crop || params.query,
    category: params.category,
  }).slice(0, 3);

  return {
    tool: 'government_schemes',
    farmerMatched: Boolean(farmer),
    eligibilityEvaluated: false,
    schemes: schemes.map((s) => ({
      id: s.id,
      name: s.name,
      officialPortal: s.officialPortal,
      benefits: s.benefits,
    })),
    spokenSummary: schemes.length
      ? schemes.map((s) => `${s.name}: ${s.benefits}`).join('. ')
      : 'No matching verified government schemes found.',
  };
}

async function handleGeneralFarmingAdvice(params, farmer) {
  const message = params.query || `General seasonal advice for ${params.crop || farmer?.primaryCrops?.[0] || 'Indian crops'}`;
  const { contextText, groundedSources } = await gatherToolGrounding({
    user: farmer,
    category: 'general',
    message,
  });

  try {
    const aiRes = await callGeminiText({
      prompt: `Farmer phone question: "${message}" (crop=${params.crop || 'general'}, location=${params.location || farmer?.district || 'India'}). Answer clearly in <= 3 concise spoken sentences grounded ONLY in the context below.`,
      systemInstruction: `You are KissanSevaAI voice agricultural assistant. Reply cleanly for telephone speech in language "${params.language}". Never invent prices, subsidies, or restricted chemical dosages.\n\nGrounded Context:\n${contextText}`,
      userId: farmer?._id || 'sarvam_call_agent',
      feature: 'call_agent_advice',
      maxOutputTokens: 280,
    });

    return {
      tool: 'general_farming_advice',
      farmerMatched: Boolean(farmer),
      aiAvailable: true,
      serviceStatus: 'ok',
      requestId: aiRes.requestId,
      groundedSources,
      spokenSummary: aiRes.text,
    };
  } catch (aiErr) {
    const classified = classifyGeminiError(aiErr);
    const conciseContext = contextText
      ? contextText.split('\n').slice(0, 3).join('. ')
      : 'Check local Open-Meteo spray windows, AGMARKNET mandi rates, and verified KVK recommendations in the KissanSevaAI app.';
    return {
      tool: 'general_farming_advice',
      farmerMatched: Boolean(farmer),
      aiAvailable: false,
      serviceStatus: classified.code,
      groundedSources,
      spokenSummary: `Live generative AI advice is currently unavailable (${classified.code}). Grounded KissanSevaAI summary: ${conciseContext}`,
    };
  }
}

export async function dispatchCallAgentTool({ tool, originalIntent, params }) {
  const farmer = await resolveCallerUser({
    callerPhone: params.callerPhone,
    farmerId: params.farmerId,
  });

  let payload;
  switch (tool) {
    case 'crop_assistance':
      payload = await handleCropAssistance(params, farmer);
      break;
    case 'livestock_questions':
      payload = await handleLivestockQuestions(params, farmer);
      break;
    case 'weather_forecasts':
      payload = await handleWeatherForecasts(params, farmer);
      break;
    case 'mandi_prices':
      payload = handleMandiPrices(params, farmer);
      break;
    case 'government_schemes':
      payload = handleGovernmentSchemes(params, farmer);
      break;
    case 'general_farming_advice':
      payload = await handleGeneralFarmingAdvice(params, farmer);
      break;
    default:
      throw new Error(`Unsupported tool "${tool}"`);
  }

  return {
    ok: true,
    intent: originalIntent,
    ...payload,
  };
}

export function getCallAgentContract() {
  const sharedSecretConfigured = Boolean(
    (process.env.CALL_AGENT_WEBHOOK_SECRET || process.env.SARVAM_WEBHOOK_SECRET || '').trim(),
  );
  const hmacSigningConfigured = Boolean(
    (process.env.SARVAM_WEBHOOK_SIGNING_SECRET || process.env.CALL_AGENT_WEBHOOK_SECRET || '').trim(),
  );
  const sarvamApiKeyConfigured = Boolean((process.env.SARVAM_API_KEY || '').trim());

  const configured = sharedSecretConfigured || hmacSigningConfigured;
  return {
    service: 'kissansevaai-sarvam-call-agent-adapter',
    provider: 'sarvam_compatible',
    status: configured ? 'webhook_configured_ready' : 'contract_ready_external_telephony_required',
    note: configured
      ? 'Webhook credentials are configured. Point your Sarvam Call Agent custom tools at the webhook or per-tool URLs and run a real inbound call test.'
      : 'Set CALL_AGENT_WEBHOOK_SECRET / SARVAM_WEBHOOK_SIGNING_SECRET and map these custom tools in your Sarvam Call Agent dashboard.',
    configuration: {
      sharedSecretConfigured,
      hmacSigningConfigured,
      sarvamApiKeyConfigured,
    },
    webhookEndpoint: '/api/integrations/call-agent/webhook',
    toolEndpointTemplate: '/api/integrations/call-agent/tools/:toolName',
    authHeaders: ['X-Call-Agent-Secret', 'X-Sarvam-Secret', 'Authorization: Bearer <secret>'],
    signatureHeaders: ['X-Sarvam-Signature', 'X-Call-Agent-Signature'],
    timestampHeaders: ['X-Sarvam-Timestamp', 'X-Call-Agent-Timestamp'],
    supportedTools: CANONICAL_TOOLS,
    supportedIntents: [
      'mandi_rate_lookup',
      'weather_spray_check',
      'scheme_summary',
      ...CANONICAL_TOOLS,
    ],
    sarvamToolSchemas: [
      {
        name: 'crop_assistance',
        endpoint: '/api/integrations/call-agent/tools/crop_assistance',
        description: 'Check farmer latest DINOv2 scan or provide safe crop guidance while strictly honoring treatmentWithheld gates.',
        parameters: ['callerPhone', 'crop', 'disease', 'symptomsNotes', 'language'],
      },
      {
        name: 'livestock_questions',
        endpoint: '/api/integrations/call-agent/tools/livestock_questions',
        description: 'Evaluate veterinary red-flag symptoms (1962 escalation) safely without prescribing antibiotics.',
        parameters: ['callerPhone', 'animalType', 'feverStatus', 'appetiteStatus', 'milkYieldChange', 'lesionsOrUlcers', 'symptomsNotes'],
      },
      {
        name: 'weather_forecasts',
        endpoint: '/api/integrations/call-agent/tools/weather_forecasts',
        description: 'Fetch Open-Meteo weather forecast and spray window suitability for farmer district or city.',
        parameters: ['callerPhone', 'location', 'state', 'language'],
      },
      {
        name: 'mandi_prices',
        endpoint: '/api/integrations/call-agent/tools/mandi_prices',
        description: 'Look up live/cached AGMARKNET mandi modal prices and cross-market spreads.',
        parameters: ['crop', 'state', 'category'],
      },
      {
        name: 'government_schemes',
        endpoint: '/api/integrations/call-agent/tools/government_schemes',
        description: 'Search verified Indian agricultural schemes or evaluate profile eligibility.',
        parameters: ['callerPhone', 'state', 'crop', 'farmSize', 'soilType', 'landType', 'query'],
      },
      {
        name: 'general_farming_advice',
        endpoint: '/api/integrations/call-agent/tools/general_farming_advice',
        description: 'Provide grounded general farming advice combining profile, weather, mandi, schemes, and planner context with graceful degradation on AI quota limits.',
        parameters: ['callerPhone', 'query', 'crop', 'location', 'language'],
      },
    ],
  };
}
