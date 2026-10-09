
import { generateText } from '../services/geminiService.js';

let cache = { data: null, expiry: 0 };

const now = new Date().toISOString();
const MOCK_PRICES = [
  { _id: 'mp_1', id: 'mp_1', crop: 'Wheat', market: 'Nagpur APMC', state: 'Maharashtra', price: 2300, minPrice: 2150, maxPrice: 2450, modalPrice: 2300, unit: 'quintal', trend: 'up', updatedAt: now, lastUpdated: now },
  { _id: 'mp_2', id: 'mp_2', crop: 'Rice', market: 'Burdwan', state: 'West Bengal', price: 3000, minPrice: 2800, maxPrice: 3200, modalPrice: 3000, unit: 'quintal', trend: 'stable', updatedAt: now, lastUpdated: now },
  { _id: 'mp_3', id: 'mp_3', crop: 'Onion', market: 'Lasalgaon', state: 'Maharashtra', price: 1600, minPrice: 1400, maxPrice: 1800, modalPrice: 1600, unit: 'quintal', trend: 'down', updatedAt: now, lastUpdated: now },
  { _id: 'mp_4', id: 'mp_4', crop: 'Tomato', market: 'Kolar', state: 'Karnataka', price: 1100, minPrice: 800, maxPrice: 1400, modalPrice: 1100, unit: 'quintal', trend: 'up', updatedAt: now, lastUpdated: now },
  { _id: 'mp_5', id: 'mp_5', crop: 'Cotton', market: 'Rajkot', state: 'Gujarat', price: 7100, minPrice: 6800, maxPrice: 7400, modalPrice: 7100, unit: 'quintal', trend: 'up', updatedAt: now, lastUpdated: now },
  { _id: 'mp_6', id: 'mp_6', crop: 'Soybean', market: 'Indore', state: 'Madhya Pradesh', price: 4700, minPrice: 4500, maxPrice: 4900, modalPrice: 4700, unit: 'quintal', trend: 'down', updatedAt: now, lastUpdated: now },
];

export const getPrices = async (req, res) => {
  const { crop, state } = req.query;

  if (cache.data && Date.now() < cache.expiry) {
    return res.json(filterResults(cache.data, crop, state));
  }

  cache.data = MOCK_PRICES;
  cache.expiry = Date.now() + 10 * 60 * 1000;
  res.json(filterResults(cache.data, crop, state));
};

export const getMarketRecommendations = async (req, res) => {
  try {
    const { location, soilType, farmSize, currentCrop, irrigation, language } = req.body;
    
    const systemInstruction = `You are a Smart Farming Market Strategist. 
      Analyze the farm profile and suggest the best crops for profit, 
      best selling months, and water management tips. 
      Reply strictly in ${language || 'English'}. Use markdown.`;

    const prompt = `Location: ${location}, Soil: ${soilType}, Size: ${farmSize} Acres, Current Crop: ${currentCrop}, Irrigation: ${irrigation}.`;
    
    const response = await generateText(prompt, systemInstruction);
    res.json({ response });
  } catch (error) {
    res.status(500).json({ error: 'Internal server error' });
  }
};

function filterResults(data, crop, state) {
  return data.filter(p => {
    const matchCrop = crop ? p.crop.toLowerCase().includes(crop.toLowerCase()) : true;
    const matchState = state ? p.state.toLowerCase().includes(state.toLowerCase()) : true;
    return matchCrop && matchState;
  });
}
