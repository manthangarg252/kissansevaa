import { callGeminiText, classifyGeminiError } from '../services/geminiService.js';
import {
  CATALOG_METADATA,
  VERIFIED_SCHEMES,
  searchSchemesCatalog,
  evaluateSchemeEligibility,
} from '../services/schemeCatalogService.js';
import SavedScheme from '../models/SavedScheme.js';

export const getSchemesCatalog = async (req, res) => {
  try {
    const { state = '', category = '', q = '' } = req.query;
    const schemes = searchSchemesCatalog({ state, category, query: q });

    let savedIds = [];
    if (req.user?._id && SavedScheme.db?.readyState === 1) {
      const saved = await SavedScheme.find({ userId: req.user._id }).lean();
      savedIds = saved.map((s) => s.schemeId);
    }

    res.json({
      catalogVersion: CATALOG_METADATA.version,
      lastVerifiedAt: CATALOG_METADATA.lastVerifiedAt,
      verificationNote: CATALOG_METADATA.verificationNote,
      total: schemes.length,
      schemes,
      savedIds,
    });
  } catch (error) {
    res.status(500).json({ error: 'Failed to retrieve verified schemes catalog' });
  }
};

export const checkSchemeEligibility = async (req, res) => {
  try {
    const {
      state = req.user?.state || '',
      farmSize = req.user?.farmSizeAcres || '',
      crop = '',
      soilType = req.user?.soilType || '',
      landType = 'owned',
      hasLivestock = false,
      language = 'en',
      includeAi = true,
    } = req.body || {};

    const results = evaluateSchemeEligibility({
      state,
      farmSize,
      landType,
      crop,
      soilType,
      hasLivestock: Boolean(hasLivestock),
    });

    const topMatches = results.slice(0, 5);
    let aiSummary = null;
    let aiAvailable = false;
    let aiServiceStatus = includeAi ? 'unavailable' : 'not_requested';

    if (includeAi) {
      try {
        const grounded = topMatches
          .map((m) => `- ${m.scheme.name} [${m.eligibilityStatus}]: ${m.scheme.benefits} (${m.scheme.officialPortal})`)
          .join('\n');
        const aiRes = await callGeminiText({
          prompt: `Explain in ${language} how this farmer (State=${state || 'India'}, Farm=${farmSize || 'N/A'} acres, Land=${landType}, Crop=${crop || 'Mixed'}) should prioritize ONLY these verified schemes:\n${grounded}\nKeep it concise (max 5 bullets) and do NOT invent any extra scheme or unverified payout.`,
          systemInstruction: 'You are an official Indian Agri-Scheme Counselor. Only explain schemes provided in the prompt.',
          userId: req.user?._id,
          feature: 'scheme_eligibility',
        });
        aiSummary = aiRes.text;
        aiAvailable = true;
        aiServiceStatus = 'ok';
      } catch (err) {
        const classified = classifyGeminiError(err);
        aiServiceStatus = classified.code;
      }
    }

    res.json({
      catalogVersion: CATALOG_METADATA.version,
      lastVerifiedAt: CATALOG_METADATA.lastVerifiedAt,
      matches: results,
      aiSummary,
      aiAvailable,
      aiServiceStatus,
    });
  } catch (error) {
    console.error('Scheme Eligibility Error:', error);
    res.status(500).json({ error: 'Failed to evaluate scheme eligibility' });
  }
};

