import mongoose from 'mongoose';

const savedSchemeSchema = mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    schemeId: { type: String, required: true },
    status: {
      type: String,
      enum: ['saved', 'applied', 'dismissed'],
      default: 'saved',
    },
    notes: { type: String, default: '' },
    clientRequestId: { type: String },
  },
  {
    timestamps: true,
    toJSON: { virtuals: true },
    toObject: { virtuals: true },
  },
);

savedSchemeSchema.index({ userId: 1, schemeId: 1 }, { unique: true });

const SavedScheme = mongoose.model('SavedScheme', savedSchemeSchema);
export default SavedScheme;
