import { MongoMemoryServer } from 'mongodb-memory-server';
import mongoose from 'mongoose';
import crypto from 'node:crypto';
import dotenv from 'dotenv';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, '../.env') });

async function start() {
  console.log('[Dev Server] Starting in-memory MongoDB...');
  const mongod = await MongoMemoryServer.create();
  const uri = mongod.getUri();
  console.log('[Dev Server] Mongo URI:', uri);

  process.env.MONGO_URI = uri;
  process.env.JWT_SECRET ||= crypto.randomBytes(32).toString('hex');
  process.env.PORT = '5050';

  const { default: User } = await import('../models/User.js');
  const { default: Trader } = await import('../models/Trader.js');
  const { default: IoTData } = await import('../models/IoTData.js');
  const { default: CropAnalysis } = await import('../models/CropAnalysis.js');
  const { createPlanWithTasks } = await import('../services/cropPlannerService.js');

  await mongoose.connect(uri);
  console.log('[Dev Server] Connected to in-memory database.');

  const user = await User.create({
    name: 'Dev Farmer',
    email: 'farmer@kissan.com',
    password: 'Password123!',
    phone: '+91 98765 00000',
    languagePreference: 'en',
    state: 'Maharashtra',
    district: 'Nagpur',
    latitude: 21.1458,
    longitude: 79.0882,
    farmSizeAcres: 4.0,
    soilType: 'Black',
    irrigationType: 'Drip / Canal',
    primaryCrops: ['Wheat', 'Cotton', 'Tomato'],
  });
  console.log('[Dev Server] Test user created: farmer@kissan.com / Password123! (id: ' + user._id + ')');

  await Trader.create([
    {
      name: 'Azadpur Agri Traders',
      location: 'Azadpur Mandi, Delhi',
      dealsIn: ['Wheat', 'Rice', 'Mustard'],
      phone: '+91 98765 43210',
      rating: 4.7,
      reviewCount: 35,
      imageUrl: 'https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&q=80&w=800',
    },
    {
      name: 'Punjab Mandi Corp',
      location: 'Khanna Mandi, Punjab',
      dealsIn: ['Wheat', 'Maize', 'Cotton'],
      phone: '+91 98123 45678',
      rating: 4.9,
      reviewCount: 42,
      imageUrl: 'https://images.unsplash.com/photo-1574943320219-553eb213f72d?auto=format&fit=crop&q=80&w=800',
    },
  ]);

  await IoTData.create({
    temperature: 27.8,
    humidity: 64,
    soilMoisture: 45,
    phLevel: 6.7,
  });

  await CropAnalysis.create({
    userId: user._id,
    cropName: 'Wheat',
    diseaseName: 'Yellow Rust',
    confidence: null,
    top_cosine_similarity: 0.74,
    neighbor_agreement: 0.80,
    needs_review: false,
    treatmentWithheld: false,
    predictionSource: 'dinov2',
    advisorySource: 'local_reference',
    severity: 'Medium',
    summary: 'Seeded historical record from V1 DINOv2 scan.',
    treatment: {
      organic: ['Neem oil spray 5ml/L', 'Trichoderma viride bio-agent'],
      chemical: ['Propiconazole 25% EC 1ml/L', 'Mancozeb 75% WP 2g/L'],
    },
    prevention: ['Use resistant varieties (HD-3086)', 'Avoid late sowing'],
    imagePath: 'uploads/sample_leaf.jpg',
    source: 'dinov2',
  });

  // Seed initial 7-day crop plan (rules_only so startup never burns Gemini RPD quota)
  await createPlanWithTasks({
    userId: user._id,
    cropName: 'Wheat',
    variety: 'HD-3086',
    stage: 'vegetative',
    soilType: 'Black',
    irrigationType: 'Drip / Canal',
    farmSizeAcres: 4.0,
    locationName: 'Nagpur, Maharashtra',
    latitude: 21.1458,
    longitude: 79.0882,
    includeAi: false,
  });

  const { default: app } = await import('../app.js');
  app.listen(5050, () => {
    console.log('[Dev Server] Backend running at http://0.0.0.0:5050 (accessible at http://10.0.2.2:5050 from emulator)');
  });
}

start().catch((err) => {
  console.error('[Dev Server] Fatal error:', err);
  process.exit(1);
});
