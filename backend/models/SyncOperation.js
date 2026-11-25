import mongoose from 'mongoose';

const syncOperationSchema = mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    operationId: { type: String, required: true },
    opType: { type: String, required: true },
    status: { type: String, enum: ['applied', 'failed'], default: 'applied' },
    resultId: { type: String, default: null },
    summary: { type: String, default: '' },
    processedAt: { type: Date, default: Date.now },
  },
  {
    timestamps: true,
    toJSON: { virtuals: true },
    toObject: { virtuals: true },
  },
);

syncOperationSchema.index({ userId: 1, operationId: 1 }, { unique: true });

const SyncOperation = mongoose.model('SyncOperation', syncOperationSchema);
export default SyncOperation;
