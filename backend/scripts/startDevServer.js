import { MongoMemoryServer } from 'mongodb-memory-server';
import mongoose from 'mongoose';
import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

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

  await mongoose.connect(uri);
  console.log('[Dev Server] Connected to in-memory database.');

  // Seed default test user
  const user = await User.create({
    name: 'Dev Farmer',
    email: 'farmer@kissan.com',
    password: 'Password123!',
    phone: '+91 98765 00000',
    languagePreference: 'en'
  });
  console.log('[Dev Server] Test user created: farmer@kissan.com / Password123! (id: ' + user._id + ')');

  // Seed traders
  await Trader.create([
    {
      name: 'Azadpur Agri Traders',
      location: 'Azadpur Mandi, Delhi',
      dealsIn: ['Wheat', 'Rice', 'Mustard'],
      phone: '+91 98765 43210',
      rating: 4.7,
      reviewCount: 35,
      imageUrl: 'https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&q=80&w=800'
    },
    {
      name: 'Punjab Mandi Corp',
      location: 'Khanna Mandi, Punjab',
      dealsIn: ['Wheat', 'Maize', 'Cotton'],
      phone: '+91 98123 45678',
      rating: 4.9,
      reviewCount: 42,
      imageUrl: 'https://images.unsplash.com/photo-1574943320219-553eb213f72d?auto=format&fit=crop&q=80&w=800'
    }
  ]);

  // Seed IoT reading
  await IoTData.create({
    temperature: 27.8,
    humidity: 64,
    soilMoisture: 45,
    phLevel: 6.7
  });

  // Seed initial crop history
  await CropAnalysis.create({
    userId: user._id,
    cropName: 'Wheat',
    diseaseName: 'Yellow Rust',
    confidence: 0.92,
    severity: 'Medium',
    summary: 'Yellow rust pustules observed on upper leaf blades.',
    treatment: {
      organic: ['Neem oil spray 5ml/L', 'Trichoderma viride bio-agent'],
      chemical: ['Propiconazole 25% EC 1ml/L', 'Mancozeb 75% WP 2g/L']
    },
    prevention: ['Use resistant varieties (HD-3086)', 'Avoid late sowing'],
    imagePath: 'uploads/sample_leaf.jpg',
    source: 'gemini'
  });

  const { default: app } = await import('../app.js');
  app.listen(5050, () => {
    console.log('[Dev Server] Backend running at http://0.0.0.0:5050 (accessible at http://10.0.2.2:5050 from emulator)');
  });
}

start().catch(err => {
  console.error('[Dev Server] Fatal error:', err);
  process.exit(1);
});
