
import mongoose from 'mongoose';

const loanRequestSchema = mongoose.Schema({
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  profile: { type: Object, required: true },
  recommendation: { type: Object, required: true }
}, { 
  timestamps: true,
  toJSON: { virtuals: true },
  toObject: { virtuals: true }
});

const LoanRequest = mongoose.model('LoanRequest', loanRequestSchema);
export default LoanRequest;
