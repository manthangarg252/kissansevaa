import mongoose from 'mongoose';
import crypto from 'node:crypto';
import dotenv from 'dotenv';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { ensurePersistentMongo } from './startPersistentMongo.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, '../.env') });

async function seedIfEmpty({
  User,
  Trader,
  IoTData,
  CropAnalysis,
  CropPlan,
  createPlanWithTasks,
}) {
  let user = await User.findOne({ email: 'farmer@kissan.com' });
  if (!user) {
    user = await User.create({
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
    console.log(`[Dev Server] Seeded default user: farmer@kissan.com (id: ${user._id})`);
  } else {
    console.log(`[Dev Server] Preserving existing farmer account: farmer@kissan.com (id: ${user._id})`);
  }

  if ((await Trader.countDocuments()) === 0) {
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
  }

  if ((await IoTData.countDocuments()) === 0) {
    await IoTData.create({
      temperature: 27.8,
      humidity: 64,
      soilMoisture: 45,
      phLevel: 6.7,
    });
  }

  if ((await CropAnalysis.countDocuments({ userId: user._id })) === 0) {
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
  }

  if ((await CropPlan.countDocuments({ userId: user._id })) === 0) {
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
  }
}

async function start() {
  const useMemoryDb =
    process.env.USE_IN_MEMORY_DB === 'true' ||
    process.argv.includes('--memory') ||
    process.env.NODE_ENV === 'test';

  let uri;
  if (useMemoryDb) {
    const { MongoMemoryServer } = await import('mongodb-memory-server');
    console.log('[Dev Server] Starting ephemeral MongoMemoryServer (--memory / test mode)...');
    const mongod = await MongoMemoryServer.create();
    uri = mongod.getUri();
  } else {
    console.log('[Dev Server] Ensuring persistent local MongoDB...');
    const mongoInfo = await ensurePersistentMongo({
      mongoUri: process.env.MONGO_URI || 'mongodb://127.0.0.1:27017/kissansevaai',
    });
    uri = mongoInfo.uri;
    console.log(
      `[Dev Server] Persistent MongoDB ready (mode=${mongoInfo.mode}${mongoInfo.dataDir ? `, dbpath=${mongoInfo.dataDir}` : ''})`,
    );
  }

  process.env.MONGO_URI = uri;
  process.env.JWT_SECRET ||= crypto.randomBytes(32).toString('hex');
  // macOS ControlCenter occupies :5000 by default; dev server standardizes on :5050 for Android & emulator
  const port = Number(process.env.DEV_PORT || 5050);
  process.env.PORT = String(port);

  const { default: User } = await import('../models/User.js');
  const { default: Trader } = await import('../models/Trader.js');
  const { default: IoTData } = await import('../models/IoTData.js');
  const { default: CropAnalysis } = await import('../models/CropAnalysis.js');
  const { default: CropPlan } = await import('../models/CropPlan.js');
  const { createPlanWithTasks } = await import('../services/cropPlannerService.js');

  await mongoose.connect(uri);
  console.log('[Dev Server] Connected cleanly without resetting existing data.');

  await seedIfEmpty({ User, Trader, IoTData, CropAnalysis, CropPlan, createPlanWithTasks });

  const { default: app } = await import('../app.js');
  app.listen(port, () => {
    console.log(`[Dev Server] Backend running at http://0.0.0.0:${port} (accessible at http://10.0.2.2:${port} from emulator)`);
  });
}

start().catch((err) => {
  console.error('[Dev Server] Fatal error:', err);
  process.exit(1);
});
