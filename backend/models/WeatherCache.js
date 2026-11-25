import mongoose from 'mongoose';

const weatherCacheSchema = mongoose.Schema(
  {
    locationKey: { type: String, required: true, unique: true },
    locationName: { type: String, default: '' },
    state: { type: String, default: '' },
    district: { type: String, default: '' },
    latitude: { type: Number, required: true },
    longitude: { type: Number, required: true },
    provider: { type: String, default: 'open-meteo' },
    current: { type: Object, required: true },
    daily: [{ type: Object }],
    hourlyNext24: [{ type: Object }],
    advisories: [{ type: Object }],
    aiSummary: { type: String, default: null },
    aiAvailable: { type: Boolean, default: false },
    aiServiceStatus: { type: String, default: null },
    fetchedAt: { type: Date, required: true },
    expiresAt: { type: Date, required: true },
  },
  {
    timestamps: true,
    toJSON: { virtuals: true },
    toObject: { virtuals: true },
  },
);

const WeatherCache = mongoose.model('WeatherCache', weatherCacheSchema);
export default WeatherCache;
