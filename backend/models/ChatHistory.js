import mongoose from 'mongoose';

const chatHistorySchema = mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    sessionId: { type: String, default: 'default', index: true },
    clientRequestId: { type: String, index: true },
    message: { type: String, required: true },
    response: { type: String, required: true },
    mode: { type: String, default: 'general' },
    category: { type: String, default: 'general' },
    language: { type: String, default: 'en' },
    groundedSources: [String],
    aiAvailable: { type: Boolean, default: true },
    serviceStatus: { type: String },
    requestId: { type: String },
  },
  {
    timestamps: true,
    toJSON: { virtuals: true },
    toObject: { virtuals: true },
  },
);

chatHistorySchema.index({ userId: 1, sessionId: 1, createdAt: 1 });

const ChatHistory = mongoose.model('ChatHistory', chatHistorySchema);
export default ChatHistory;
