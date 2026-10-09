
import mongoose from 'mongoose';

const loanSchema = mongoose.Schema({
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  loanType: { type: String, required: true },
  amount: { type: Number, required: true },
  tenureMonths: { type: Number, required: true },
  status: { type: String, enum: ['Pending', 'Approved', 'Rejected'], default: 'Pending' },
  purpose: { type: String },
  landSize: { type: Number },
  cropType: { type: String }
}, { 
  timestamps: true,
  toJSON: { virtuals: true },
  toObject: { virtuals: true }
});

const Loan = mongoose.model('Loan', loanSchema);
export default Loan;
