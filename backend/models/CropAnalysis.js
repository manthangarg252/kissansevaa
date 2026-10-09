
import mongoose from 'mongoose';

const cropAnalysisSchema = mongoose.Schema({
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  cropName: { type: String },
  diseaseName: { type: String, required: true },
  confidence: { type: Number },
  severity: { type: String },
  summary: { type: String },
  treatment: {
    organic: [String],
    chemical: [String]
  },
  prevention: [String],
  imagePath: { type: String },
  source: { type: String, enum: ['model', 'gemini', 'mock'] }
}, { 
  timestamps: true,
  toJSON: { virtuals: true },
  toObject: { virtuals: true }
});

const CropAnalysis = mongoose.model('CropAnalysis', cropAnalysisSchema);
export default CropAnalysis;
