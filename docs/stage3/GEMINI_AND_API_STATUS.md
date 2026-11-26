# KissanSevaAI — Stage 3 Gemini & External API Verification

**Verification Date:** November 26, 2026  
**Credential Hygiene:** Raw API keys and secrets in `backend/.env` were never printed, logged, or committed. All statuses below reflect live Stage 3 probes on `:8000` and `:5050`.

---

## 1. Local & Cloud Service Summary

| Service | Configuration | Live Stage 3 Behavior | Classification |
|---|---|---|---|
| **Google Gemini (`GEMINI_API_KEY`)** | `configured: true`, `activeModel: "gemini-3-flash-preview"`, `fallbackModels: []`, `timeoutMs: 15000`, `userRateLimit: {rpm: 20, rpd: 200}` | Returns HTTP **`429 RESOURCE_EXHAUSTED` (`quota_exhausted`)** immediately without retry storms (`shouldRetryError` short-circuits on `quota_exhausted`). | **`BLOCKED`** (Upstream key daily quota exhausted) |
| **FastAPI Crop ML (`:8000`)** | V1 active (`dinov2_vits14`, `768` dim, `67` classes, `2,144` refs); `shadow_mode_enabled: true`, `v3_candidate_status: "ready_candidate_only"` | Serves V1 predictions cleanly (`200 OK`) and appends V3 comparison telemetry to `ml-service/logs/shadow_comparison.jsonl`. | **`VERIFIED LIVE`** |
| **Open-Meteo Weather** | `requiresApiKey: false`, `provider: "open-meteo"` | Live `200 OK` Nagpur & multi-day forecasts + spraying advisories. | **`VERIFIED LIVE`** |
| **Data.gov.in AGMARKNET** | `apiKeyConfigured: true`, `updateIntervalMinutes: 15` | Live fetch succeeded (`lastLiveSuccessAt: 2026-11-26T06:36:28.008Z`, `total: 43`, `15` crops / `18` veggies / `10` fruits); rapid repeat hits `429` cleanly and serves cached verified rates (`cached_agmarknet_rate_limited`) with explicit banner and zero fake prices. | **`VERIFIED LIVE`** |
| **Sarvam Call-Agent Webhook** | `sharedSecretConfigured: false`, `hmacSigningConfigured: false`, `sarvamApiKeyConfigured: false` | Contract & all 6 tool endpoints ready; unauthenticated requests fail closed (`503 not_configured` until secrets added). | **`IMPLEMENTED BUT UNVERIFIED`** (Live telephony unconfigured) |

---

## 2. Gemini Per-Feature Verification & Degradation Audit

Every AI-backed feature was probed once in Stage 3 without blind retry loops, using `GET /api/ai/status` and live Android/HTTP checks:

- **Observed Telemetry (`GET /api/ai/status` after interactive QA):**
  - `activeModel: "gemini-3-flash-preview"`
  - `totalRequests: 6`, `successCount: 0`, `errorCount: 6`, `errorCountsByCode: {"quota_exhausted": 6}`
  - Breakdown by feature:
    - `crop_vision`: `2` requests, `2` failures (`quota_exhausted`)
    - `crop_advisory`: `2` requests, `2` failures (`quota_exhausted`)
    - `livestock_triage`: `1` request, `1` failure (`quota_exhausted`)
    - `chat_crop`: `1` request, `1` failure (`quota_exhausted`)
- **Feature Behavior Under Quota Exhaustion:**
  1. **Crop Vision Fallback & Second Opinion (`POST /api/crop/second-opinion`):**
     - Returned HTTP `429` with `reconciliationStatus: "ai_unavailable"`.
     - **Verified:** Primary V1 DINOv2 scan remained strictly preserved (`Wheat Black Rust`, `reconciliationStatus: "ai_unavailable"`, never overwritten or erased; `a35_04_crop_second_opinion.png`).
  2. **Livestock Vision & Clinical Triage (`POST /api/livestock/analyze`):**
     - Returned HTTP `429` for Gemini Vision enrichment while deterministic red-flag triage persisted safely.
     - **Stage 3 Fix Applied ([`livestockController.js`](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/controllers/livestockController.js)):** Updated `analyzeLivestock` `catch (aiErr)` block so degraded triage records (`source: 'service_unavailable'`, `confidence: null`, `prescriptionsWithheld: true`, `urgentVetEscalation`, `SAFETY_NOTICE`) are persisted to MongoDB `LivestockAnalysis` before responding `429`/`503`. Verified in [`stage1ApiIntegration.test.js`](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/tests/stage1ApiIntegration.test.js), post-restart Mongo survival (`POST_RESTART_LIVESTOCK: 1`), and on Galaxy A35 (`a35_05_livestock.png`).
  3. **Multi-Turn Chatbot (`POST /api/chat`):**
     - Returned HTTP `429` (`quota_exhausted`); Android Chatbot displayed exact grounded fallback notice (`"AI daily API quota is exhausted on the configured key. Deterministic tools continue working; update GEMINI_API_KEY or wait for quota reset."`, `a35_11_chatbot.png`).
  4. **Scheme AI Explainer (`POST /api/schemes/explain`):**
     - Returned HTTP `429` (`quota_exhausted`) with `fallbackChecklist: true` using deterministic curated checklist steps.
  5. **Crop Planner AI Refinement:**
     - Deterministic weather-aware 7-day crop schedules (`cropPlannerService.js` + Open-Meteo) continue operating reliably (`includeAi: false` default / rules fallback when quota exhausted).

---

## 3. AGMARKNET Live Fetch + Rate-Limit Handling

Live `GET /api/market/stats` output observed in Stage 3 (`2026-11-26T06:55:39Z`):
```json
{
  "total": 43,
  "cropsCount": 15,
  "vegetablesCount": 18,
  "fruitsCount": 10,
  "lastUpdated": "2026-11-26T06:36:28.010Z",
  "lastLiveSuccessAt": "2026-11-26T06:36:28.008Z",
  "sourceStatus": "cached_agmarknet_rate_limited",
  "lastFetchError": "Data.gov.in API HTTP 429 (rate-limited). Serving last verified data without fabricating updates.",
  "apiKeyConfigured": true,
  "updateIntervalMinutes": 15
}
```
Notice the automatic transition: a fresh live fetch succeeded at `06:36:28.008Z` (`sourceStatus: "live_agmarknet"`), and subsequent rapid test refreshes respected the upstream `429` cooldown cleanly without synthetic random price generation.

---

## 4. Restoring Live Gemini Capabilities

1. Provision or billing-upgrade a Gemini API key with active quota for `gemini-3-flash-preview`.
2. Update `GEMINI_API_KEY=...` inside `backend/.env`.
3. Restart `node backend/scripts/startDevServer.js` (`:5050`) and confirm `GET /api/ai/status` transitions from `quota_exhausted` to `successCount >= 1` on a single `POST /api/chat` test.
