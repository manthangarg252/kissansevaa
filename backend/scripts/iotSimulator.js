
import fetch from 'node-fetch';

const API_URL = 'http://localhost:5000/api/iot/update';

function generateRandomStats() {
  return {
    temperature: +(24 + Math.random() * 8).toFixed(1),
    humidity: +(55 + Math.random() * 15).toFixed(0),
    soilMoisture: +(40 + Math.random() * 20).toFixed(0),
    phLevel: +(6.2 + Math.random() * 1).toFixed(1)
  };
}

async function simulate() {
  const stats = generateRandomStats();
  try {
    const res = await fetch(API_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(stats)
    });
    console.log('Stats updated:', stats);
  } catch (err) {
    console.error('Simulation update failed:', err.message);
  }
}

console.log('Starting IoT Simulator...');
setInterval(simulate, 5000);
