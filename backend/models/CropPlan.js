import mongoose from 'mongoose';

const cropPlanSchema = mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    clientRequestId: { type: String, index: true },
    cropName: { type: String, required: true },
    variety: { type: String, default: '' },
    stage: {
      type: String,
      enum: ['pre_sowing', 'sowing', 'vegetative', 'flowering', 'fruiting', 'maturity', 'harvest'],
      default: 'vegetative',
    },
    soilType: { type: String, default: 'Loamy' },
    irrigationType: { type: String, default: 'Drip / Canal' },
    farmSizeAcres: { type: Number, default: 1.0 },
    locationName: { type: String, default: 'India' },
    latitude: { type: Number, default: null },
    longitude: { type: Number, default: null },
    startDate: { type: Date, default: Date.now },
    status: {
      type: String,
      enum: ['active', 'completed', 'archived'],
      default: 'active',
    },
    generationSource: {
      type: String,
      enum: ['rules_only', 'rules+gemini'],
      default: 'rules_only',
    },
    aiAdvisoryNote: { type: String, default: null },
  },
  {
    timestamps: true,
    toJSON: { virtuals: true },
    toObject: { virtuals: true },
  },
);

cropPlanSchema.index({ userId: 1, createdAt: -1 });

const CropPlan = mongoose.model('CropPlan', cropPlanSchema);
export default CropPlan;
