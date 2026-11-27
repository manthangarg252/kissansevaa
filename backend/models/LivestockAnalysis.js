import mongoose from 'mongoose';

const livestockAnalysisSchema = mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    clientRequestId: { type: String, index: true },
    animalType: { type: String, required: true },
    animalAge: { type: String, default: '' },
    symptomDuration: { type: String, default: '' },
    feverStatus: { type: String, default: 'unknown' },
    appetiteStatus: { type: String, default: 'unknown' },
    milkYieldChange: { type: String, default: 'unknown' },
    lesionsOrUlcers: { type: Boolean, default: false },
    isolatedFromHerd: { type: Boolean, default: false },
    symptomsNotes: { type: String, default: '' },
    language: { type: String, default: 'en' },
    conditionName: { type: String },
    confidence: { type: Number, default: null },
    severity: {
      type: String,
      enum: ['Low', 'Medium', 'High', 'Critical', 'Unknown'],
      default: 'Unknown',
    },
    urgentVetEscalation: { type: Boolean, default: false },
    escalationReason: { type: String, default: '' },
    prescriptionsWithheld: { type: Boolean, default: true },
    safetyNotice: { type: String },
    source: {
      type: String,
      enum: ['model', 'gemini', 'local_triage', 'service_unavailable', 'mock'],
      default: 'gemini',
    },
    aiAvailable: { type: Boolean, default: true },
    serviceStatus: { type: String },
    requestId: { type: String },
    symptoms: [String],
    visibleObservations: [String],
    possibleConcerns: [String],
    missingInformation: [String],
    warningSigns: [String],
    veterinaryUrgency: { type: String, default: '' },
    uncertaintyNote: { type: String, default: '' },
    immediateAction: { type: String },
    longTermCare: { type: String },
    vetConsultationAdvice: { type: String },
    imagePath: { type: String },
  },
  {
    timestamps: true,
    toJSON: { virtuals: true },
    toObject: { virtuals: true },
  },
);

livestockAnalysisSchema.index({ userId: 1, createdAt: -1 });

const LivestockAnalysis = mongoose.model('LivestockAnalysis', livestockAnalysisSchema);
export default LivestockAnalysis;
