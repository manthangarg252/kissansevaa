
import { generateStructuredJSON } from '../services/geminiService.js';
import { Type } from "@google/genai";
import LoanRequest from '../models/LoanRequest.js';

const loanSchema = {
  type: Type.OBJECT,
  properties: {
    eligibilityLevel: { type: Type.STRING, enum: ['High', 'Medium', 'Low'] },
    recommendedLoan: { type: Type.STRING },
    amountRange: { type: Type.STRING },
    estimatedInterestRange: { type: Type.STRING },
    documents: { type: Type.ARRAY, items: { type: Type.STRING } },
    nextSteps: { type: Type.ARRAY, items: { type: Type.STRING } },
    riskNotes: { type: Type.ARRAY, items: { type: Type.STRING } }
  },
  required: ['eligibilityLevel', 'recommendedLoan', 'amountRange', 'estimatedInterestRange', 'documents', 'nextSteps', 'riskNotes']
};

export const getLoanAdvice = async (req, res) => {
  try {
    const profile = req.body;
    const userId = req.user._id;

    const prompt = `Act as an Indian Agriculture Loan Advisor. Evaluate this farmer profile: ${JSON.stringify(profile)}.
    Recommend the best loan options, interest rates, and document checklist. Return structured JSON.`;

    const result = await generateStructuredJSON(prompt, loanSchema);

    // Save request to MongoDB
    await LoanRequest.create({
      userId,
      profile,
      recommendation: result
    });

    res.json(result);
  } catch (error) {
    console.error('Loan Advisor Error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
};
