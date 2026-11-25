import crypto from 'node:crypto';
import dotenv from 'dotenv';
import { GoogleGenAI, Type } from '@google/genai';

dotenv.config();

const DEFAULT_MODEL = process.env.GEMINI_MODEL || 'gemini-3-flash-preview';
const DEFAULT_TIMEOUT_MS = Number(process.env.GEMINI_TIMEOUT_MS || 15000);
const DEFAULT_MAX_RETRIES = Number(process.env.GEMINI_MAX_RETRIES || 2);
const USER_RPM_LIMIT = Number(process.env.GEMINI_USER_RPM || 20);
const USER_RPD_LIMIT = Number(process.env.GEMINI_USER_RPD || 200);

// In-memory sliding-window rate limiters keyed by userId
const userMinuteWindows = new Map();
const userDayWindows = new Map();

// Telemetry counters (safe to expose; never includes API keys or raw prompts)
const telemetry = {
  totalRequests: 0,
  successCount: 0,
  errorCount: 0,
  errorCountsByCode: {},
  usageByFeature: {},
  totalPromptTokens: 0,
  totalCandidateTokens: 0,
  totalTokens: 0,
  lastSuccessAt: null,
  lastErrorAt: null,
  lastErrorCode: null,
  lastErrorMessage: null,
  recentRequests: [], // bounded to 50 entries
};

// Optional hook to persist token/request usage without hard-coupling when Mongo is offline in unit tests
let usageSink = null;
// Test hook so unit/integration tests never consume live Gemini RPD quota
let clientFactoryOverride = null;

export class GeminiServiceError extends Error {
  constructor({
    code = 'upstream_unavailable',
    statusCode = 503,
    message = 'Gemini AI service is currently unavailable.',
    userMessage = null,
    requestId = null,
    model = DEFAULT_MODEL,
    latencyMs = 0,
    retryAfterSeconds = null,
    cause = null,
  }) {
    super(message);
    this.name = 'GeminiServiceError';
    this.code = code;
    this.statusCode = statusCode;
    this.userMessage = userMessage || message;
    this.requestId = requestId;
    this.model = model;
    this.latencyMs = latencyMs;
    this.retryAfterSeconds = retryAfterSeconds;
    if (cause) this.cause = cause;
  }

  toJSON() {
    return {
      error: this.userMessage,
      code: this.code,
      aiAvailable: false,
      serviceStatus: this.code,
      requestId: this.requestId,
      model: this.model,
      retryAfterSeconds: this.retryAfterSeconds,
    };
  }
}

export function setGeminiClientFactoryForTests(factory) {
  clientFactoryOverride = factory;
}

export function setGeminiUsageSink(fn) {
  usageSink = typeof fn === 'function' ? fn : null;
}

export function resetGeminiRateLimitsForTests() {
  userMinuteWindows.clear();
  userDayWindows.clear();
}

export function isGeminiConfigured() {
  if (typeof clientFactoryOverride === 'function') return true;
  const key = (process.env.GEMINI_API_KEY || '').trim();
  return key.length > 0 && !/your_.*api_key|placeholder|changeme/i.test(key);
}

