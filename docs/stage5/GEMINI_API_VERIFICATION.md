# KissanSevaAI Stage 5 — Gemini API Activation & Live Verification

**Classification Highlights:**
- Server-side `GEMINI_API_KEY` + `@google/genai` SDK activation & `ai.models.list()`: **VERIFIED LIVE**
- `gemini-3-flash-preview` live text, structured JSON & vision generation: **VERIFIED LIVE**
- `gemini-2.5-flash` availability check (`404 NOT_FOUND` on new key): **VERIFIED LIVE**
- Error classification across 6 failure modes (`invalid_key`, `billing_restricted`, `model_unavailable`, `quota_exhausted`, `rate_limited`, `network_failure` / `timeout`) and `AQ.` / `AIza` secret redaction: **VERIFIED BY AUTOMATED TEST** (plus **VERIFIED LIVE** for `model_unavailable` and `quota_exhausted`)

---

## 1. Secure Credential & SDK Configuration

- Updated `GEMINI_API_KEY` solely in gitignored [backend/.env](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/.env) (`.gitignore` line 38 excludes `.env` and `backend/.env`).
- Verified zero API keys appear anywhere in `android/app/src/`, `android/app/build/generated/`, `backend/` source, documentation, or Git history.
- Uses the official `@google/genai` (`^1.35.0`) Node.js SDK inside [geminiService.js](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/services/geminiService.js).
- Hardened `sanitizeMessage()` in [geminiService.js](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/services/geminiService.js) to redact `AQ.<token>`, `AIza<token>`, `Bearer <token>`, `key=<token>`, and literal `process.env.GEMINI_API_KEY`.
- Raised `DEFAULT_TIMEOUT_MS` default in [geminiService.js](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/services/geminiService.js) to `25000` ms (`GEMINI_TIMEOUT_MS` overridable).

## 2. Live Model Discovery & Probe Results

1. **`ai.models.list()` Live Probe**:
   - Credential authenticated cleanly and returned 20 listed models including `models/gemini-2.5-flash`, `models/gemini-2.5-pro`, `models/gemini-2.5-flash-lite`, `models/gemini-3-flash-preview`, `models/gemini-3.1-pro-preview`, `models/gemini-3.1-flash-lite-preview`, and `models/gemini-flash-latest`.
2. **`gemini-2.5-flash` vs `gemini-3-flash-preview` Live Generation**:
   - Probing `gemini-2.5-flash` returned HTTP **`404 NOT_FOUND`**: *"This model models/gemini-2.5-flash is no longer available to new users."* Our backend classifier mapped this cleanly to `502` / `model_unavailable`.
   - Probing **`gemini-3-flash-preview`** returned HTTP **`200 OK`** (`"OK"`), plus valid multimodal JSON vision + text responses across Crop Advisory, Crop Second Opinion, and Grounded Chatbot endpoints.
   - Configured `GEMINI_MODEL=gemini-3-flash-preview` in [backend/.env](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/.env) and default `DEFAULT_MODEL` in [geminiService.js](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/services/geminiService.js), while keeping `GEMINI_MODEL` environment-configurable.

## 3. Quota, Limits & 6-Way Error Classification

- During sustained end-to-end testing in this session, Google AI Free Tier enforced **`GenerateRequestsPerDayPerProjectPerModel-FreeTier`** (`limit: 20` requests per day on `gemini-3-flash`).
- Fixed `classifyGeminiError()` in [geminiService.js](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/services/geminiService.js) so Google's standard `https://aistudio.google.com/docs/rate-limits` URL inside daily quota errors does **not** misclassify `PerDay` exhaustion as per-minute rate limiting:
  - `invalid_key` (`401` / `API_KEY_INVALID` / `UNAUTHENTICATED`)
  - `billing_restricted` (`403` / `BILLING_DISABLED` / `FAILED_PRECONDITION`)
  - `model_unavailable` (`404` / `NOT_FOUND` / unsupported model)
  - `quota_exhausted` (`429` + `PerDay` / `RequestsPerDay` / `RPD` / `FreeTier` / `limit: 0`)
  - `rate_limited` (`429` + `PerMinute` / `RequestsPerMinute` / `TokensPerMinute` / `RATE_LIMIT_EXCEEDED`)
  - `network_failure` (`ENOTFOUND`, `ECONNREFUSED`, `ECONNRESET`, `fetch failed`) and `timeout` (`TIMEOUT`, `AbortError`, `ETIMEDOUT`)
- `GET http://localhost:5050/api/ai/status` exposes only non-secret telemetry (`configured: true`, `activeModel: "gemini-3-flash-preview"`, `timeoutMs: 25000`, `userRateLimit: { rpm: 20, rpd: 200 }`).
