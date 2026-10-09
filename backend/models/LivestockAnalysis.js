
import mongoose from 'mongoose';

const livestockAnalysisSchema = mongoose.Schema({
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  animalType: { type: String },
  conditionName: { type: String },
  confidence: { type: Number },
  source: { type: String, enum: ['model', 'gemini', 'mock'], default: 'mock' },
  symptoms: [String],
  immediateAction: { type: String },
  longTermCare: { type: String },
  vetConsultationAdvice: { type: String },
  imagePath: { type: String }
}, { 
  timestamps: true,
  toJSON: { virtuals: true },
  toObject: { virtuals: true }
});

const LivestockAnalysis = mongoose.model('LivestockAnalysis', livestockAnalysisSchema);
export default LivestockAnalysis;
