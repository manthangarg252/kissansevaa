# KissanSevaAI — Stage 2 Final Functional QA Report

**Verification Date:** November 26, 2026  
**Branch:** `stage1/autonomous-implementation` (base Stage 1 commit `38c5914`, plus Stage 2 bug fixes & QA artifacts)  
**Test Environment:**
- **ML Service (`:8000`):** FastAPI (`dinov2_vits14`, V1 active, `shadow_mode_enabled=true`, 67 classes, 2,144 reference embeddings)
- **Backend (`:5050`):** Node.js/Express + `MongoMemoryServer` (`backend/scripts/startDevServer.js`)
- **Android Client (`com.kissansevaai`):** `android/app/build/outputs/apk/debug/app-debug.apk` (`24.5 MB` / `25,695,489` bytes) verified interactively on `test_pixel` (`emulator-5554`, Android 14 / API 34)

---

## 1. Honest Feature Classification Matrix

Allowed Stage 2 classifications used strictly below:
- **`VERIFIED END TO END`** — Verified across Android UI -> Node.js/FastAPI/Room -> MongoDB with live responses/persistence in Stage 2.
- **`VERIFIED WITH TEST FIXTURES ONLY`** — Code & UI implemented and verified via automated mock/integration test suites (`pytest`, `node --test`, JUnit), while live external AI/telephony execution could not complete live.
- **`IMPLEMENTED BUT NOT LIVE-VERIFIED`** — Code implemented & compiled, not acoustically/externally exercised live in Stage 2.
- **`BLOCKED`** — External dependency (API quota exhausted, physical device locked/disconnected, external SIP partner missing).
- **`FAILED`** — Failing check. *(None remain in core offline/deterministic/V1 paths after Stage 2 fixes.)*

