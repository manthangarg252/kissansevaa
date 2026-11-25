import { marketService } from '../services/marketDataService.js';
import { callGeminiText, sendAiErrorResponse } from '../services/geminiService.js';
import MarketFavorite from '../models/MarketFavorite.js';

export const getPrices = async (req, res) => {
  try {
    const { crop, state, district, market, category, sort, refresh } = req.query;

    if (refresh === 'true' || refresh === '1') {
      await marketService.updatePrices();
    }

    const prices = marketService.getPrices({ crop, state, district, market, category, sort });
    res.json(prices);
  } catch (error) {
    console.error('Error fetching market prices:', error);
    res.status(500).json({ error: 'Failed to retrieve market prices' });
  }
};

export const refreshPrices = async (req, res) => {
  try {
    const refreshResult = await marketService.updatePrices();
    const stats = marketService.getStats();
    res.json({
      success: true,
      ...refreshResult,
      message: refreshResult.updatedFromLiveApi
        ? 'Market prices refreshed from Data.gov.in APMC feed.'
        : `Live APMC refresh skipped/unavailable (${refreshResult.sourceStatus}); serving verified cached rates.`,
      stats,
    });
  } catch (error) {
    console.error('Error refreshing prices:', error);
    res.status(500).json({ error: 'Failed to refresh prices' });
  }
};

export const getMarketStats = async (_req, res) => {
  try {
    res.json(marketService.getStats());
  } catch (error) {
    res.status(500).json({ error: 'Failed to get stats' });
  }
};

export const compareMarketPrices = async (req, res) => {
  try {
    const { crop, category } = req.query;
    const comparisons = marketService.compareMarkets({ crop, category });
    res.json({
      stats: marketService.getStats(),
      comparisons,
    });
  } catch (error) {
    res.status(500).json({ error: 'Failed to compare mandi prices' });
  }
};

export const getMarketFavorites = async (req, res) => {
  try {
    const favorites = await MarketFavorite.find({ userId: req.user._id }).sort({ createdAt: -1 }).lean();
    res.json(favorites);
  } catch (error) {
    res.status(500).json({ error: 'Failed to load market favorites' });
  }
};

export const addMarketFavorite = async (req, res) => {
  try {
    const { crop, market = '', state = '', category = 'crop', clientRequestId } = req.body || {};
    if (!crop) {
      return res.status(400).json({ error: 'crop is required' });
    }
    const favorite = await MarketFavorite.findOneAndUpdate(
      { userId: req.user._id, crop, market },
      { $set: { state, category, clientRequestId } },
      { upsert: true, new: true },
    );
    res.json(favorite);
  } catch (error) {
    res.status(500).json({ error: 'Failed to save market favorite' });
  }
};

export const removeMarketFavorite = async (req, res) => {
  try {
    const crop = req.query.crop || req.body?.crop;
    const market = req.query.market ?? req.body?.market ?? '';
    if (!crop) {
      return res.status(400).json({ error: 'crop is required' });
    }
    await MarketFavorite.deleteOne({ userId: req.user._id, crop, market });
    res.json({ removed: true, crop, market });
  } catch (error) {
    res.status(500).json({ error: 'Failed to remove market favorite' });
  }
};

export const getMarketRecommendations = async (req, res) => {
  try {
    const { location, soilType, farmSize, currentCrop, irrigation, language = 'en' } = req.body || {};
    const liveMatches = marketService.getPrices({ crop: currentCrop }).slice(0, 5);
    const rateSummary = liveMatches
      .map((m) => `${m.crop} @ ${m.market} (${m.state}): ₹${m.modalPrice || m.price}/quintal (${m.trend})`)
      .join('; ');

    const systemInstruction = `You are a Smart Farming Market Strategist.
Analyze the farm profile and ground price insights strictly on the mandi snapshot below when available.
Reply strictly in ${language || 'English'}. Use plain clear formatting.`;

    const prompt = `Location: ${location}, Soil: ${soilType}, Size: ${farmSize} Acres, Current Crop: ${currentCrop}, Irrigation: ${irrigation}.${rateSummary ? ` Current Mandi Snapshot: ${rateSummary}` : ''}`;

    const aiRes = await callGeminiText({
      prompt,
      systemInstruction,
      userId: req.user?._id,
      feature: 'market_strategy',
    });

    res.json({
      response: aiRes.text,
      aiAvailable: true,
      serviceStatus: 'ok',
      requestId: aiRes.requestId,
      marketStats: marketService.getStats(),
    });
  } catch (error) {
    return sendAiErrorResponse(res, error, { marketStats: marketService.getStats() });
  }
};
