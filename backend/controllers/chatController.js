import { callGeminiText, classifyGeminiError } from '../services/geminiService.js';
import ChatHistory from '../models/ChatHistory.js';
import CropAnalysis from '../models/CropAnalysis.js';
import LivestockAnalysis from '../models/LivestockAnalysis.js';
import CropPlan from '../models/CropPlan.js';
import PlannerTask from '../models/PlannerTask.js';
import { marketService } from '../services/marketDataService.js';
import { getWeatherForecast } from '../services/weatherService.js';
import { searchSchemesCatalog } from '../services/schemeCatalogService.js';

const LANG_NAMES = {
  en: 'English',
  hi: 'Hindi',
  bho: 'Bhojpuri',
  mr: 'Marathi',
  pa: 'Punjabi',
  sa: 'Sanskrit',
};

function normalizeCategory(modeOrCat = 'general', message = '') {
  const raw = String(modeOrCat || '').toLowerCase().trim();
  if (raw.includes('crop') && !raw.includes('plan')) return 'crop';
  if (raw.includes('livestock') || raw.includes('vet') || raw.includes('animal')) return 'livestock';
  if (raw.includes('mandi') || raw.includes('market') || raw.includes('price')) return 'mandi';
  if (raw.includes('scheme') || raw.includes('subsidy') || raw.includes('loan')) return 'schemes';
  if (raw.includes('weather') || raw.includes('rain')) return 'weather';
  if (raw.includes('plan') || raw.includes('calendar') || raw.includes('task')) return 'planner';

  const msg = String(message || '').toLowerCase();
  if (/mandi|price|rate|bhav|quintal|market|मंडी|भाव/.test(msg)) return 'mandi';
  if (/rain|weather|spray|wind|temp|मौसम|बारिश|छिड़काव/.test(msg)) return 'weather';
  if (/scheme|kisan|pmfby|kcc|subsidy|योजना|सब्सिडी/.test(msg)) return 'schemes';
  if (/cow|buffalo|goat|poultry|milk|fever|गाय|भैंस|पशु/.test(msg)) return 'livestock';
  if (/plan|schedule|sow|irriga|fertiliz|कैलेण्डर|खाद|सिंचाई/.test(msg)) return 'planner';
  if (/leaf|disease|rust|blight|fungus|रोग|पत्ती/.test(msg)) return 'crop';
  return 'general';
}

