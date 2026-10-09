
import mongoose from 'mongoose';

const iotDataSchema = mongoose.Schema({
  temperature: { type: Number },
  humidity: { type: Number },
  soilMoisture: { type: Number },
  phLevel: { type: Number },
  timestamp: { type: Date, default: Date.now }
}, { 
  timestamps: true,
  toJSON: { virtuals: true },
  toObject: { virtuals: true }
});

const IoTData = mongoose.model('IoTData', iotDataSchema);
export default IoTData;
