
import IoTData from '../models/IoTData.js';
import { generateText } from '../services/geminiService.js';

export const getLiveStats = async (req, res) => {
  try {
    const latest = await IoTData.findOne().sort({ createdAt: -1 });
    if (!latest) {
      return res.json({
        temperature: 28.5,
        humidity: 65,
        soilMoisture: 42,
        phLevel: 6.8,
        timestamp: new Date().toISOString()
      });
    }
    res.json({
        temperature: latest.temperature,
        humidity: latest.humidity,
        soilMoisture: latest.soilMoisture,
        phLevel: latest.phLevel,
        timestamp: latest.timestamp
    });
  } catch (error) {
    res.status(500).json({ error: 'Error fetching IoT data' });
  }
};

export const updateStats = async (req, res) => {
  try {
    const data = req.body;
    await IoTData.create({
      temperature: data.temperature,
      humidity: data.humidity,
      soilMoisture: data.soilMoisture,
      phLevel: data.phLevel
    });
    res.json({ status: 'success' });
  } catch (error) {
    res.status(500).json({ error: 'Error updating IoT data' });
  }
};

export const getInsights = async (req, res) => {
  try {
    const { sensorData, language } = req.body;

    if (!sensorData) {
      return res.status(400).json({ error: 'sensorData object is required' });
    }

    const systemInstruction = `You are a smart farming IoT data analyst.
      Interpret sensor readings and provide 3 concise actionable recommendations.
      Consider optimal ranges: temperature 20-35°C, humidity 50-80%,
      soil moisture 30-60%, pH 6.0-7.5.
      Reply strictly in ${language || 'English'}.
      Use plain text only. No Markdown. Number points 1., 2., 3.`;

    const prompt = `Current sensor readings: Temperature: ${sensorData.temperature}°C, Humidity: ${sensorData.humidity}%, Soil Moisture: ${sensorData.soilMoisture}%, pH Level: ${sensorData.phLevel}.`;

    const response = await generateText(prompt, systemInstruction);
    res.json({ response });
  } catch (error) {
    console.error('IoT Insights Error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
};