async function gatherToolGrounding({ user, category, message }) {
  const contextLines = [];
  const groundedSources = [];

  if (user) {
    const profileParts = [
      user.state ? `State=${user.state}` : null,
      user.district ? `District=${user.district}` : null,
      user.soilType ? `Soil=${user.soilType}` : null,
      user.irrigationType ? `Irrigation=${user.irrigationType}` : null,
      user.farmSizeAcres ? `FarmSize=${user.farmSizeAcres} acres` : null,
      Array.isArray(user.primaryCrops) && user.primaryCrops.length ? `Crops=${user.primaryCrops.join(', ')}` : null,
    ].filter(Boolean);
    if (profileParts.length) {
      contextLines.push(`Farmer Profile: ${profileParts.join('; ')}`);
      groundedSources.push('farm_profile');
    }
  }

  // Ground mandi queries against live/cached market prices
  if (category === 'mandi' || category === 'general') {
    const words = String(message || '').toLowerCase().split(/\s+/).filter((w) => w.length > 3);
    const allPrices = marketService.getPrices({});
    const matched = words.length
      ? allPrices.filter((p) => words.some((w) => p.crop.toLowerCase().includes(w) || p.state.toLowerCase().includes(w)))
      : [];
    const sample = (matched.length ? matched : allPrices).slice(0, 5);
    if (sample.length) {
      const formatted = sample.map((p) => `${p.crop} @ ${p.market} (${p.state}): ₹${p.modalPrice || p.price}/${p.unit}`).join('; ');
      contextLines.push(`Latest Mandi Rates (${marketService.getStats().sourceStatus || 'cached'}): ${formatted}`);
      groundedSources.push(`mandi_prices(${sample.length})`);
    }
  }

  // Ground scheme queries against curated verified government scheme catalog
  if (category === 'schemes' || category === 'general') {
    const schemeMatches = searchSchemesCatalog({ query: message, state: user?.state }).slice(0, 4);
    if (schemeMatches.length) {
      const sSummary = schemeMatches.map((s) => `${s.name} (${s.officialPortal}) — ${s.benefits}`).join('; ');
      contextLines.push(`Verified Govt Schemes: ${sSummary}`);
      groundedSources.push(`verified_schemes(${schemeMatches.length})`);
    }
  }

  // Ground weather if requested or user location known
  if (category === 'weather' || category === 'planner' || category === 'crop') {
    try {
      const loc = user?.district || user?.state || 'New Delhi';
      const w = await getWeatherForecast({
        lat: user?.latitude,
        lon: user?.longitude,
        location: loc,
        includeAi: false,
      });
      const adv = (w.advisories || []).map((a) => a.title).join('; ');
      contextLines.push(
        `Weather (${w.locationName}, ${w.source}): ${w.current?.temperatureC}°C, RH ${w.current?.humidityPct}%, Wind ${w.current?.windSpeedKmh}km/h. Spray suitable=${w.sprayWindowSuitable}. Advisories: ${adv}`,
      );
      groundedSources.push(`weather(${w.source})`);
    } catch {
      // Do not block chat if external weather is down
    }
  }

  // Ground recent crop & livestock records + active crop plans when DB is online
  if (user?._id && CropAnalysis.db?.readyState === 1) {
    if (category === 'crop') {
      const recentCrops = await CropAnalysis.find({ userId: user._id }).sort({ createdAt: -1 }).limit(2).lean();
      if (recentCrops.length) {
        contextLines.push(
          `Recent Crop Scans: ${recentCrops.map((c) => `${c.cropName || ''} - ${c.diseaseName} (needs_review=${c.needs_review})`).join('; ')}`,
        );
        groundedSources.push(`crop_history(${recentCrops.length})`);
      }
    }
    if (category === 'livestock') {
      const recentAnimals = await LivestockAnalysis.find({ userId: user._id }).sort({ createdAt: -1 }).limit(2).lean();
      if (recentAnimals.length) {
        contextLines.push(
          `Recent Livestock Cases: ${recentAnimals.map((l) => `${l.animalType}: ${l.conditionName || 'Unresolved'} (urgent=${l.urgentVetEscalation})`).join('; ')}`,
        );
        groundedSources.push(`livestock_history(${recentAnimals.length})`);
      }
    }
    if (category === 'planner') {
      const activePlan = await CropPlan.findOne({ userId: user._id, status: 'active' }).sort({ updatedAt: -1 }).lean();
      if (activePlan) {
        const pendingTasks = await PlannerTask.find({ planId: activePlan._id, status: 'pending' }).sort({ dayOffset: 1 }).limit(4).lean();
        contextLines.push(
          `Active Crop Plan: ${activePlan.cropName} (${activePlan.stage}). Pending tasks: ${pendingTasks.map((t) => `${t.scheduledDate}: ${t.title}`).join('; ')}`,
        );
        groundedSources.push('crop_planner');
      }
    }
  }

  return { contextText: contextLines.join('\n'), groundedSources };
}

