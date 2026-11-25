import { callGeminiStructured, sendAiErrorResponse } from '../services/geminiService.js';
import { Type } from '@google/genai';
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
    riskNotes: { type: Type.ARRAY, items: { type: Type.STRING } },
  },
  required: [
    'eligibilityLevel',
    'recommendedLoan',
    'amountRange',
    'estimatedInterestRange',
    'documents',
    'nextSteps',
    'riskNotes',
  ],
};

export const getLoanAdvice = async (req, res) => {
  try {
    const profile = req.body;
    const userId = req.user._id;

    const prompt = `Act as an Indian Agriculture Loan Advisor. Evaluate this farmer profile: ${JSON.stringify(profile)}.
Recommend realistic official Indian agricultural loan options (e.g., KCC, NABARD term loans, AIF), approximate interest ranges, and document checklist. Return structured JSON.`;

    const aiRes = await callGeminiStructured({
      prompt,
      schema: loanSchema,
      userId,
      feature: 'loan_advisor',
    });

    if (LoanRequest.db?.readyState === 1) {
      await LoanRequest.create({
        userId,
        profile,
        recommendation: aiRes.data,
      });
    }

    res.json({
      ...aiRes.data,
      aiAvailable: true,
      requestId: aiRes.requestId,
    });
  } catch (error) {
    return sendAiErrorResponse(res, error);
  }
};
