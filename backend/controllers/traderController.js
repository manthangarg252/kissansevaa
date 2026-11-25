import Trader from '../models/Trader.js';
import { callGeminiText, sendAiErrorResponse } from '../services/geminiService.js';

export const getTraders = async (req, res) => {
  try {
    const { location, search } = req.query;
    const filter = {};
    if (location) filter.location = location;

    let traders = await Trader.find(filter).sort({ rating: -1 });
    if (search) {
      traders = traders.filter(
        (t) =>
          t.name.toLowerCase().includes(search.toLowerCase()) ||
          t.dealsIn.some((crop) => crop.toLowerCase().includes(search.toLowerCase())),
      );
    }

    res.json(traders);
  } catch (error) {
    res.status(500).json({ error: 'Error fetching traders' });
  }
};

export const addTrader = async (req, res) => {
  try {
    const data = req.body;
    const trader = await Trader.create({
      ...data,
      rating: 0,
      reviewCount: 0,
    });
    res.json({ _id: trader._id, id: trader._id, message: 'Trader added successfully' });
  } catch (error) {
    res.status(500).json({ error: 'Error adding trader' });
  }
};

export const getTraderAdvisory = async (req, res) => {
  try {
    const { query, language = 'en' } = req.body || {};

    if (!query) {
      return res.status(400).json({ error: 'query is required' });
    }

    const systemInstruction = `You are an expert Trading Advisor for Indian Farmers.
Focus on market trends, reliable buyer verification, weighing transparency, and e-NAM / APMC safeguards.
Reply strictly in ${language || 'English'}.
Use plain text only with simple numbering.`;

    const aiRes = await callGeminiText({
      prompt: query,
      systemInstruction,
      userId: req.user?._id,
      feature: 'trader_advisory',
    });

    res.json({
      response: aiRes.text,
      aiAvailable: true,
      requestId: aiRes.requestId,
    });
  } catch (error) {
    return sendAiErrorResponse(res, error);
  }
};