export const handleChat = async (req, res) => {
  try {
    const {
      message,
      language = 'en',
      mode = 'general',
      category: explicitCategory = null,
      sessionId = 'default',
      clientRequestId = null,
    } = req.body || {};

    if (!message || !String(message).trim()) {
      return res.status(400).json({ error: 'message is required' });
    }

    const category = normalizeCategory(explicitCategory || mode, message);
    const dbReady = ChatHistory.db?.readyState === 1;

    if (dbReady && clientRequestId && req.user?._id) {
      const replay = await ChatHistory.findOne({ userId: req.user._id, clientRequestId }).lean();
      if (replay) {
        return res.json({
          response: replay.response,
          sessionId: replay.sessionId,
          category: replay.category,
          groundedSources: replay.groundedSources || [],
          aiAvailable: replay.aiAvailable,
          idempotentReplay: true,
        });
      }
    }

    // Load last 8 conversation turns in this session for multi-turn continuity
    const history = [];
    if (dbReady && req.user?._id) {
      const recentTurns = await ChatHistory.find({ userId: req.user._id, sessionId })
        .sort({ createdAt: -1 })
        .limit(4)
        .lean();
      for (const turn of recentTurns.reverse()) {
        history.push({ role: 'user', text: turn.message });
        history.push({ role: 'model', text: turn.response });
      }
    }

    const { contextText, groundedSources } = await gatherToolGrounding({
      user: req.user,
      category,
      message,
    });

    const targetLang = LANG_NAMES[(language || 'en').toLowerCase().trim()] || 'English';
    const systemInstruction = `You are KissanSevaAI ("किसान मित्र"), a practical agricultural assistant for Indian farmers.
Focus category: ${category}.
Rules:
1. Reply strictly in ${targetLang}.
2. Use plain readable text (avoid complex markdown tables; use simple numbered bullets).
3. Base numbers, mandi rates, scheme names, and weather advice strictly on the verified Context below when applicable. Never fabricate prices, dosages, or subsidies.
4. For animal illnesses or uncertain crop diseases, remind the farmer to verify with a local veterinarian (1962) or KVK agronomist rather than prescribing restricted dosages.
${contextText ? `\nVerified Grounding Context:\n${contextText}` : ''}`;

    try {
      const aiRes = await callGeminiText({
        prompt: String(message).trim(),
        systemInstruction,
        history,
        userId: req.user?._id,
        feature: `chat_${category}`,
      });

      if (dbReady && req.user?._id) {
        await ChatHistory.create({
          userId: req.user._id,
          sessionId,
          clientRequestId: clientRequestId || undefined,
          message: String(message).trim(),
          response: aiRes.text,
          mode,
          category,
          language,
          groundedSources,
          aiAvailable: true,
          serviceStatus: 'ok',
          requestId: aiRes.requestId,
        });
      }

      return res.json({
        response: aiRes.text,
        sessionId,
        category,
        groundedSources,
        aiAvailable: true,
        serviceStatus: 'ok',
        requestId: aiRes.requestId,
      });
    } catch (aiErr) {
      const classified = classifyGeminiError(aiErr);
      return res.status(classified.statusCode || 503).json({
        error: classified.userMessage,
        code: classified.code,
        aiAvailable: false,
        serviceStatus: classified.code,
        requestId: classified.requestId,
        retryAfterSeconds: classified.retryAfterSeconds,
        sessionId,
        category,
        groundedSources,
        groundedContextSummary: contextText || null,
      });
    }
  } catch (error) {
    console.error('Chat Controller Error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
};

export const getChatHistory = async (req, res) => {
  try {
    const { sessionId = 'default', limit = 40 } = req.query;
    const query = { userId: req.user._id };
    if (sessionId !== 'all') query.sessionId = sessionId;

    const turns = await ChatHistory.find(query)
      .sort({ createdAt: 1 })
      .limit(Math.min(Number(limit) || 40, 100))
      .lean();
    res.json({ sessionId, turns });
  } catch (error) {
    res.status(500).json({ error: 'Failed to load chat history' });
  }
};

export const clearChatHistory = async (req, res) => {
  try {
    const { sessionId } = req.query;
    const query = { userId: req.user._id };
    if (sessionId && sessionId !== 'all') query.sessionId = sessionId;
    const result = await ChatHistory.deleteMany(query);
    res.json({ cleared: result.deletedCount || 0, sessionId: sessionId || 'all' });
  } catch (error) {
    res.status(500).json({ error: 'Failed to clear chat history' });
  }
};
