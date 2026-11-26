# KissanSevaAI — Stage 3 Sarvam Call-Agent Integration Report

**Integration Classification:** **`IMPLEMENTED BUT UNVERIFIED` (Live PSTN / Sarvam Telephony Call Pending External Credentials)**  
**Backend Automated Test Status:** **`VERIFIED`** in [`backend/tests/callAgentIntegration.test.js`](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/tests/callAgentIntegration.test.js) (`10/10` Node suite passing, `0` failed)

---

## 1. Architecture & Reused Modules

No duplicate call-agent service or paid telephony account was created. Instead, the existing webhook route [`backend/routes/callAgentRoutes.js`](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/routes/callAgentRoutes.js) was expanded via [`backend/services/callAgentService.js`](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/services/callAgentService.js), reusing KissanSevaAI's production models and domain engines:

- [`backend/app.js`](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/app.js): captures exact `req.rawBody` bytes inside `express.json({ limit: '10mb', verify })` for exact HMAC digest calculation.
- [`backend/controllers/chatController.js`](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/controllers/chatController.js): exports `gatherToolGrounding(...)` so telephony general-advice calls share the exact grounded farmer/weather/mandi/scheme context builder used by the in-app Chatbot.
- [`backend/controllers/livestockController.js`](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/controllers/livestockController.js): exports `normalizeAnimalType`, `evaluateLivestockRedFlags`, and `SAFETY_NOTICE` (`1962` helpline escalation).
- Domain services & Mongo collections: `User`, `CropAnalysis`, `CropPlan`, `LivestockAnalysis`, `weatherService.js` (`getWeatherForecast`), `marketDataService.js` (`marketService`), `schemeCatalogService.js` (`searchSchemesCatalog`, `evaluateSchemeEligibility`), and `geminiService.js`.

---

## 2. Endpoints, Authentication & Replay Protection

- **Discovery Contract:** `GET /api/integrations/call-agent/contract`
  - Public non-secret metadata exposing supported tools, aliases, auth headers, and boolean config flags (`sharedSecretConfigured`, `hmacSigningConfigured`, `sarvamApiKeyConfigured`). Currently reports `"status": "contract_ready_external_telephony_required"`.
- **Execution Endpoints:**
  - `POST /api/integrations/call-agent/webhook` (routes via `tool`, `intent`, or `action` field)
  - `POST /api/integrations/call-agent/tools/:toolName` (direct per-tool Sarvam custom tool URLs)
- **Authentication (`verifyCallAgentAuth`):**
  - Fails closed with HTTP `503 not_configured` when neither `CALL_AGENT_WEBHOOK_SECRET` / `SARVAM_WEBHOOK_SECRET` nor `SARVAM_WEBHOOK_SIGNING_SECRET` is configured.
  - **Method 1 — HMAC-SHA256 + Replay Window (`<= 300s`):** Validates `X-Sarvam-Signature` / `X-Call-Agent-Signature` (`sha256=<hex>`) using `crypto.timingSafeEqual`. When `X-Sarvam-Timestamp` / `X-Call-Agent-Timestamp` is supplied, validates `<= 300` seconds drift and hashes `${timestamp}.${rawBody}`.
  - **Method 2 — Shared Secret:** Constant-time comparison (`crypto.timingSafeEqual`) against `X-Call-Agent-Secret`, `X-Sarvam-Secret`, or `Authorization: Bearer <secret>`.
- **Caller Identification (`resolveCallerUser`):** Matches `farmerId` (ObjectId) or normalizes `callerPhone` to last 10 digits with separator-tolerant regex (`[\s\-]*`) against `User.phone` (e.g., matching `+91 98765 00000`).

---

## 3. Canonical Sarvam Tools & Safety Gates (`CANONICAL_TOOLS`)