function getModelCandidates(preferredModel) {
  const envFallbacks = (process.env.GEMINI_FALLBACK_MODELS || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  const candidates = [preferredModel || process.env.GEMINI_MODEL || DEFAULT_MODEL, ...envFallbacks];
  return [...new Set(candidates)];
}

function getAiClient() {
  if (typeof clientFactoryOverride === 'function') {
    return clientFactoryOverride();
  }
  const apiKey = (process.env.GEMINI_API_KEY || '').trim();
  if (!apiKey || /your_.*api_key|placeholder|changeme/i.test(apiKey)) {
    throw new GeminiServiceError({
      code: 'not_configured',
      statusCode: 503,
      message: 'GEMINI_API_KEY is missing or placeholder.',
      userMessage: 'AI service is not configured on the server. Deterministic features remain available.',
    });
  }
  return new GoogleGenAI({ apiKey });
}

function sanitizeMessage(msg = '') {
  return String(msg)
    .replace(/AIza[0-9A-Za-z\-_]{10,}/g, '[REDACTED_KEY]')
    .replace(/key=[0-9A-Za-z\-_]+/gi, 'key=[REDACTED]');
}

function parseRetrySeconds(rawMessage = '') {
  const secMatch = rawMessage.match(/retryDelay["':\s]+(\d+)s/i)
    || rawMessage.match(/retry in\s+(\d+)\s*s/i)
    || rawMessage.match(/(\d+)h(\d+)m(\d+)?/i);
  if (!secMatch) return null;
  if (secMatch.length >= 3 && /h/i.test(secMatch[0])) {
    const h = Number(secMatch[1] || 0);
    const m = Number(secMatch[2] || 0);
    const s = Number(secMatch[3] || 0);
    return h * 3600 + m * 60 + s;
  }
  return Number(secMatch[1] || 0) || null;
}

export function classifyGeminiError(err, { requestId = null, model = DEFAULT_MODEL, latencyMs = 0 } = {}) {
  if (err instanceof GeminiServiceError) return err;

  const rawMsg = sanitizeMessage(err?.message || String(err || 'Unknown Gemini error'));
  const status = Number(err?.status || err?.statusCode || (rawMsg.match(/"code":\s*(\d{3})/)?.[1] || 0));
  const retryAfterSeconds = parseRetrySeconds(rawMsg);

  if (rawMsg.includes('TIMEOUT') || err?.name === 'AbortError') {
    return new GeminiServiceError({
      code: 'timeout',
      statusCode: 504,
      message: `Gemini request timed out: ${rawMsg}`,
      userMessage: 'AI response timed out. Please retry shortly.',
      requestId,
      model,
      latencyMs,
      cause: err,
    });
  }

  if (status === 429 || /RESOURCE_EXHAUSTED|quota exceeded/i.test(rawMsg)) {
    const isDailyExhaustion = /PerDay|FreeTier|generate_content_free_tier_requests/i.test(rawMsg);
    return new GeminiServiceError({
      code: isDailyExhaustion ? 'quota_exhausted' : 'rate_limited',
      statusCode: 429,
      message: `Gemini quota/rate error (${status || 429}): ${rawMsg}`,
      userMessage: isDailyExhaustion
        ? 'AI daily API quota is exhausted on the configured key. Deterministic tools continue working; update GEMINI_API_KEY or wait for quota reset.'
        : 'AI service is temporarily rate-limited. Please wait a few seconds and retry.',
      requestId,
      model,
      latencyMs,
      retryAfterSeconds: retryAfterSeconds || 60,
      cause: err,
    });
  }

  if (status === 401 || status === 403 || /API_KEY_INVALID|permission denied/i.test(rawMsg)) {
    return new GeminiServiceError({
      code: 'not_configured',
      statusCode: 503,
      message: `Gemini authentication error (${status}): ${rawMsg}`,
      userMessage: 'AI service API credentials are invalid or unauthorized.',
      requestId,
      model,
      latencyMs,
      cause: err,
    });
  }

  if (status === 400 || status === 404) {
    return new GeminiServiceError({
      code: 'bad_request',
      statusCode: 502,
      message: `Gemini model/request error (${status}): ${rawMsg}`,
      userMessage: `Configured AI model (${model}) rejected the request or is unavailable.`,
      requestId,
      model,
      latencyMs,
      cause: err,
    });
  }

  return new GeminiServiceError({
    code: 'upstream_unavailable',
    statusCode: 503,
    message: `Gemini upstream error (${status || 'network'}): ${rawMsg}`,
    userMessage: 'AI service is temporarily unavailable. Deterministic features remain active.',
    requestId,
    model,
    latencyMs,
    cause: err,
  });
}

function enforceUserRateLimit(userId, requestId, model) {
  if (!userId) return;
  const key = String(userId);
  const now = Date.now();

  // Sliding 60s window
  const recentMin = (userMinuteWindows.get(key) || []).filter((ts) => now - ts < 60_000);
  if (recentMin.length >= USER_RPM_LIMIT) {
    throw new GeminiServiceError({
      code: 'rate_limited',
      statusCode: 429,
      message: `Per-user RPM limit (${USER_RPM_LIMIT}/min) reached for user ${key}`,
      userMessage: 'Too many AI requests in a short time. Please wait a minute and try again.',
      requestId,
      model,
      retryAfterSeconds: 60,
    });
  }

  // Sliding 24h window
  const recentDay = (userDayWindows.get(key) || []).filter((ts) => now - ts < 86_400_000);
  if (recentDay.length >= USER_RPD_LIMIT) {
    throw new GeminiServiceError({
      code: 'quota_exhausted',
      statusCode: 429,
      message: `Per-user RPD limit (${USER_RPD_LIMIT}/day) reached for user ${key}`,
      userMessage: 'Daily per-user AI request limit reached. Deterministic features remain available.',
      requestId,
      model,
      retryAfterSeconds: 3600,
    });
  }

  recentMin.push(now);
  recentDay.push(now);
  userMinuteWindows.set(key, recentMin);
  userDayWindows.set(key, recentDay);
}

function recordUsage({ requestId, feature, model, ok, code, latencyMs, userId, usage }) {
  telemetry.totalRequests += 1;
  const feat = feature || 'general';
  if (!telemetry.usageByFeature[feat]) {
    telemetry.usageByFeature[feat] = { requests: 0, successes: 0, failures: 0, totalTokens: 0 };
  }
  telemetry.usageByFeature[feat].requests += 1;

  if (ok) {
    telemetry.successCount += 1;
    telemetry.lastSuccessAt = new Date().toISOString();
    telemetry.usageByFeature[feat].successes += 1;
  } else {
    telemetry.errorCount += 1;
    telemetry.errorCountsByCode[code] = (telemetry.errorCountsByCode[code] || 0) + 1;
    telemetry.lastErrorAt = new Date().toISOString();
    telemetry.lastErrorCode = code;
    telemetry.usageByFeature[feat].failures += 1;
  }

  const promptTokens = Number(usage?.promptTokenCount || usage?.promptTokens || 0);
  const candidateTokens = Number(usage?.candidatesTokenCount || usage?.candidateTokens || 0);
  const totalTokens = Number(usage?.totalTokenCount || usage?.totalTokens || promptTokens + candidateTokens);

  telemetry.totalPromptTokens += promptTokens;
  telemetry.totalCandidateTokens += candidateTokens;
  telemetry.totalTokens += totalTokens;
  telemetry.usageByFeature[feat].totalTokens += totalTokens;

  const entry = {
    requestId,
    feature: feat,
    model,
    ok,
    code: ok ? 'ok' : code,
    latencyMs,
    promptTokens,
    candidateTokens,
    totalTokens,
    userId: userId ? String(userId) : null,
    timestamp: new Date().toISOString(),
  };
  telemetry.recentRequests.unshift(entry);
  if (telemetry.recentRequests.length > 50) telemetry.recentRequests.pop();

  if (usageSink) {
    Promise.resolve(usageSink(entry)).catch(() => {});
  }
}

export function stripMarkdownJsonFences(text = '') {
  const trimmed = String(text).trim();
  const fenceMatch = trimmed.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/i);
  return fenceMatch ? fenceMatch[1].trim() : trimmed;
}

export function validateAgainstSchema(value, schema, path = '$') {
  const errors = [];
  if (!schema || typeof schema !== 'object') return errors;

  if (value === null || value === undefined) {
    if (schema.nullable) return errors;
    errors.push(`${path}: expected non-null value`);
    return errors;
  }

  const expectedType = String(schema.type || '').toUpperCase();
  if (expectedType === Type.OBJECT || expectedType === 'OBJECT') {
    if (typeof value !== 'object' || Array.isArray(value)) {
      errors.push(`${path}: expected OBJECT`);
      return errors;
    }
    if (Array.isArray(schema.required)) {
      for (const reqKey of schema.required) {
        if (!(reqKey in value)) {
          errors.push(`${path}.${reqKey}: missing required property`);
        }
      }
    }
    if (schema.properties && typeof schema.properties === 'object') {
      for (const [k, propSchema] of Object.entries(schema.properties)) {
        if (k in value) {
          errors.push(...validateAgainstSchema(value[k], propSchema, `${path}.${k}`));
        }
      }
    }
  } else if (expectedType === Type.ARRAY || expectedType === 'ARRAY') {
    if (!Array.isArray(value)) {
      errors.push(`${path}: expected ARRAY`);
      return errors;
    }
    if (schema.items) {
      value.forEach((item, idx) => {
        errors.push(...validateAgainstSchema(item, schema.items, `${path}[${idx}]`));
      });
    }
  } else if (expectedType === Type.STRING || expectedType === 'STRING') {
    if (typeof value !== 'string') {
      errors.push(`${path}: expected STRING`);
    } else if (Array.isArray(schema.enum) && !schema.enum.includes(value)) {
      errors.push(`${path}: value "${value}" not in enum [${schema.enum.join(', ')}]`);
    }
  } else if (
    expectedType === Type.NUMBER ||
    expectedType === 'NUMBER' ||
    expectedType === Type.INTEGER ||
    expectedType === 'INTEGER'
  ) {
    if (typeof value !== 'number' || Number.isNaN(value)) {
      errors.push(`${path}: expected NUMBER`);
    }
  } else if (expectedType === Type.BOOLEAN || expectedType === 'BOOLEAN') {
    if (typeof value !== 'boolean') {
      errors.push(`${path}: expected BOOLEAN`);
    }
  }

  return errors;
}

async function withTimeout(promise, ms, label) {
  let timer;
  try {
    return await Promise.race([
      promise,
      new Promise((_, reject) => {
        timer = setTimeout(() => {
          const e = new Error(`TIMEOUT after ${ms}ms during ${label}`);
          e.name = 'AbortError';
          reject(e);
        }, ms);
      }),
    ]);
  } finally {
    clearTimeout(timer);
  }
}

function shouldRetryError(classified) {
  if (classified.code === 'upstream_unavailable' || classified.code === 'timeout') return true;
  if (classified.code === 'rate_limited' && classified.retryAfterSeconds && classified.retryAfterSeconds <= 4) {
    return true;
  }
  return false;
}

async function executeWithRetry({
  invokeFn,
  preferredModel,
  userId,
  feature,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  maxRetries = DEFAULT_MAX_RETRIES,
}) {
  const requestId = `gem_${crypto.randomUUID().replace(/-/g, '').slice(0, 12)}`;
  const startMs = Date.now();
  const candidates = getModelCandidates(preferredModel);

  enforceUserRateLimit(userId, requestId, candidates[0]);
  const ai = getAiClient();

  let lastError = null;
  for (const modelName of candidates) {
    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        const res = await withTimeout(invokeFn(ai, modelName, requestId), timeoutMs, `${feature || 'gemini'}:${modelName}`);
        const latencyMs = Date.now() - startMs;
        const usage = {
          promptTokens: res?.usageMetadata?.promptTokenCount || 0,
          candidateTokens: res?.usageMetadata?.candidatesTokenCount || 0,
          totalTokens: res?.usageMetadata?.totalTokenCount || 0,
        };
        recordUsage({
          requestId,
          feature,
          model: modelName,
          ok: true,
          latencyMs,
          userId,
          usage,
        });
        return { response: res, requestId, model: modelName, latencyMs, usage };
      } catch (rawErr) {
        const classified = classifyGeminiError(rawErr, {
          requestId,
          model: modelName,
          latencyMs: Date.now() - startMs,
        });
        lastError = classified;
        if (attempt < maxRetries && shouldRetryError(classified)) {
          const jitter = Math.floor(Math.random() * 200);
          const delayMs = Math.min(2500, 350 * 2 ** attempt + jitter);
          await new Promise((r) => setTimeout(r, delayMs));
          continue;
        }
        // Only try next fallback model on bad_request(404/400), upstream_unavailable(503), or quota_exhausted if configured
        break;
      }
    }
  }

  recordUsage({
    requestId,
    feature,
    model: lastError?.model || candidates[0],
    ok: false,
    code: lastError?.code || 'upstream_unavailable',
    latencyMs: Date.now() - startMs,
    userId,
  });
  telemetry.lastErrorMessage = lastError?.message || null;
  throw lastError;
}

