import { marketService } from '../services/marketDataService.js';
import { generateText } from '../services/geminiService.js';

export const getPrices = async (req, res) => {
  try {
    const { crop, state, category, refresh } = req.query;

    if (refresh === 'true' || refresh === '1') {
      await marketService.updatePrices();
    }

    const prices = marketService.getPrices({ crop, state, category });
    res.json(prices);
  } catch (error) {
    console.error('Error fetching market prices:', error);
    res.status(500).json({ error: 'Failed to retrieve market prices' });
  }
};

export const refreshPrices = async (req, res) => {
  try {
    await marketService.updatePrices();
    const stats = marketService.getStats();
    res.json({
      success: true,
      message: 'Market prices refreshed successfully',
      stats
    });
  } catch (error) {
    console.error('Error refreshing prices:', error);
    res.status(500).json({ error: 'Failed to refresh prices' });
  }
};

export const getMarketStats = async (req, res) => {
  try {
    res.json(marketService.getStats());
  } catch (error) {
    res.status(500).json({ error: 'Failed to get stats' });
  }
};

export const getMarketRecommendations = async (req, res) => {
  try {
    const { location, soilType, farmSize, currentCrop, irrigation, language } = req.body;
    
    const systemInstruction = `You are a Smart Farming Market Strategist. 
      Analyze the farm profile and suggest the best crops, vegetables, or fruits for maximum profit, 
      optimal selling months, APMC mandi rates, and water/soil management tips. 
      Reply strictly in ${language || 'English'}. Use markdown.`;

    const prompt = `Location: ${location}, Soil: ${soilType}, Size: ${farmSize} Acres, Current Crop: ${currentCrop}, Irrigation: ${irrigation}.`;
    
    const response = await generateText(prompt, systemInstruction);
    res.json({ response });
  } catch (error) {
    console.error('Error generating market recommendation:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
};
