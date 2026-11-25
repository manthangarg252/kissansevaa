import { callGeminiText, sendAiErrorResponse } from '../services/geminiService.js';

export const estimateCarbon = async (req, res) => {
  try {
    const { farmSize, cropType, method, irrigation, language = 'en' } = req.body || {};

    const systemInstruction = `You are a Carbon Credit Consultant for sustainable Indian agriculture.
Estimate indicative soil carbon potential honestly (state assumptions clearly) and list actionable practices.
Reply strictly in ${language || 'English'}.`;

    const prompt = `Farm Size: ${farmSize} Acres, Crop: ${cropType}, Method: ${method}, Irrigation: ${irrigation}.`;

    const aiRes = await callGeminiText({
      prompt,
      systemInstruction,
      userId: req.user?._id,
      feature: 'carbon_estimate',
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