export const recommendSchemes = async (req, res) => {
  try {
    const { state, farmSize, crop, soilType, landType, language = 'en' } = req.body || {};
    const topMatches = evaluateSchemeEligibility({ state, farmSize, landType, crop, soilType }).slice(0, 4);

    try {
      const groundedList = topMatches
        .map((r) => `${r.scheme.name}: ${r.scheme.benefits}. Portal: ${r.scheme.officialPortal}`)
        .join('\n');
      const aiRes = await callGeminiText({
        prompt: `Farmer profile: State=${state}, Farm=${farmSize} Acres, Crop=${crop}, Soil=${soilType}, Land=${landType}. Explain step-by-step in ${language} how to apply for these verified schemes:\n${groundedList}`,
        systemInstruction: 'You are an agricultural scheme advisor. Rely strictly on the verified schemes provided.',
        userId: req.user?._id,
        feature: 'scheme_recommendations',
      });
      return res.json({
        response: aiRes.text,
        matches: topMatches,
        aiAvailable: true,
        serviceStatus: 'ok',
        catalogLastVerifiedAt: CATALOG_METADATA.lastVerifiedAt,
      });
    } catch (aiErr) {
      const classified = classifyGeminiError(aiErr);
      // Still return structured verified matches transparently instead of failing with fake text!
      const deterministicSummary = topMatches
        .map(
          (r, idx) =>
            `${idx + 1}. ${r.scheme.name} (${r.eligibilityStatus})\n   Benefit: ${r.scheme.benefits}\n   Portal: ${r.scheme.officialPortal} | Helpline: ${r.scheme.phone || 'N/A'}`,
        )
        .join('\n\n');
      return res.json({
        response: `Verified Scheme Matches (AI explanation unavailable: ${classified.code}):\n\n${deterministicSummary}`,
        matches: topMatches,
        aiAvailable: false,
        serviceStatus: classified.code,
        catalogLastVerifiedAt: CATALOG_METADATA.lastVerifiedAt,
      });
    }
  } catch (error) {
    res.status(500).json({ error: 'Internal server error' });
  }
};

export const explainScheme = async (req, res) => {
  try {
    const { schemeId, language = 'en' } = req.body || {};
    const scheme = VERIFIED_SCHEMES.find((s) => s.id === schemeId);
    if (!scheme) {
      return res.status(404).json({ error: `Verified scheme "${schemeId}" not found` });
    }

    try {
      const aiRes = await callGeminiText({
        prompt: `Explain "${scheme.name}" simply for a farmer in language "${language}".
Facts (do not alter or add numbers):
- Description: ${scheme.description}
- Benefits: ${scheme.benefits}
- Eligibility: ${scheme.eligibility.join('; ')}
- Documents: ${scheme.documents.join(', ')}
- Application steps: ${scheme.applicationSteps.join(' -> ')}
- Official portal: ${scheme.officialPortal}`,
        systemInstruction: 'Explain government schemes clearly in plain language. Never invent unverified subsidy amounts.',
        userId: req.user?._id,
        feature: 'scheme_explain',
      });
      return res.json({
        scheme,
        explanation: aiRes.text,
        aiAvailable: true,
        serviceStatus: 'ok',
        requestId: aiRes.requestId,
      });
    } catch (aiErr) {
      const classified = classifyGeminiError(aiErr);
      return res.status(classified.statusCode || 503).json({
        error: classified.userMessage,
        aiAvailable: false,
        serviceStatus: classified.code,
        requestId: classified.requestId,
        scheme,
        fallbackChecklist: {
          benefits: scheme.benefits,
          documents: scheme.documents,
          applicationSteps: scheme.applicationSteps,
          officialPortal: scheme.officialPortal,
        },
      });
    }
  } catch (error) {
    res.status(500).json({ error: 'Failed to explain scheme' });
  }
};

export const getSavedSchemes = async (req, res) => {
  try {
    const saved = await SavedScheme.find({ userId: req.user._id }).sort({ createdAt: -1 }).lean();
    res.json(saved);
  } catch (error) {
    res.status(500).json({ error: 'Failed to load saved schemes' });
  }
};

export const saveScheme = async (req, res) => {
  try {
    const { schemeId, status = 'saved', notes = '', clientRequestId } = req.body || {};
    if (!schemeId) {
      return res.status(400).json({ error: 'schemeId is required' });
    }
    const saved = await SavedScheme.findOneAndUpdate(
      { userId: req.user._id, schemeId },
      { $set: { status, notes, clientRequestId } },
      { upsert: true, new: true },
    );
    res.json(saved);
  } catch (error) {
    res.status(500).json({ error: 'Failed to save scheme' });
  }
};

export const removeSavedScheme = async (req, res) => {
  try {
    await SavedScheme.deleteOne({ userId: req.user._id, schemeId: req.params.schemeId });
    res.json({ removed: true, schemeId: req.params.schemeId });
  } catch (error) {
    res.status(500).json({ error: 'Failed to remove saved scheme' });
  }
};