export async function callGeminiText({
  prompt,
  systemInstruction = null,
  history = [],
  userId = null,
  feature = 'text',
  model = null,
  temperature = 0.4,
  maxOutputTokens = 1024,
  timeoutMs = DEFAULT_TIMEOUT_MS,
}) {
  const contents = [];
  if (Array.isArray(history) && history.length > 0) {
    for (const turn of history) {
      if (!turn?.text) continue;
      contents.push({
        role: turn.role === 'model' || turn.role === 'assistant' ? 'model' : 'user',
        parts: [{ text: String(turn.text) }],
      });
    }
  }
  contents.push({ role: 'user', parts: [{ text: String(prompt) }] });

  const config = { temperature, maxOutputTokens };
  if (systemInstruction) config.systemInstruction = systemInstruction;

  const { response, requestId, model: usedModel, latencyMs, usage } = await executeWithRetry({
    preferredModel: model,
    userId,
    feature,
    timeoutMs,
    invokeFn: (ai, modelName) =>
      ai.models.generateContent({
        model: modelName,
        contents: contents.length === 1 ? prompt : contents,
        config,
      }),
  });

  const text = (response?.text || '').trim();
  if (!text) {
    throw new GeminiServiceError({
      code: 'invalid_schema',
      statusCode: 502,
      message: 'Gemini returned an empty text response.',
      userMessage: 'AI service returned an empty response. Please try again.',
      requestId,
      model: usedModel,
      latencyMs,
    });
  }

  return {
    text,
    requestId,
    model: usedModel,
    latencyMs,
    usage,
    aiAvailable: true,
    source: 'gemini',
  };
}

