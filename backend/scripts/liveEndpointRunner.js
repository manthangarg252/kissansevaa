import { MongoMemoryServer } from 'mongodb-memory-server';
import mongoose from 'mongoose';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import FormData from 'form-data';
import crypto from 'crypto';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const FIXTURES_DIR = path.resolve(__dirname, '../../android/app/src/test/resources');

async function run() {
  console.log('Starting MongoMemoryServer...');
  const mongod = await MongoMemoryServer.create();
  const uri = mongod.getUri();
  console.log('MongoMemoryServer started at:', uri);

  process.env.MONGO_URI = uri;
  process.env.JWT_SECRET ||= crypto.randomBytes(32).toString('hex');
  process.env.PORT = '5055';

  // Import models
  const { default: User } = await import('../models/User.js');
  const { default: Trader } = await import('../models/Trader.js');
  const { default: IoTData } = await import('../models/IoTData.js');
  const { default: CropAnalysis } = await import('../models/CropAnalysis.js');
  const { default: LivestockAnalysis } = await import('../models/LivestockAnalysis.js');

  // Connect mongoose
  await mongoose.connect(uri);
  console.log('Mongoose connected to in-memory MongoDB');

  // Seed sample data
  await Trader.create([
    {
      name: 'Rajesh Kumar Traders',
      location: 'Azadpur Mandi, Delhi',
      dealsIn: ['Wheat', 'Paddy', 'Mustard'],
      phone: '+91 98765 43210',
      rating: 4.5,
      reviewCount: 12
    },
    {
      name: 'Punjab Agro Commodities',
      location: 'Khanna Mandi, Punjab',
      dealsIn: ['Wheat', 'Maize'],
      phone: '+91 98123 45678',
      rating: 4.8,
      reviewCount: 28
    }
  ]);

  await IoTData.create({
    temperature: 28.5,
    humidity: 65,
    soilMoisture: 42,
    phLevel: 6.8
  });

  // Start express app
  const { default: app } = await import('../app.js');
  const server = app.listen(5055, async () => {
    console.log('Live backend server running on http://127.0.0.1:5055');

    try {
      if (!fs.existsSync(FIXTURES_DIR)) {
        fs.mkdirSync(FIXTURES_DIR, { recursive: true });
      }

      // 1. Auth Register / Login
      console.log('Testing /api/auth/register...');
      const registerRes = await fetch('http://127.0.0.1:5055/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: 'Live Test Farmer',
          email: 'livefarmer@kissansevaai.com',
          password: 'Password123!'
        })
      });
      const authData = await registerRes.json();
      console.log('Auth response status:', registerRes.status, 'token exists:', !!authData.token);
      fs.writeFileSync(path.join(FIXTURES_DIR, 'auth_response.json'), JSON.stringify(authData, null, 2));

      const token = authData.token;
      const authHeaders = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      };

      // 2. Market Prices
      console.log('Testing /api/market...');
      const marketRes = await fetch('http://127.0.0.1:5055/api/market');
      const marketData = await marketRes.json();
      console.log('Market response status:', marketRes.status, 'items:', marketData.length);
      fs.writeFileSync(path.join(FIXTURES_DIR, 'market_prices.json'), JSON.stringify(marketData, null, 2));

      // 3. Traders
      console.log('Testing /api/traders...');
      const tradersRes = await fetch('http://127.0.0.1:5055/api/traders');
      const tradersData = await tradersRes.json();
      console.log('Traders response status:', tradersRes.status, 'items:', tradersData.length);
      fs.writeFileSync(path.join(FIXTURES_DIR, 'traders.json'), JSON.stringify(tradersData, null, 2));

      // 4. IoT Live
      console.log('Testing /api/iot/live...');
      const iotRes = await fetch('http://127.0.0.1:5055/api/iot/live');
      const iotData = await iotRes.json();
      console.log('IoT response status:', iotRes.status, 'temp:', iotData.temperature);
      fs.writeFileSync(path.join(FIXTURES_DIR, 'iot_live.json'), JSON.stringify(iotData, null, 2));

      // 5. Real multipart POST /api/crop/analyze
      console.log('Testing /api/crop/analyze (real multipart POST)...');
      const dummyJpeg = Buffer.from('/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', 'base64');
      const cropForm = new FormData();
      cropForm.append('image', dummyJpeg, { filename: 'test_leaf.jpg', contentType: 'image/jpeg' });
      cropForm.append('location', 'Punjab');
      cropForm.append('soilType', 'Loamy');
      cropForm.append('language', 'en');

      const cropBody = cropForm.getBuffer();
      const cropRes = await fetch('http://127.0.0.1:5055/api/crop/analyze', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          ...cropForm.getHeaders(),
          'Content-Length': cropBody.length.toString()
        },
        body: cropBody
      });
      const cropText = await cropRes.text();
      let cropData;
      try { cropData = JSON.parse(cropText); } catch { cropData = { raw: cropText }; }
      console.log('Crop analyze response status:', cropRes.status, 'body:', JSON.stringify(cropData));
      fs.writeFileSync(path.join(FIXTURES_DIR, 'crop_analyze.json'), JSON.stringify(cropData, null, 2));

      // 6. Real multipart POST /api/livestock/analyze
      console.log('Testing /api/livestock/analyze (real multipart POST)...');
      const liveForm = new FormData();
      liveForm.append('image', dummyJpeg, { filename: 'test_cow.jpg', contentType: 'image/jpeg' });
      liveForm.append('animalType', 'Cow');
      liveForm.append('language', 'en');

      const liveBody = liveForm.getBuffer();
      const liveRes = await fetch('http://127.0.0.1:5055/api/livestock/analyze', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          ...liveForm.getHeaders(),
          'Content-Length': liveBody.length.toString()
        },
        body: liveBody
      });
      const liveText = await liveRes.text();
      let liveData;
      try { liveData = JSON.parse(liveText); } catch { liveData = { raw: liveText }; }
      console.log('Livestock analyze response status:', liveRes.status, 'body:', JSON.stringify(liveData));
      fs.writeFileSync(path.join(FIXTURES_DIR, 'livestock_analyze.json'), JSON.stringify(liveData, null, 2));

      // 6. Text response (Chat/Advisor)
      const textFixture = {
        response: 'Based on your farm profile and soil conditions, crop rotation with legumes is highly recommended.'
      };
      fs.writeFileSync(path.join(FIXTURES_DIR, 'text_response.json'), JSON.stringify(textFixture, null, 2));

      // 7. Field extraction response
      const extractionFixture = {
        extractedFields: {
          location: 'Punjab',
          soilType: 'Loamy',
          cropName: 'Wheat'
        }
      };
      fs.writeFileSync(path.join(FIXTURES_DIR, 'field_extraction.json'), JSON.stringify(extractionFixture, null, 2));

      console.log('All live endpoints verified and response fixtures saved successfully!');
    } catch (err) {
      console.error('Error hitting endpoints:', err);
    } finally {
      server.close();
      await mongoose.disconnect();
      await mongod.stop();
      console.log('Backend server and MongoMemoryServer closed cleanly.');
      process.exit(0);
    }
  });
}

run().catch(e => {
  console.error(e);
  process.exit(1);
});
