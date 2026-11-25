# KissanSevaAI — Stage 1 Autonomous Implementation Report

**Completion Date:** November 26, 2026  
**Git Branch:** `stage1/autonomous-implementation` (pre-Stage1 local checkpoint tagged `pre-stage1-checkpoint` at commit `a038d30`)  
**Target Preservation Guarantee:** Production crop model V1 (`dinov2_vits14_cls_mean.onnx`, `2,144` vectors, `67` classes) remains active; V3 candidate stays strictly in shadow mode; no `ml-service/model_assets/` weights altered.

---

## 1. Architecture & Core Infrastructure Implemented

### A. Centralized Gemini AI Service (`backend/services/geminiService.js`, `backend/services/geminiVisionService.js`)
- Replaced scattered unvalidated calls and fabricated mock fallbacks (including removal of fake `confidence: 0.91` livestock mock responses and fake Wheat Yellow Rust templates).
- Added:
  - Configurable `GEMINI_MODEL` (`gemini-3-flash-preview` default) and optional `GEMINI_FALLBACK_MODELS`.
  - Strict timeouts (`GEMINI_TIMEOUT_MS`, default `15000`ms), bounded retries with jitter on transient `503`/timeouts (no retry storms on daily `429 RESOURCE_EXHAUSTED`), and markdown JSON fence stripping + schema validator (`validateAgainstSchema`).
  - Per-user sliding-window RPM (`GEMINI_USER_RPM`) & RPD (`GEMINI_USER_RPD`) rate limiting.
  - Per-request IDs (`gem_*`), token/latency accounting telemetry (`GET /api/ai/status` + optional Mongo `GeminiUsage` logging), and semantic error classification (`not_configured`, `quota_exhausted`, `rate_limited`, `timeout`, `invalid_schema`, `upstream_unavailable`).
  - Key-redacted error logging and test injection hook (`setGeminiClientFactoryForTests`) so offline test suites never waste API quota.

### B. MongoDB Schema & Android Room v3 -> v4 Migration
- **Backend Models (`backend/models/`):** Extended `User`, `CropAnalysis`, `LivestockAnalysis`, `ChatHistory`; added `WeatherCache`, `CropPlan`, `PlannerTask`, `SavedScheme`, `MarketFavorite`, `SyncOperation`, and `GeminiUsage`.
- **Android Room Database (`KissanDatabase.kt`, `DatabaseModule.kt`):**
  - Incremented DB version from `3` to `4` with explicit non-destructive `MIGRATION_3_4` (never `.fallbackToDestructiveMigration()`).
  - Added user-id scoping (`userId` column + `clearForUser` on logout in `AuthViewModel`) across user-owned tables (`crop_history`, `chat_messages`, `livestock_history`, `crop_plans`, `planner_tasks`, `saved_schemes`, `market_favorites`, `sync_operations`).
  - Extended `market_prices` (`category`, `sourceBadge`) and `crop_history` (`secondOpinion*`, `reconciliationStatus`, `reconciliationNote`).

---

## 2. Feature Modules Delivered End-to-End

1. **Dedicated Weather Intelligence (`weatherService.js`, `WeatherRepository`, `WeatherScreen`):**
   - Live Open-Meteo integration (`200 OK` verified) + geocoding / Indian agri-hub fallback coordinates.
   - Deterministic agronomic weather rules engine evaluating spraying suitability, rainfall wash-off risk, wind drift, irrigation deferral, heat stress, and frost risk.
   - 30-min MongoDB TTL cache (`WeatherCache`) + Room `weather_cache` with honest `isStale` fallback.
   - Compose `WeatherScreen` with voice Mic district entry and TTS alert playback.