export async function callGeminiStructured({
  prompt,
  schema,
  systemInstruction = null,
  userId = null,
  feature = 'structured',
  model = null,
  temperature = 0.2,
  timeoutMs = DEFAULT_TIMEOUT_MS,
}) {
  const config = {
    responseMimeType: 'application/json',
    temperature,
  };
  if (schema) config.responseSchema = schema;
  if (systemInstruction) config.systemInstruction = systemInstruction;

  const { response, requestId, model: usedModel, latencyMs, usage } = await executeWithRetry({
    preferredModel: model,
    userId,
    feature,
    timeoutMs,
    invokeFn: (ai, modelName) =>
      ai.models.generateContent({
        model: modelName,
        contents: prompt,
        config,
      }),
  });

  const rawText = stripMarkdownJsonFences(response?.text || '');
  let parsed;
  try {
    parsed = JSON.parse(rawText);
  } catch (parseErr) {
    throw new GeminiServiceError({
      code: 'invalid_schema',
      statusCode: 502,
      message: `Gemini returned malformed JSON: ${parseErr.message}`,
      userMessage: 'AI service returned malformed structured data. Please try again.',
      requestId,
      model: usedModel,
      latencyMs,
      cause: parseErr,
    });
  }

  const schemaErrors = validateAgainstSchema(parsed, schema);
  if (schemaErrors.length > 0) {
    throw new GeminiServiceError({
      code: 'invalid_schema',
      statusCode: 502,
      message: `Gemini JSON failed schema validation: ${schemaErrors.join('; ')}`,
      userMessage: 'AI response did not match the expected structure. Please retry.',
      requestId,
      model: usedModel,
      latencyMs,
    });
  }

  return {
    data: parsed,
    requestId,
    model: usedModel,
    latencyMs,
    usage,
    aiAvailable: true,
    source: 'gemini',
  };
}

