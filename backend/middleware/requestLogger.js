
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const LOG_DIR = path.join(__dirname, '..', 'logs');

// Ensure log directory exists on startup
if (!fs.existsSync(LOG_DIR)) {
  fs.mkdirSync(LOG_DIR, { recursive: true });
}

const SENSITIVE_KEYS = new Set([
  'password', 'token', 'authorization', 'cookie',
  'jwt', 'api_key', 'apikey', 'secret'
]);

const sanitizeObj = (obj) => {
  if (!obj || typeof obj !== 'object') return obj;
  if (Array.isArray(obj)) return obj.map(sanitizeObj);
  const cleaned = {};
  for (const [key, value] of Object.entries(obj)) {
    if (SENSITIVE_KEYS.has(key.toLowerCase())) {
      cleaned[key] = '[REDACTED]';
    } else if (typeof value === 'object' && value !== null) {
      cleaned[key] = sanitizeObj(value);
    } else {
      cleaned[key] = value;
    }
  }
  return cleaned;
};

const pathToFilename = (reqPath) => {
  return reqPath
    .replace(/^\/api\//, '')
    .replace(/\//g, '_')
    .replace(/[^a-zA-Z0-9_-]/g, '') + '.jsonl';
};

/**
 * Dev-only request/response logger.
 * Writes JSONL per endpoint (one file per route path).
 * Strips auth headers, tokens, passwords.
 * Skipped entirely when NODE_ENV=production.
 */
export const requestLogger = (req, res, next) => {
  if (process.env.NODE_ENV === 'production') return next();

  const startTime = Date.now();
  const originalJson = res.json.bind(res);

  res.json = (body) => {
    const entry = {
      timestamp: new Date().toISOString(),
      method: req.method,
      path: req.originalUrl,
      requestBody: sanitizeObj(req.body),
      statusCode: res.statusCode,
      responseBody: body,
      latencyMs: Date.now() - startTime,
    };

    // Log file metadata without the actual image data
    if (req.file) {
      entry.requestFile = {
        fieldname: req.file.fieldname,
        originalname: req.file.originalname,
        mimetype: req.file.mimetype,
        size: req.file.size,
      };
    }

    if (Object.keys(req.query).length) {
      entry.query = req.query;
    }

    const filename = pathToFilename(req.path);
    const filepath = path.join(LOG_DIR, filename);

    try {
      fs.appendFileSync(filepath, JSON.stringify(entry) + '\n');
    } catch (_) {
      // Silently ignore logging errors — never break the app
    }

    return originalJson(body);
  };

  next();
};