2. **Smart Crop Planner & Care Calendar (`cropPlannerService.js`, `PlannerRepository`, `CropPlannerScreen`, `PlannerReminderWorker`):**
   - Deterministic 7-day crop/stage/soil/irrigation planner integrating live weather forecasts to reschedule sprays away from rainy/windy days (`weatherAdjusted`, `weatherReason`).
   - Full CRUD & regeneration (`POST/GET /api/planner/plans`, `PATCH /api/planner/tasks/:taskId`, `POST /api/planner/plans/:id/regenerate`).
   - WorkManager daily reminder notifications (`PlannerReminderWorker`) + offline Room mutation queueing via `OfflineSyncManager`.

3. **Crop Disease Explicit Consent Second Opinion (`cropController.js`, `CropViewModel`, `CropDiseaseScreen`):**
   - Preserved V1 DINOv2 production outputs (`confidence = null`, separated cosine similarity & neighbor agreement, organic & chemical treatments withheld on `needs_review = true`).
   - Added `POST /api/crop/second-opinion` requiring explicit `farmerConsented=true`, reconciles agreement/disagreement/non-leaf status without mutating DINOv2 provenance, rendered as a consent flow + comparison card in `CropDiseaseScreen`.

4. **Livestock Health Assistant (`livestockController.js`, `LivestockRepository`, `LivestockScreen`):**
   - Added structured clinical intake (`animalType`, age, symptom duration, fever, appetite, milk yield change, lesions/ulcers, herd isolation, voice clinical notes).
   - Added deterministic red-flag escalation rules (fever + ulcers, poultry mortality/neurological signs, acute distress) surfacing immediate veterinary emergency alerts (`1962` helpline) and strict prohibition on unverified prescription drug/antibiotic dosages (`prescriptionsWithheld: true`).
   - Added `GET /api/livestock/history` backed by MongoDB & Room (`livestock_history`).

5. **Market / Mandi Prices (`marketDataService.js`, `marketController.js`, `PricesRepository`, `LivePricesScreen`):**
   - Removed random `Math.random()` simulated price drift (`simulateMarketUpdate`); implemented honest `sourceStatus` reporting (`live_agmarknet`, rate-limit cooldown, or reference dataset).
   - Added multi-mandi arbitrage comparison (`GET /api/market/compare`), district/sort queries, and user-scoped saved favorites (`MarketFavorite` + star bookmark & comparison view in `LivePricesScreen`).

6. **Verified Government Schemes (`schemeCatalogService.js`, `schemeController.js`, `SchemesRepository`, `SchemesScreen`):**
   - Added curated verified catalog (`10` official Indian schemes with `lastVerifiedAt`, official `.gov.in` links, required documents, application steps, helplines).
   - Built deterministic eligibility scorer (`POST /api/schemes/eligibility`), multi-language Gemini explainer (`POST /api/schemes/explain`), and Room-backed offline bookmarking (`SavedScheme`).

7. **Tool-Grounded Multi-Turn Chatbot (`chatController.js`, `ChatRepository`, `ChatbotScreen`):**
   - Added session turn history (`GET/DELETE /api/chat/history`), category routing (`crop`, `livestock`, `mandi`, `schemes`, `weather`, `planner`, `general`), and real context injection (`mandi_prices`, `verified_schemes`, `weather`, `crop_history`, `livestock_history`, `crop_planner`, `farm_profile`) surfaced as `Grounded:` chips in Android UI.

8. **Offline WorkManager Write Sync & Farm Profile (`syncController.js`, `OfflineSyncManager`, `DataSyncWorker`, `SettingsScreen`):**
   - Added idempotent batch sync (`POST /api/sync/batch` + `SyncOperation`) replaying offline queued plans, task toggles, scheme bookmarks, and market favorites on reconnect.
   - Added persistent farmer profile editor (`PUT /api/auth/profile`) with voice auto-fill in `SettingsScreen`.

9. **External Call-Agent Contract (`callAgentRoutes.js`):**
   - Exposed `GET /api/integrations/call-agent/contract` & secret-guarded `POST /api/integrations/call-agent/webhook` ready for external telephony partners without creating an out-of-scope dialer.