export async function callGeminiVision({
  prompt,
  imageBuffer,
  mimeType = 'image/jpeg',
  schema = null,
  systemInstruction = null,
  userId = null,
  feature = 'vision',
  model = null,
  timeoutMs = DEFAULT_TIMEOUT_MS,
}) {
  if (!imageBuffer || imageBuffer.length === 0) {
    throw new GeminiServiceError({
      code: 'bad_request',
      statusCode: 400,
      message: 'Image buffer is empty.',
      userMessage: 'Please attach a valid image file.',
    });
  }

  const config = {};
  if (schema) {
    config.responseMimeType = 'application/json';
    config.responseSchema = schema;
  }
  if (systemInstruction) config.systemInstruction = systemInstruction;

  const base64Data = imageBuffer.toString('base64');
  const contents = [
    {
      role: 'user',
      parts: [
        { inlineData: { data: base64Data, mimeType } },
        { text: prompt },
      ],
    },
  ];

  const { response, requestId, model: usedModel, latencyMs, usage } = await executeWithRetry({
    preferredModel: model,
    userId,
    feature,
    timeoutMs,
    invokeFn: (ai, modelName) =>
      ai.models.generateContent({
        model: modelName,
        contents,
        config,
      }),
  });

  if (!schema) {
    return {
      text: (response?.text || '').trim(),
      requestId,
      model: usedModel,
      latencyMs,
      usage,
      aiAvailable: true,
      source: 'gemini',
    };
  }

  const rawText = stripMarkdownJsonFences(response?.text || '');
  let parsed;
  try {
    parsed = JSON.parse(rawText);
  } catch (parseErr) {
    throw new GeminiServiceError({
      code: 'invalid_schema',
      statusCode: 502,
      message: `Gemini Vision returned malformed JSON: ${parseErr.message}`,
      userMessage: 'AI vision analysis returned unreadable output. Please retry.',
      requestId,
      model: usedModel,
      latencyMs,
      cause: parseErr,
    });
  }

  const schemaErrors = validateAgainstSchema(parsed, schema);
  if (schemaErrors.length > 0) {
    throw new GeminiServiceError({
      code: 'invalid_schema',
      statusCode: 502,
      message: `Gemini Vision JSON failed schema validation: ${schemaErrors.join('; ')}`,
      userMessage: 'AI vision analysis returned incomplete output. Please retry.',
      requestId,
      model: usedModel,
      latencyMs,
    });
  }

  return {
    data: parsed,
    requestId,
    model: usedModel,
    latencyMs,
    usage,
    aiAvailable: true,
    source: 'gemini',
  };
}

