import mongoose from 'mongoose';

const cropAnalysisSchema = mongoose.Schema({
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  cropName: { type: String },
  diseaseName: { type: String, required: true },
  confidence: { type: Number },
  top_cosine_similarity: { type: Number },
  neighbor_agreement: { type: Number },
  needs_review: { type: Boolean, default: false },
  matches: [{
    class_name: String,
    cosine_similarity: Number
  }],
  warning: { type: String },
  severity: { type: String },
  summary: { type: String },
  treatment: {
    organic: [String],
    chemical: [String]
  },
  prevention: [String],
  imagePath: { type: String },
  source: { type: String, enum: ['dinov2', 'model', 'gemini', 'mock', 'model_assets_missing'] }
}, { 
  timestamps: true,
  toJSON: { virtuals: true },
  toObject: { virtuals: true }
});

const CropAnalysis = mongoose.model('CropAnalysis', cropAnalysisSchema);
export default CropAnalysis;
