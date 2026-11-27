import express from 'express';
import cors from 'cors';
import mongoose from 'mongoose';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { requestLogger } from './middleware/requestLogger.js';
import { setGeminiUsageSink } from './services/geminiService.js';
import GeminiUsage from './models/GeminiUsage.js';
import authRoutes from './routes/authRoutes.js';
import cropRoutes from './routes/cropRoutes.js';
import loanRoutes from './routes/loanRoutes.js';
import chatRoutes from './routes/chatRoutes.js';
import livestockRoutes from './routes/livestockRoutes.js';
import marketRoutes from './routes/marketRoutes.js';
import schemeRoutes from './routes/schemeRoutes.js';
import carbonRoutes from './routes/carbonRoutes.js';
import traderRoutes from './routes/traderRoutes.js';
import iotRoutes from './routes/iotRoutes.js';
import fieldExtractionRoutes from './routes/fieldExtractionRoutes.js';
import weatherRoutes from './routes/weatherRoutes.js';
import plannerRoutes from './routes/plannerRoutes.js';
import syncRoutes from './routes/syncRoutes.js';
import aiRoutes from './routes/aiRoutes.js';
import callAgentRoutes from './routes/callAgentRoutes.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Ensure uploads directory exists at startup
const uploadsDir = path.join(__dirname, 'uploads');
if (!fs.existsSync(uploadsDir)) {
  fs.mkdirSync(uploadsDir, { recursive: true });
}

// Persist Gemini usage accounting in MongoDB when connected
setGeminiUsageSink(async (entry) => {
  if (GeminiUsage.db?.readyState === 1) {
    await GeminiUsage.create({
      userId: entry.userId || undefined,
      requestId: entry.requestId,
      feature: entry.feature,
      model: entry.model,
      ok: entry.ok,
      code: entry.code,
      latencyMs: entry.latencyMs,
      promptTokens: entry.promptTokens,
      candidateTokens: entry.candidateTokens,
      totalTokens: entry.totalTokens,
    });
  }
});

const app = express();
app.set('trust proxy', 1);

app.use(cors());
app.use(
  express.json({
    limit: '10mb',
    verify: (req, _res, buf) => {
      req.rawBody = buf;
    },
  }),
);

// Dev-only JSONL request/response logger
app.use(requestLogger);

// Serving uploaded images as static assets
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));

// Unauthenticated health probe for container orchestration and reverse proxy checks
app.get('/health', (_req, res) => {
  const mongoConnected = mongoose.connection.readyState === 1;
  res.status(mongoConnected ? 200 : 503).json({
    ok: mongoConnected,
    status: mongoConnected ? 'healthy' : 'degraded',
    service: 'kissansevaai-backend',
    mongodb: mongoConnected ? 'connected' : 'disconnected',
    uptimeSeconds: Math.floor(process.uptime()),
  });
});

// Core API Routes
app.use('/api/auth', authRoutes);
app.use('/api/crop', cropRoutes);
app.use('/api/loans', loanRoutes);
app.use('/api/chat', chatRoutes);
app.use('/api/livestock', livestockRoutes);
app.use('/api/market', marketRoutes);
app.use('/api/schemes', schemeRoutes);
app.use('/api/carbon', carbonRoutes);
app.use('/api/traders', traderRoutes);
app.use('/api/iot', iotRoutes);
app.use('/api/extract-fields', fieldExtractionRoutes);
app.use('/api/weather', weatherRoutes);
app.use('/api/planner', plannerRoutes);
app.use('/api/sync', syncRoutes);
app.use('/api/ai', aiRoutes);
app.use('/api/integrations/call-agent', callAgentRoutes);

export default app;