// ── Backwards-compatible exports (strictly fail with GeminiServiceError; NO fabricated fallbacks) ──

export async function generateText(prompt, systemInstruction, options = {}) {
  const res = await callGeminiText({ prompt, systemInstruction, ...options });
  return res.text;
}

export async function generateStructuredJSON(prompt, schema, options = {}) {
  const res = await callGeminiStructured({ prompt, schema, ...options });
  return res.data;
}

export async function analyzeImage(prompt, imageBuffer, mimeType, schema, options = {}) {
  const res = await callGeminiVision({ prompt, imageBuffer, mimeType, schema, ...options });
  return schema ? res.data : res.text;
}

export function getGeminiStatus() {
  return {
    configured: isGeminiConfigured(),
    activeModel: process.env.GEMINI_MODEL || DEFAULT_MODEL,
    fallbackModels: getModelCandidates().slice(1),
    timeoutMs: DEFAULT_TIMEOUT_MS,
    maxRetries: DEFAULT_MAX_RETRIES,
    userRateLimit: { rpm: USER_RPM_LIMIT, rpd: USER_RPD_LIMIT },
    telemetry: {
      totalRequests: telemetry.totalRequests,
      successCount: telemetry.successCount,
      errorCount: telemetry.errorCount,
      errorCountsByCode: telemetry.errorCountsByCode,
      usageByFeature: telemetry.usageByFeature,
      totalPromptTokens: telemetry.totalPromptTokens,
      totalCandidateTokens: telemetry.totalCandidateTokens,
      totalTokens: telemetry.totalTokens,
      lastSuccessAt: telemetry.lastSuccessAt,
      lastErrorAt: telemetry.lastErrorAt,
      lastErrorCode: telemetry.lastErrorCode,
    },
  };
}

export function sendAiErrorResponse(res, err, extraPayload = {}) {
  const classified = err instanceof GeminiServiceError ? err : classifyGeminiError(err);
  return res.status(classified.statusCode || 503).json({
    ...classified.toJSON(),
    ...extraPayload,
  });
}
