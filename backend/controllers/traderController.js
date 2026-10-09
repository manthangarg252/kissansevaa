
import Trader from '../models/Trader.js';
import { generateText } from '../services/geminiService.js';

export const getTraders = async (req, res) => {
  try {
    const { location, search } = req.query;
    let filter = {};

    if (location) filter.location = location;

    let traders = await Trader.find(filter).sort({ rating: -1 });

    if (search) {
      traders = traders.filter(t => 
        t.name.toLowerCase().includes(search.toLowerCase()) || 
        t.dealsIn.some(crop => crop.toLowerCase().includes(search.toLowerCase()))
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
      reviewCount: 0
    });
    res.json({ _id: trader._id, id: trader._id, message: 'Trader added successfully' });
  } catch (error) {
    res.status(500).json({ error: 'Error adding trader' });
  }
};

export const getTraderAdvisory = async (req, res) => {
  try {
    const { query, language } = req.body;

    if (!query) {
      return res.status(400).json({ error: 'query is required' });
    }

    const systemInstruction = `You are an expert Trading Advisor for Indian Farmers.
      Focus on market trends, reliable buyer practices, negotiation strategies,
      and how to identify trustworthy buyers.
      Reply strictly in ${language || 'English'}.
      Use plain text only. No Markdown. Use simple numbering for lists.`;

    const response = await generateText(query, systemInstruction);
    res.json({ response });
  } catch (error) {
    console.error('Trader Advisory Error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
};