| Canonical Tool (`normalizeToolName`) | Aliases Supported | Endpoint & Parameters | Grounding & Safety Rules |
|---|---|---|---|
| `crop_assistance` | `crop`, `crop_help`, `crop_diagnosis`, `crop_status` | `/api/integrations/call-agent/tools/crop_assistance` (`callerPhone`, `crop`, `disease`, `symptomsNotes`, `language`) | Queries caller's latest `CropAnalysis` (`dinov2_v1`) & active `CropPlan`. Strictly enforces `needs_review \|\| treatmentWithheld` -> empties `organic`/`chemical` arrays and instructs consultation with nearest Krishi Vigyan Kendra. |
| `livestock_questions` | `livestock`, `livestock_triage`, `veterinary`, `animal_health` | `/api/integrations/call-agent/tools/livestock_questions` (`callerPhone`, `animalType`, `feverStatus`, `appetiteStatus`, `milkYieldChange`, `lesionsOrUlcers`, `symptomsNotes`) | Runs `evaluateLivestockRedFlags` + checks caller's `LivestockAnalysis` history. Always sets `prescriptionsWithheld: true`, `helplineNumber: "1962"`, and triggers `urgentVetEscalation` alert on red flags without prescribing antibiotics/steroids. |
| `weather_forecasts` | `weather_forecast`, `weather_spray_check`, `weather` | `/api/integrations/call-agent/tools/weather_forecasts` (`callerPhone`, `location`, `state`, `language`) | Uses live Open-Meteo `getWeatherForecast({ includeAi: false })` via explicit location or caller GPS (`latitude`/`longitude`), returning spoken `sprayWindowSuitable` and tomorrow range. |
| `mandi_prices` | `mandi_rate_lookup`, `mandi`, `market_prices` | `/api/integrations/call-agent/tools/mandi_prices` (`crop`, `state`, `category`) | Reads `marketService.getPrices`, `getStats`, and `compareMarkets` (reporting highest/lowest mandi spread & exact `sourceStatus`). |
| `government_schemes` | `scheme_summary`, `scheme_eligibility`, `schemes` | `/api/integrations/call-agent/tools/government_schemes` (`callerPhone`, `state`, `crop`, `farmSize`, `soilType`, `landType`, `query`) | Calls `evaluateSchemeEligibility` when profile/parameter criteria present (falling back to caller profile `farmSizeAcres`/`soilType`/`state`), or `searchSchemesCatalog` (`top 3`). |
| `general_farming_advice` | `general_advice`, `general`, `advice` | `/api/integrations/call-agent/tools/general_farming_advice` (`callerPhone`, `query`, `crop`, `location`, `language`) | Builds grounded summary via `gatherToolGrounding` and invokes `callGeminiText(..., maxOutputTokens: 280)`. On Gemini `quota_exhausted`/timeout, degrades cleanly with `aiAvailable: false`, `serviceStatus`, and deterministic `groundedSources` summary. |

---

## 4. Exact Steps to Wire Into Your Existing Sarvam Call Agent

Because external Sarvam dashboard credentials (`CALL_AGENT_WEBHOOK_SECRET`, `SARVAM_WEBHOOK_SIGNING_SECRET`, `SARVAM_API_KEY`) and a live PSTN call were not executed autonomously, follow these exact steps to go live:

1. **Expose Node Backend (`:5050`) Over Public HTTPS:** Place the Express backend behind TLS/reverse proxy (or HTTPS tunnel such as Cloudflare Tunnel / ngrok pointing to `127.0.0.1:5050`) so Sarvam cloud servers can reach `/api/integrations/call-agent/...`.
2. **Configure Secrets in `backend/.env`:** Set strong random values for `CALL_AGENT_WEBHOOK_SECRET` and/or `SARVAM_WEBHOOK_SIGNING_SECRET` (plus `SARVAM_API_KEY` if outbound SDK/API calls are added later), then restart `node backend/scripts/startDevServer.js`.
3. **Verify Contract Readiness:** Confirm `GET /api/integrations/call-agent/contract` returns `"status": "webhook_configured_ready"`, `"sharedSecretConfigured": true`, and `"hmacSigningConfigured": true`.
4. **Register Custom Tools in Sarvam Call Agent Dashboard:** Map all 6 tools from `sarvamToolSchemas` (pointing each tool to its `https://<public-host>/api/integrations/call-agent/tools/<toolName>` URL or unified `/webhook`), configure the webhook auth/HMAC header (`X-Sarvam-Signature` + `X-Sarvam-Timestamp` or `X-Call-Agent-Secret`), and ensure telephony passes caller phone number (`callerPhone`) and utterance params.
5. **Place a Real Inbound Test Call:** Call your Sarvam agent phone number, test Hindi/English queries for crop scan status, mandi rates, weather spray window, livestock urgent symptoms, and schemes, and verify Node `:5050` logs `200 OK` responses before marking telephony `VERIFIED LIVE`.
