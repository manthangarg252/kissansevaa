
import { generateText } from '../services/geminiService.js';
import ChatHistory from '../models/ChatHistory.js';

export const handleChat = async (req, res) => {
  try {
    const { message, language, mode } = req.body;
    const userId = req.user._id;

    const systemInstruction = `You are an expert agriculture AI assistant named KissanSevaAI. 
      You are responding to a farmer. 
      Mode: ${mode || 'General Assistant'}. 
      Language: ${language || 'English'}.
      Guidelines:
      - Be polite and helpful.
      - Use bullet points for clarity.
      - Keep responses concise and actionable.
      - Reply strictly in the requested language.`;

    const aiResponse = await generateText(message, systemInstruction);

    // Save to MongoDB
    await ChatHistory.create({
      userId,
      message,
      response: aiResponse,
      mode,
      language
    });

    res.json({ response: aiResponse });
  } catch (error) {
    console.error('Chat Error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
};
