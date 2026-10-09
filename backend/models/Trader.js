
import mongoose from 'mongoose';

const traderSchema = mongoose.Schema({
  name: { type: String, required: true },
  location: { type: String, required: true },
  dealsIn: [{ type: String }],
  phone: { type: String, required: true },
  rating: { type: Number, default: 0 },
  reviewCount: { type: Number, default: 0 },
  imageUrl: { type: String }
}, { 
  timestamps: true,
  toJSON: { virtuals: true },
  toObject: { virtuals: true }
});

const Trader = mongoose.model('Trader', traderSchema);
export default Trader;
