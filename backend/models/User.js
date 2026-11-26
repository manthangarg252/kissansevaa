import mongoose from 'mongoose';
import bcrypt from 'bcryptjs';

const userSchema = mongoose.Schema(
  {
    name: { type: String, required: true },
    email: { type: String, required: true, unique: true },
    password: { type: String, required: true },
    phone: { type: String },
    languagePreference: { type: String, default: 'en' },
    // Extended farmer profile for weather, crop planner, schemes & mandi personalization
    farmName: { type: String, default: '' },
    state: { type: String, default: '' },
    district: { type: String, default: '' },
    city: { type: String, default: '' },
    village: { type: String, default: '' },
    country: { type: String, default: '' },
    postalCode: { type: String, default: '' },
    latitude: { type: Number, default: null },
    longitude: { type: Number, default: null },
    locationSource: { type: String, default: '' },
    locationUpdatedAt: { type: Date, default: null },
    farmSizeAcres: { type: Number, default: null },
    soilType: { type: String, default: '' },
    irrigationType: { type: String, default: '' },
    primaryCrops: [{ type: String }],
  },
  {
    timestamps: true,
    toJSON: { virtuals: true },
    toObject: { virtuals: true },
  },
);

userSchema.pre('save', async function (next) {
  if (!this.isModified('password')) return next();
  const salt = await bcrypt.genSalt(10);
  this.password = await bcrypt.hash(this.password, salt);
});

userSchema.methods.matchPassword = async function (enteredPassword) {
  return await bcrypt.compare(enteredPassword, this.password);
};

const User = mongoose.model('User', userSchema);
export default User;
