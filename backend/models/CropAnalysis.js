import mongoose from 'mongoose';

const secondOpinionSchema = mongoose.Schema(
  {
    requested: { type: Boolean, default: false },
    farmerConsented: { type: Boolean, default: false },
    consentTimestamp: { type: Date },
    source: { type: String },
    cropName: { type: String },
    diseaseName: { type: String },
    confidence: { type: Number, default: null },
    isPlantLeaf: { type: Boolean, default: true },
    summary: { type: String },
    severity: { type: String },
    treatment: {
      organic: [String],
      chemical: [String],
    },
    prevention: [String],
    warning: { type: String },
    requestId: { type: String },
    serviceStatus: { type: String },
  },
  { _id: false },
);

const cropAnalysisSchema = mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    clientRequestId: { type: String, index: true },
    cropName: { type: String },
    diseaseName: { type: String, required: true },
    confidence: { type: Number, default: null },
    top_cosine_similarity: { type: Number },
    neighbor_agreement: { type: Number },
    needs_review: { type: Boolean, default: false },
    treatmentWithheld: { type: Boolean, default: false },
    confidence_type: { type: String },
    predictionSource: { type: String },
    advisorySource: { type: String },
    matches: [
      {
        class_name: String,
        cosine_similarity: Number,
      },
    ],
    warning: { type: String },
    severity: { type: String },
    summary: { type: String },
    treatment: {
      organic: [String],
      chemical: [String],
    },
    prevention: [String],
    imagePath: { type: String },
    cropHint: { type: String, default: '' },
    symptomDescription: { type: String, default: '' },
    source: {
      type: String,
      enum: ['dinov2', 'model', 'gemini', 'mock', 'model_assets_missing', 'service_unavailable'],
    },
    secondOpinion: secondOpinionSchema,
    reconciliationStatus: {
      type: String,
      enum: ['primary_only', 'agreed', 'disagreed', 'unsupported_or_non_leaf', 'ai_unavailable'],
      default: 'primary_only',
    },
    reconciliationNote: { type: String },
    v3Shadow: {
      predictedClass: String,
      cropGateAccepted: Boolean,
      calibratedConfidence: Number,
      hybridDisagreement: Boolean,
    },
  },
  {
    timestamps: true,
    toJSON: { virtuals: true },
    toObject: { virtuals: true },
  },
);

cropAnalysisSchema.index({ userId: 1, createdAt: -1 });

const CropAnalysis = mongoose.model('CropAnalysis', cropAnalysisSchema);
export default CropAnalysis;
