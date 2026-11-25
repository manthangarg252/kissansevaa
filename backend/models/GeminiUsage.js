import mongoose from 'mongoose';

const geminiUsageSchema = mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: false },
    requestId: { type: String, required: true, index: true },
    feature: { type: String, required: true },
    model: { type: String, required: true },
    ok: { type: Boolean, required: true },
    code: { type: String, default: 'ok' },
    latencyMs: { type: Number, default: 0 },
    promptTokens: { type: Number, default: 0 },
    candidateTokens: { type: Number, default: 0 },
    totalTokens: { type: Number, default: 0 },
  },
  { timestamps: true },
);

const GeminiUsage = mongoose.model('GeminiUsage', geminiUsageSchema);
export default GeminiUsage;
