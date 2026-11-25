import mongoose from 'mongoose';

const marketFavoriteSchema = mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    crop: { type: String, required: true },
    market: { type: String, default: '' },
    state: { type: String, default: '' },
    category: { type: String, default: 'crop' },
    clientRequestId: { type: String },
  },
  {
    timestamps: true,
    toJSON: { virtuals: true },
    toObject: { virtuals: true },
  },
);

marketFavoriteSchema.index({ userId: 1, crop: 1, market: 1 }, { unique: true });

const MarketFavorite = mongoose.model('MarketFavorite', marketFavoriteSchema);
export default MarketFavorite;
