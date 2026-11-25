import mongoose from 'mongoose';

const plannerTaskSchema = mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    planId: { type: mongoose.Schema.Types.ObjectId, ref: 'CropPlan', required: true, index: true },
    clientRequestId: { type: String, index: true },
    dayOffset: { type: Number, required: true, min: 0, max: 30 },
    scheduledDate: { type: String, required: true }, // YYYY-MM-DD
    title: { type: String, required: true },
    description: { type: String, default: '' },
    category: {
      type: String,
      enum: ['irrigation', 'fertilizer', 'spray', 'weeding', 'inspection', 'harvest', 'soil'],
      default: 'inspection',
    },
    priority: {
      type: String,
      enum: ['low', 'medium', 'high'],
      default: 'medium',
    },
    status: {
      type: String,
      enum: ['pending', 'completed', 'skipped'],
      default: 'pending',
    },
    completedAt: { type: Date, default: null },
    weatherAdjusted: { type: Boolean, default: false },
    weatherReason: { type: String, default: '' },
    reminderEnabled: { type: Boolean, default: true },
  },
  {
    timestamps: true,
    toJSON: { virtuals: true },
    toObject: { virtuals: true },
  },
);

plannerTaskSchema.index({ userId: 1, scheduledDate: 1 });

const PlannerTask = mongoose.model('PlannerTask', plannerTaskSchema);
export default PlannerTask;