| Feature / Workflow | Classification | Stage 2 Verification Evidence & Notes |
|---|---|---|
| **Authentication (Email/PIN Login, Profile Hydration, Isolation)** | `VERIFIED END TO END` | Logged in cleanly on `emulator-5554` (`farmer@kissan.com` / `Password123!`); API isolation verified (`u1` vs `u2` on Crop, Livestock, Planner). Biometric OS prompt & SMS OTP gateway remain device/SMS-provider dependent (`IMPLEMENTED BUT NOT LIVE-VERIFIED`). |
| **Crop Disease V1 Detection (`DINOv2-S/14` + `FAISS`)** | `VERIFIED END TO END` | Real leaf upload via Android Photo Picker -> `:5050` -> `:8000`. Displayed `Wheat Aphid`, `Cosine Similarity 0.244`, `k-NN Neighbor Agreement 20.0%`, `Model: DINOv2-S/14`, explicit disclaimer, `Needs Review`, and **withheld BOTH organic and chemical treatments** (`04_crop_v1.png`, `04b_crop_v1_consent.png`). Shadow V3 logged `13` entries to `ml-service/logs/shadow_comparison.jsonl` without replacing V1. |
| **Crop Disease Explicit Gemini Second Opinion** | `VERIFIED WITH TEST FIXTURES ONLY` (`BLOCKED` live by Gemini 429) | Android consent gate (`enabled=false` until checkbox ticked, `04c_crop_second_opinion.png`) and backend `400` rejection (`farmerConsented=false`) verified live. Backend + Android reconciliation UI tested with fixtures (`stage1ApiIntegration.test.js`, `Stage1FeaturesTest.kt`); live Gemini call returns honest `429 quota_exhausted` banner. |
| **Livestock Clinical Triage & Red-Flag Escalation** | `VERIFIED END TO END` (rules & escalation) / `BLOCKED` (live Gemini AI vision) | Interactive UI submission (`Appetite Reduced`, `Milk Stopped`, `Fever Yes`, `Visible Lesions`, `05_livestock.png`) triggered immediate **red-flag `URGENT VETERINARY ESCALATION RECOMMENDED`** card + `1962` helpline. Stage 2 fix (`LivestockRepository.parseHttpDegradedTriage`) ensures `429`/`503` degraded responses never swallow `urgentVetEscalation` or `immediateAction`. |
| **Weather Intelligence (Open-Meteo Live + Spray Window)** | `VERIFIED END TO END` | Live Open-Meteo API `200 OK` observed (`Nagpur, Maharashtra`: `18°C`, `61%` humidity, `5.6 km/h` wind, `Spraying Window: Suitable`, `03_weather.png`). Stage 2 `optionalProtect` middleware fix ensures authenticated Dashboard/Weather calls personalize to Nagpur instead of falling back to New Delhi (`02_dashboard.png`). |
| **Live Mandi Prices, Agmarknet Sync, Favorites & Compare** | `VERIFIED END TO END` | Live `data.gov.in` Agmarknet refresh populated `71` listings (`36` crops, `21` veg, `14` fruit, `sourceStatus: live_agmarknet`). Verified Veg filter, Apple favorite persistence (`FAVORITES: 1`), Compare Mandis spreads (`06_mandi.png`, `06b_mandi_compare.png`), and 5-min cooldown on upstream `429`. |
| **Government Schemes Catalog, Bookmarks & Eligibility** | `VERIFIED END TO END` | Verified `10` schemes live (`PM-KISAN`, `PMFBY`, etc.), scheme bookmark toggle (`SAVED_SCHEMES: 1`, `07_schemes.png`), and `Eligibility Check` pre-populated from farm profile (`Maharashtra`, `4` acres, `Black Cotton`, `07b_schemes_eligibility.png`). AI "Explain" prose uses Gemini and is `VERIFIED WITH TEST FIXTURES ONLY` (`BLOCKED` live by 429). |
| **Smart Crop Planner (7-Day Rules + Weather Sync)** | `VERIFIED END TO END` | Deterministic 7-day schedules generated (`Wheat`, `Soybean`, `Cottonan`), visible both in Planner (`08_planner.png`) and Dashboard preview (`02b_dashboard_toolkit.png`). Honest `quota_exhausted` note shown while rules tasks succeed cleanly. |
| **Offline-First Room Queue, Restart Persistence & Sync Dedup** | `VERIFIED END TO END` | With `adb reverse --remove-all` and `svc wifi/data disable`, created `"Cottonan"` plan offline and checked task `dayOffset=0` (`09_offline_pending.png`). Survived `am force-stop com.kissansevaai` cold restart offline (`2 offline change(s) queued` preserved). Upon restoring network + tapping sync, backend showed `3` plans (`['Cottonan', 'Soybean', 'Wheat']`) with **zero duplicates**, `6` server tasks, and `dayOffset=0` `status='completed'` (`10_offline_synced.png`). |
| **Multilingual UI, STT & TTS Infrastructure** | `VERIFIED END TO END` (UI & Engine Init) / `IMPLEMENTED BUT NOT LIVE-VERIFIED` (Live Mic Audio) | Top-bar & Settings language switcher, Hindi labels, and `SpeechRecognizer.isRecognitionAvailable = true` + `com.google.android.tts` (`en-IN`, `hi-IN`, `mr-IN`, `pa-IN` available; `sa-IN` unsupported) verified via `PlatformVoiceDiagnosticTest` (`12_settings.png`). Live spoken mic capture was not acoustically driven on headless emulator. |
| **AI Assistant Chatbot, Loans, Carbon, Traders, Market Insights, IoT** | `VERIFIED END TO END` (Deterministic Screens) / `VERIFIED WITH TEST FIXTURES ONLY` (Live Gemini Replies) | Navigation & screen rendering verified (`11_chatbot.png`, `13_iot_monitoring.png`, `14_market_insights.png`). Backend multi-turn Chat + tool context tested in `stage1ApiIntegration.test.js`; live Gemini completions blocked by `429 quota_exhausted`. |
| **External Call-Agent Telephony Webhook** | `IMPLEMENTED BUT NOT LIVE-VERIFIED` | `GET /api/integrations/call-agent/contract` returns `200 OK` (`contract_ready_external_telephony_required`). No SIP/IVR provider or `CALL_AGENT_WEBHOOK_SECRET` configured. |

