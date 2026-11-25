# KissanSevaAI — Stage 1 Verification & Test Results

**Verification Timestamp:** November 26, 2026  
**Result Summary:** All automated Python ML tests (`16/16`), Node.js backend unit & MongoMemoryServer integration tests (`9/9`), Android JVM unit tests (`63/63`), debug APK build (`24M`), and live backend/ML/Open-Meteo smoke checks **passed** after final edits.

---

## 1. FastAPI ML Service (`ml-service/`)
- **Command:** `cd ml-service && venv/bin/python3 -m pytest tests/ -v --tb=short`
- **Result:** **16 passed, 0 failed, 0 skipped** in `1.37s`.
- **Coverage:**
  - `test_endpoints.py` (5 tests): real V1 ONNX/FAISS inference, `/predict-crop` legacy bridge, missing asset handling.
  - `test_preprocess.py` (6 tests): portrait/landscape/square preprocessing, weighted k-NN voting, low-similarity review flagging.
  - `test_v3_shadow.py` (5 tests): V3 shadow mode isolation, rollback toggle, V1 model asset integrity.

---

## 2. Node.js Backend (`backend/`)
- **Command:** `cd backend && NODE_ENV=test node --test tests/*.test.js` *(run outside sandbox for ephemeral localhost HTTP ports and `MongoMemoryServer`)*
- **Result:** **9 passed, 0 failed, 0 skipped** in `1.33s`.
- **Suites:**
  - `tests/cropContract.test.js` (1 test): V1 DINOv2 attribution preserved, `confidence: null`, `needs_review: true`, BOTH organic and chemical treatments withheld.
  - `tests/geminiAndServices.test.js` (7 tests): Gemini structured schema validation & fence stripping, schema-mismatch rejection, key-redacted `429` quota classification, per-user RPM rate limiter, Open-Meteo weather rules engine, 7-day planner rain rescheduling, schemes eligibility & livestock red flags & crop reconciliation & mandi spreads.
  - `tests/stage1ApiIntegration.test.js` (1 full end-to-end integration scenario across 11 API workflows against real `MongoMemoryServer`): Auth/Profile, Weather, Planner create/task update, Livestock triage/history + honest `429` unavailability without fake confidence, Crop second-opinion consent gate & disagreement reconciliation, Schemes catalog/eligibility, Market compare, Multi-turn grounded Chatbot, Idempotent `/api/sync/batch`, `/api/ai/status`, `/api/integrations/call-agent/contract`.

---

## 3. Native Android (`android/`)
- **Command:**
  ```bash
  JAVA_HOME=/Users/manthangarg/.jdks/jdk-21.0.12.1+1/Contents/Home \
    ./android/gradlew -p android testDebugUnitTest assembleDebug --offline --console=plain
  ```
- **Build Artifact:** `android/app/build/outputs/apk/debug/app-debug.apk` (`24M`, `BUILD SUCCESSFUL`)
- **Unit Test Results (63 passed, 0 failures, 0 errors, 0 skipped):**
  - `com.kissansevaai.DataSyncWorkerTest`: `2` passed
  - `com.kissansevaai.EngineRouterTest`: `7` passed
  - `com.kissansevaai.ImageCompressorTest`: `1` passed
  - `com.kissansevaai.ModelParsingTest`: `12` passed
  - `com.kissansevaai.RepositoryCachingTest`: `19` passed
  - `com.kissansevaai.ResourceTest`: `7` passed
  - `com.kissansevaai.SpeechToTextTest`: `6` passed
  - `com.kissansevaai.Stage1FeaturesTest`: `5` passed (Room entity round-trip, WeatherRepository offline stale cache fallback, PlannerRepository offline sync queueing, OfflineSyncManager batch flush, Stage 1 DTO parsing)
  - `com.kissansevaai.TextToSpeechTest`: `2` passed
  - `com.kissansevaai.UnauthorizedInterceptorTest`: `2` passed

---

## 4. Live Local Server & External API Smoke Verification
- **ML Health (`http://localhost:8000/health`):** `200 OK` (`active_model="v1"`, `shadow_mode_enabled=true`, `classes=67`, `reference_images=2144`)
- **Backend Auth Login (`http://localhost:5050/api/auth/login`):** `200 OK`, seeded `farmer@kissan.com` logged in with farm profile (`Maharashtra / Nagpur`).
- **Live Weather (`http://localhost:5050/api/weather/forecast?location=Nagpur`):** `200 OK` via **Open-Meteo** (`19.5°C`, `spraySuitable: true`, `7` daily forecast days).
- **Seeded Crop Planner (`GET /api/planner/plans`):** `200 OK` (`1` plan, `6` scheduled tasks).
- **Verified Schemes (`GET /api/schemes`):** `200 OK` (`10` curated verified schemes).
- **Mandi Compare (`GET /api/market/compare`):** `200 OK` (`33` commodity comparisons; Data.gov.in upstream observed rate-limiting HTTP `429` cleanly handled without fabricated prices).
- **AI Status (`GET /api/ai/status`) & Call Agent Contract (`GET /api/integrations/call-agent/contract`):** `200 OK`.
