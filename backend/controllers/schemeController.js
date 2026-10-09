
import { generateText } from '../services/geminiService.js';

export const recommendSchemes = async (req, res) => {
  try {
    const { state, farmSize, crop, soilType, landType, language } = req.body;

    const systemInstruction = `You are a Government Schemes Expert for Indian Farmers. 
      Based on user details, recommend the top 3-5 relevant government schemes (PM-KISAN, PMFBY, KCC, etc.).
      Include eligibility and how to apply. 
      Reply strictly in ${language || 'English'}. Use bullet points.`;

    const prompt = `State: ${state}, Farm Size: ${farmSize} Acres, Crop: ${crop}, Soil: ${soilType}, Land: ${landType}.`;
    
    const response = await generateText(prompt, systemInstruction);
    res.json({ response });
  } catch (error) {
    res.status(500).json({ error: 'Internal server error' });
  }
};
