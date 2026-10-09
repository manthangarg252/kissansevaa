
import { generateText } from '../services/geminiService.js';

export const estimateCarbon = async (req, res) => {
  try {
    const { farmSize, cropType, method, irrigation, language } = req.body;

    const systemInstruction = `You are a Carbon Credit Consultant for sustainable agriculture. 
      Estimate potential carbon credits and earnings for the given farm profile.
      List specific practices to adopt. 
      Reply strictly in ${language || 'English'}. Use markdown formatting.`;

    const prompt = `Farm Size: ${farmSize} Acres, Crop: ${cropType}, Method: ${method}, Irrigation: ${irrigation}.`;
    
    const response = await generateText(prompt, systemInstruction);
    res.json({ response });
  } catch (error) {
    res.status(500).json({ error: 'Internal server error' });
  }
};
