# KissanSevaAI — Stage 2 Live API & External Integration Verification

**Verification Date:** November 26, 2026  
**Security Policy:** Raw `.env` API secrets are never inspected in full, logged, or committed. All external service outcomes below were directly observed via live HTTP/SDK probes in Stage 2.

---

## 1. Local Microservices & Production Safety Flags

| Service | Endpoint | Observed Live Response (Nov 26, 2026) | Status |
|---|---|---|---|
| **FastAPI ML (`:8000`)** | `GET /health` | `{"ok":true,"status":"healthy","active_model":"v1","shadow_mode_enabled":true,"v3_candidate_status":"ready_candidate_only","model":"dinov2_vits14","embedding_dim":768,"classes":67,"reference_images":2144}` | **VERIFIED END TO END** (`V1` strictly active; `V3` strictly shadow with `13` entries logged in `ml-service/logs/shadow_comparison.jsonl`) |
| **Node.js Backend (`:5050`)** | `POST /api/auth/login`, `/api/ai/status`, `/api/market/stats`, `/api/crop/history`, `/api/planner/plans`, `/api/schemes/saved` | `200 OK` against in-memory `MongoMemoryServer` (`mongod-arm64-darwin-8.2.6`), JWT auth + `optionalProtect` verified | **VERIFIED END TO END** |

---

## 2. External Cloud & Government API Probes

### 2.1 Open-Meteo Weather Forecast API (`https://api.open-meteo.com`)
- **Classification:** **`VERIFIED END TO END`**
- **Requirements:** No API key required.
- **Observed Live Output:**
  - `GET /api/weather/forecast?location=Nagpur` -> `200 OK` (`18°C`, Humidity `61%`, Wind `5.6 km/h`, Rain `0.0 mm`, `Spraying Window: Suitable`, 7 daily entries, backed by `WeatherCache` + Room cache).
  - Earlier probes for `New Delhi` (`13°C`) and `Nagpur` (`19.5°C`) also returned live `200 OK` `7`-day data.

### 2.2 `data.gov.in` Agmarknet Live Mandi API
- **Classification:** **`VERIFIED END TO END`**
- **API Key Configured:** Yes (`apiKeyConfigured: true`).
- **Observed Live Output (`GET /api/market/stats`):**
  ```json
  {
    "total": 71,
    "cropsCount": 36,
    "vegetablesCount": 21,
    "fruitsCount": 14,
    "lastUpdated": "2026-11-26T05:53:35.939Z",
    "lastLiveSuccessAt": "2026-11-26T05:53:35.937Z",
    "sourceStatus": "live_agmarknet",
    "lastFetchError": null,
    "apiKeyConfigured": true,
    "updateIntervalMinutes": 15
  }
  ```
- **Quota / Rate-Limit Behavior:** Upstream `api.data.gov.in` returned HTTP `429` during rapid back-to-back manual refresh stress probes; `marketDataService.js` cleanly enforces a 5-minute cooldown while preserving verified database rates with zero synthetic random price fabrication. Startup auto-fetch ensures fresh dev launches populate Agmarknet rates immediately.

### 2.3 Google Gemini API (`GEMINI_API_KEY`)
- **Classification:** **`BLOCKED` (External Free-Tier Quota / Upstream 503 Demand)**
- **Observed Live Probe Results:**
  - `gemini-3-flash-preview` -> HTTP **`429 RESOURCE_EXHAUSTED`** (`quota_exhausted` — Free Tier `20` RPD limit reached).
  - `gemini-3.5-flash` & `gemini-3.1-flash-lite-preview` -> timed out at `8s`.
  - `gemma-4-26b-a4b-it` -> simple text succeeded once (`"Namaste, aap kaise hain ji?"`), then structured JSON schema call returned HTTP **`503 UNAVAILABLE`** (high demand); `gemma-4-31b-it` also returned **`503 UNAVAILABLE`**.
- **Observed Telemetry (`GET /api/ai/status` during Stage 2 QA):**
  - `activeModel: "gemini-3-flash-preview"`
  - `totalRequests: 3`, `successCount: 0`, `errorCount: 3`, `errorCountsByCode: {"quota_exhausted": 3}` across `crop_planner`, `crop_advisory`, and `livestock_triage`.
  - Honest user-facing `quota_exhausted` messages surfaced cleanly across UI without blocking deterministic features (`V1 DINOv2`, Livestock red-flag triage, Open-Meteo weather, 7-day rules planner, Schemes catalog).

### 2.4 Call-Agent Telephony Webhook (`backend/routes/callAgentRoutes.js`)
- **Classification:** **`IMPLEMENTED BUT NOT LIVE-VERIFIED` (`BLOCKED` on External Partner Credentials)**
- **Observed Endpoint (`GET /api/integrations/call-agent/contract`):**
  ```json
  {
    "service": "kissansevaai-call-agent-adapter",
    "status": "contract_ready_external_telephony_required",
    "note": "External telephony/SIP/IVR credentials are required before live voice call routing is active.",
    "webhookEndpoint": "/api/integrations/call-agent/webhook",
    "authHeader": "X-Call-Agent-Secret",
    "supportedIntents": ["mandi_rate_lookup", "weather_spray_check", "scheme_summary"]
  }
  ```
- **Webhook Secret Status:** `CALL_AGENT_WEBHOOK_SECRET` is not set; `POST /api/integrations/call-agent/webhook` cleanly stays gated until an external SIP/IVR provider is integrated.

### 2.5 Persistence Store (`MongoDB`)
- **Current Dev Setup:** `MongoMemoryServer` (`mongod-arm64-darwin-8.2.6`) launched by `backend/scripts/startDevServer.js` (ephemeral across Node restarts). No standalone local/Atlas `mongod` instance is currently running outside `startDevServer.js`.