---

## 2. Stage 2 Root-Cause Bugs Found & Fixed

1. **HTTP Error Body Messages Swallowed (`Resource.kt`):** Generic `"Too many requests"` replaced helpful backend `429`/`503` explanations; added `extractHttpErrorBodyMessage()` with unit coverage in `Stage1FeaturesTest.kt`.
2. **Degraded Livestock Emergency Red Flags Hidden (`LivestockRepository.kt`, `LivestockScreen.kt`):** HTTP `429`/`503` exceptions bypassed structured degraded triage cards; added `parseHttpDegradedTriage(e)` so `urgentVetEscalation`, `escalationReason`, and `immediateAction` remain visible.
3. **Stale Physical Debug LAN Host (`NetworkModule.kt`):** Replaced hardcoded `10.201.214.248` with `127.0.0.1` (`adb reverse`) and prioritized fallback candidates `["127.0.0.1", "10.0.2.2", "192.168.1.7"]` with bounded timeouts.
4. **Offline Task Sync `CastError` & Room Local Placeholder Leakage (`syncController.js`, `CropPlannerEntities.kt`, `PlannerRepository.kt`, `OfflineSyncManager.kt`):**
   - Offline tasks (`localtask_<reqId>_0`) previously caused Mongoose `ObjectId` cast errors on `planner_task_update`. Fixed via `planClientRequestId` + `dayOffset` resolution on server and cleanup of `local_*` Room placeholders after batch sync. Scoped pending queue count to active `userId`.
5. **Startup Market Auto-Fetch (`marketDataService.js`):** Added non-test startup APMC refresh so newly launched dev servers boot with live Agmarknet data instead of waiting for manual refresh.
6. **Dashboard Weather Location Unpersonalized (`authMiddleware.js`, `weatherRoutes.js`, `schemeRoutes.js`):** Added `optionalProtect` so authenticated weather/scheme calls personalize to user district/state (`Nagpur, Maharashtra`) without blocking unauthenticated callers.
7. **Compact-Screen Badge Wrapping (`DashboardScreen.kt`, `LivePricesScreen.kt`):** Fixed squished `"Clear Sky"` pill (`DashboardScreen.kt` line 507), vertical letter-wrapping `"View All →"` (`DashboardScreen.kt` lines 848-869, `02b_dashboard_toolkit.png`), and vertical `"Spread ₹.../q"` badge wrapping on long crop names (`LivePricesScreen.kt` lines 351-368, `06b_mandi_compare.png`).
8. **Room Migration & Platform Voice Instrumentation (`MigrationAndScreenTest.kt`):** Added real Android SQLite v3 -> v4 `MIGRATION_3_4` + DAO instrumentation test verifying zero data loss across database upgrades.

---

## 3. Final Post-Edit Automated Test Summary

| Layer | Command | Result |
|---|---|---|
| **FastAPI ML** | `cd ml-service && venv/bin/python3 -m pytest tests/ -v --tb=short` | **16/16 passed** (`1.39s`) |
| **Node.js Backend** | `cd backend && NODE_ENV=test node --test tests/*.test.js` *(outside sandbox for MongoMemoryServer/HTTP sockets)* | **9/9 passed** (`1.42s`) |
| **Android JVM Unit** | `./android/gradlew -p android testDebugUnitTest --offline --console=plain` | **65/65 passed** (`10` test classes) |
| **Android Connected** | `ANDROID_SERIAL=emulator-5554 ./android/gradlew -p android connectedDebugAndroidTest --offline --console=plain` | **2/2 passed** (`test_pixel(AVD) - 14`) |
