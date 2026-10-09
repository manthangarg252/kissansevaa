# KissanSevaAI Android — Architecture & Implementation Plan

> **Phase 0 deliverable — Audit-only, no code yet.**
> Generated from a full read of the repo. Where this brief and the repo disagree, this plan follows the repo.

---

## 1. Repo Audit — Discrepancies vs. User Brief

| # | Brief said | Repo says | This plan follows |
|---|-----------|-----------|-------------------|
| 1 | Livestock result shape: `{summary, treatment[], prevention[], vetAdvice}` | Backend uses `{symptoms[], immediateAction, longTermCare, vetConsultationAdvice}` (see `livestockController.js` + `LivestockAnalysis.js`) | **Backend schema** — the Android app calls the backend |
| 2 | `SensorData` has `moisture`, `ph` | Backend IoT model/controller uses `soilMoisture`, `phLevel` | **Backend field names** (`soilMoisture`, `phLevel`) |
| 3 | "Most AI features call Gemini via geminiService.ts / api/gemini.js" | Those are **frontend-only** paths calling a Vite proxy. The backend has its own `services/geminiService.js` using `@google/genai`. Android calls backend endpoints, never those frontend paths. | Documented; Android uses only `/api/*` backend routes |
| 4 | "Trader advisory" feature exists | No backend AI endpoint for trader advisory. Only `GET /api/traders` (list) and `POST /api/traders/add`. The frontend calls the frontend Gemini proxy directly. | **Route through chat endpoint** — see §5 |
| 5 | "Voice form filling (speech -> field extraction)" | `extractFieldsFromSpeech` calls frontend Gemini proxy, no backend route. | **Route through chat endpoint** — see §5 |
| 6 | `MarketPrice.trend` in types.ts | Backend mock data has no `trend` field | Default to `"stable"` on Android if field missing |
| 7 | IoT insights AI | `getIoTInsights` calls frontend Gemini proxy only, no backend route | **Route through chat endpoint** — see §5 |
| 8 | Carbon credits AI | Backend has `POST /api/carbon/estimate` ✅ | Works |
| 9 | Scheme recommendations AI | Backend has `POST /api/schemes/recommendations` ✅ | Works |

---

## 2. Backend Endpoints — Full Inventory

### Existing endpoints the Android app will use

| Method | Path | Auth | Body / Query | Response | Notes |
|--------|------|------|-------------|----------|-------|
| `POST` | `/api/auth/register` | No | `{name, email, password}` | `{id, name, email, token}` | |
| `POST` | `/api/auth/login` | No | `{email, password}` | `{id, name, email, token}` | |
| `GET` | `/api/auth/me` | Bearer | — | User object | |
| `POST` | `/api/crop/analyze` | Bearer | `multipart: image, location, soilType, language` | `{source, cropName, diseaseName, confidence, severity, summary, treatment:{organic[], chemical[]}, prevention[], language, id}` | |
| `GET` | `/api/crop/history` | Bearer | — | `CropAnalysis[]` | |
| `POST` | `/api/livestock/analyze` | Bearer | `multipart: image, animalType, language` | `{conditionName, confidence, symptoms[], immediateAction, longTermCare, vetConsultationAdvice, imageUrl}` | |
| `POST` | `/api/chat` | Bearer | `{message, language, mode}` | `{response}` | |
| `GET` | `/api/market` | No | `?crop=&state=` | `MarketPrice[]` (no trend field) | |
| `POST` | `/api/market/recommendations` | Bearer | `{location, soilType, farmSize, currentCrop, irrigation, language}` | `{response}` | |
| `POST` | `/api/schemes/recommendations` | Bearer | `{state, farmSize, crop, soilType, landType, language}` | `{response}` | |
| `POST` | `/api/carbon/estimate` | Bearer | `{farmSize, cropType, method, irrigation, language}` | `{response}` | |
| `POST` | `/api/loans/advisor` | Bearer | profile object + `language` | `LoanAdvisorResult` JSON | |
| `GET` | `/api/traders` | No | `?location=&search=` | `Trader[]` | |
| `POST` | `/api/traders/add` | Bearer | Trader fields | `{id, message}` | |
| `GET` | `/api/iot/live` | No | — | `{temperature, humidity, soilMoisture, phLevel, timestamp}` | |
| `POST` | `/api/iot/update` | No | sensor fields | `{status}` | |
| `POST` | `/api/traders/advisory` | Bearer | `{query, language}` | `{response}` | Dedicated AI trading advisory endpoint |
| `POST` | `/api/iot/insights` | Bearer | `{sensorData, language}` | `{response}` | Dedicated AI IoT sensor insights endpoint |
| `POST` | `/api/extract-fields` | Bearer | `{text, fields: string[], language}` | `{extractedFields: Record<String, String?>}` | Strict JSON dynamic field extraction |

### Dedicated Endpoints Implemented for Android
Per Phase 1 approved instructions, dedicated endpoints were added to `backend/` rather than multiplexing `/api/chat`:
- **Trader advisory**: `POST /api/traders/advisory` (`traderRoutes.js`, `traderController.js`)
- **IoT insights**: `POST /api/iot/insights` (`iotRoutes.js`, `iotController.js`)
- **Field extraction**: `POST /api/extract-fields` (`fieldExtractionRoutes.js`, `fieldExtractionController.js`) returning strict JSON via `generateStructuredJSON`
- **Request/Response Logger**: Dev-only JSONL logging added via `backend/middleware/requestLogger.js` (redacts sensitive tokens/passwords, records latency & payloads)
- **Multer Fix**: Fixed `multer.diskStorage` buffer mismatch in `cropController.js` and `livestockController.js` using `fs.readFileSync(file.path)`

---

## 3. Screen Mapping (React → Compose)

| React Page / Component | Route | Compose Screen | Notes |
|------------------------|-------|----------------|-------|
| `Login.tsx` | `/login` | `LoginScreen` | Email/password, language picker |
| `Signup.tsx` | `/signup` | `SignupScreen` | Name/email/password, language picker |
| `Dashboard.tsx` | `/` | `DashboardScreen` | 9 feature cards grid |
| `CropDisease.tsx` | `/crop-disease` | `CropDiseaseScreen` | Image upload → analysis result card |
| `Livestock.tsx` | `/livestock` | `LivestockScreen` | 2 tabs: Disease Detection + AI Assistant chat |
| `MarketInsights.tsx` | `/market-insights` | `MarketInsightsScreen` | Farm profile form → AI recommendations |
| `IoTMonitoring.tsx` | `/iot` | `IoTMonitoringScreen` | Live sensor gauges + AI insights + chart |
| `GovernmentSchemes.tsx` | `/schemes` | `SchemesScreen` | Scheme cards + AI recommendation modal |
| `LoansPage.tsx` | `/loans` | `LoansScreen` | Loan products + EMI calculator + AI eligibility |
| `TradersDirectory.tsx` | `/traders` | `TradersScreen` | Search/filter + AI advisory chat |
| `CarbonCredits.tsx` | `/carbon` | `CarbonCreditsScreen` | Farm form → AI estimation |
| `LiveMarketPrices.tsx` | `/live-prices` | `LivePricesScreen` | Filterable price table |
| `Chatbot.tsx` | `/chatbot` | `ChatbotScreen` | Full-screen chat with voice I/O |
| `Layout.tsx` | (wraps all) | `MainScaffold` (NavBar + FAB) | Language picker in top bar, chatbot FAB |
| `VoiceFormFillButton.tsx` | (component) | `VoiceFormFillButton` composable | Speech → field extraction |
| `MicButton.tsx` | (component) | `MicButton` composable | Simple STT toggle |
| `TTSButton.tsx` | (component) | `TtsButton` composable | Read text aloud |
| `VoiceControls.tsx` | (component) | `VoiceControls` composable | Combined mic + TTS strip |
| `AnalysisResultCard.tsx` | (component) | `AnalysisResultCard` composable | Disease analysis display |
| — (new) | — | `SettingsScreen` | Base URL override, engine selection, language, model download manager |

---

## 4. Technology Choices

| Category | Library | Version | Rationale |
|----------|---------|---------|-----------|
| **UI** | Jetpack Compose (BOM) | `2025.05.00` | Current stable Compose BOM |
| **Material** | Material 3 | via Compose BOM | Consistent with M3 design |
| **Navigation** | Navigation Compose | `2.9.0` | Type-safe navigation |
| **DI** | Hilt | `2.56.1` / `hilt-navigation-compose 1.2.0` | Standard DI |
| **Networking** | Retrofit 2 + OkHttp | `2.11.0` / `4.12.0` | Multipart upload support; kotlinx-serialization converter |
| **Serialization** | kotlinx.serialization | `1.8.1` | `@Serializable` data classes |
| **Local DB** | Room | `2.7.1` | Offline cache |
| **Preferences** | DataStore Preferences | `1.1.7` | Settings, feature flags |
| **Async** | Kotlin Coroutines + Flow | `1.10.2` | Reactive streams |
| **Image loading** | Coil Compose | `3.2.0` | Lightweight, Compose-native |
| **Camera** | CameraX | `1.5.0` | Photo capture |
| **Background** | WorkManager | `2.10.1` | Background sync |
| **TTS** | Android `TextToSpeech` | Platform API | No extra dependency |
| **STT** | Android `SpeechRecognizer` | Platform API | Temporary; replaced by IndicWhisper in Phase 4 |
| **On-device ML** | LiteRT (TFLite) runtime | `2.18.0` | Phase 3 crop disease |
| **On-device LLM** | MediaPipe Tasks GenAI | `0.10.24` | Phase 4, chat |
| **Charts** | Vico | `2.1.2` | Sensor history charts |
| **Kotlin** | `2.1.20` | Current stable |
| **AGP** | `8.8.2` | Current stable |
| **Compile SDK** | 35 | |
| **Min SDK** | 26 | Per requirement |
| **Target SDK** | 35 | |

> I will verify latest exact stable versions against Maven Central / Google Maven before writing `build.gradle.kts`.

---

## 5. AI Engine Layer — Detailed Design

### 5.1 Interfaces (one per capability)

```
ai/
├── engine/
│   ├── CropDiseaseEngine.kt       // suspend fun analyze(image, location, soilType, lang): CropDiseaseResult
│   ├── LivestockEngine.kt         // suspend fun analyze(image, animalType, lang): LivestockResult
│   ├── ChatEngine.kt              // suspend fun chat(message, mode, lang): ChatResponse
│   ├── AdvisoryEngine.kt          // suspend fun getMarketAdvice(form, lang): AdvisoryResponse
│   │                              // suspend fun getSchemeAdvice(form, lang): AdvisoryResponse
│   │                              // suspend fun getCarbonEstimate(form, lang): AdvisoryResponse
│   │                              // suspend fun getIoTInsights(data, lang): AdvisoryResponse
│   │                              // suspend fun getTraderAdvisory(query, lang): AdvisoryResponse
│   ├── LoanAdvisorEngine.kt       // suspend fun advise(profile, lang): LoanAdvisorResult
│   ├── FieldExtractor.kt          // suspend fun extract(text, fields, lang): Map<String, Any?>
│   ├── SpeechToText.kt            // fun start(lang, onResult, onError); fun stop()
│   └── TextToSpeechEngine.kt      // fun speak(text, lang, onDone); fun stop()
├── remote/
│   ├── RemoteCropDiseaseEngine.kt
│   ├── RemoteLivestockEngine.kt
│   ├── RemoteChatEngine.kt        // calls /api/chat
│   ├── RemoteAdvisoryEngine.kt    // calls /api/market/recommendations, /api/schemes/recommendations,
│   │                              // /api/carbon/estimate, /api/chat (for trader + IoT)
│   ├── RemoteLoanAdvisorEngine.kt
│   └── RemoteFieldExtractor.kt    // calls /api/chat with extraction prompt
├── ondevice/                      // Phase 3+
│   ├── OnDeviceCropDiseaseEngine.kt
│   ├── AndroidSpeechToText.kt     // Phase 2 (platform SpeechRecognizer)
│   ├── AndroidTextToSpeech.kt     // Phase 2 (platform TTS)
│   ├── OnDeviceSpeechToText.kt    // Phase 4 stub (IndicWhisper)
│   └── OnDeviceChatEngine.kt      // Phase 4 stub (MediaPipe GenAI)
└── router/
    └── EngineRouter.kt            // Chooses impl based on: user pref, connectivity, device tier, feature flag
```

### 5.2 EngineRouter decision logic

```
For each engine call:
  1. Check DataStore feature flag: "engine.{capability}.mode" → "auto" | "remote" | "ondevice"
  2. If "remote" → always RemoteXxx
  3. If "ondevice" → check model availability → if missing, fallback to Remote
  4. If "auto":
     a. Check connectivity (ConnectivityManager)
     b. Check device tier (ActivityManager.memoryClass, Build.SOC_MODEL)
     c. Check model availability
     d. If on-device model available AND (offline OR device_tier >= MEDIUM) → OnDeviceXxx
     e. Else → RemoteXxx
  5. Log: engine used, latency_ms, confidence (if applicable) → Room table `engine_log`
```

### 5.3 Device capability tiers

| Tier | RAM | Criteria |
|------|-----|----------|
| LOW | < 3 GB | Only remote + TFLite crop disease |
| MEDIUM | 3–6 GB | + on-device STT |
| HIGH | > 6 GB | + on-device LLM |

---

## 6. Package Structure

```
com.kissansevaai/
├── app/                         # Application, Hilt setup
├── core/
│   ├── data/                    # Room DB, DAOs, DataStore
│   ├── network/                 # Retrofit service, AuthInterceptor, NetworkMonitor
│   ├── model/                   # @Serializable data classes (from types.ts)
│   └── util/                    # Extensions, ImageCompressor, DeviceTier
├── ai/                          # Engine interfaces, router, remote/ondevice impls
├── feature/
│   ├── auth/                    # LoginScreen, SignupScreen, AuthViewModel, AuthRepository
│   ├── dashboard/               # DashboardScreen
│   ├── crop/                    # CropDiseaseScreen, CropViewModel, CropRepository
│   ├── livestock/               # LivestockScreen, LivestockViewModel
│   ├── market/                  # MarketInsightsScreen, MarketViewModel
│   ├── iot/                     # IoTMonitoringScreen, IoTViewModel
│   ├── schemes/                 # SchemesScreen, SchemesViewModel
│   ├── loans/                   # LoansScreen, LoansViewModel
│   ├── traders/                 # TradersScreen, TradersViewModel
│   ├── carbon/                  # CarbonCreditsScreen, CarbonViewModel
│   ├── prices/                  # LivePricesScreen, PricesViewModel
│   ├── chat/                    # ChatbotScreen, ChatViewModel
│   └── settings/                # SettingsScreen, SettingsViewModel
├── ui/
│   ├── theme/                   # Color, Type, Theme (M3 dynamic)
│   ├── component/               # Shared composables (AnalysisResultCard, VoiceControls, etc.)
│   └── navigation/              # NavGraph, Screen sealed class
└── ml/                          # Phase 3+: LiteRT loader, preprocessor, parity tests
```

---

## 7. Offline Strategy

| Data | Cache strategy | Staleness |
|------|---------------|-----------|
| Government schemes | Pre-populated Room from `constants.tsx` SCHEMES array + API refresh | Show cached; refresh in background |
| Loan products | Pre-populated Room from LOAN_PRODUCTS array | Same |
| Traders | Room cache from `GET /api/traders` | Last-fetched; pull-to-refresh |
| Market prices | Room cache from `GET /api/market` | Show with "last updated" timestamp |
| Crop scan history | Room cache from `GET /api/crop/history` | Synced on open |
| Chat history | Room (local) + optional server sync | Local-first |
| IoT readings | Room cache; WorkManager periodic sync | Show "offline" badge when no connectivity |
| AI results | All AI calls need network (until Phase 3/4) | Show "requires network" overlay |

---

## 8. i18n Strategy

- Convert all 5 JSON locale files (`en.json`, `hi.json`, `mr.json`, `pa.json`, `sa.json`) to Android `values-{qualifier}/strings.xml`:
  - `values/strings.xml` (English, default)
  - `values-hi/strings.xml` (Hindi)
  - `values-mr/strings.xml` (Marathi)
  - `values-pa/strings.xml` (Punjabi)
  - `values-sa/strings.xml` (Sanskrit)
- Use `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(...))` for per-app locale (API 33+ native, AppCompat polyfill for 26-32).
- Sanskrit TTS: detect unavailable voice at runtime → fall back to `hi-IN` voice → show user a snackbar: "Sanskrit voice unavailable, using Hindi".
- Flatten nested JSON keys to Android resource IDs: `dashboard.cards.crop_detect` → `dashboard_cards_crop_detect`.
- The `TRANSLATIONS` object in `constants.tsx` (used on Login/Signup) overlaps with `locales/*.json` but has additional voice strings. Merge both into Android resources.

---

## 9. Risks & Mitigations

| Risk | Severity | Mitigation |
|------|----------|------------|
| Backend has no routes for trader advisory, IoT insights, field extraction | Medium | Route through `/api/chat` with structured mode; add dedicated routes later if quality is poor |
| ViT model checkpoint (22 MB `.pth`) needs conversion to LiteRT | Medium | Phase 3 export script with ONNX fallback |
| CLASS_NAMES are placeholders (`class_0`..`class_14`) | Info | Load from `labels.json` at runtime; show raw class name until file is supplied |
| Sanskrit has no system TTS voice on most devices | Low | Detected at runtime; fall back to Hindi with user notification |
| Large on-device models (Gemma, IndicWhisper) could be > 1 GB | High | Phase 4: download-on-demand with progress, resume (OkHttp range requests), SHA256 integrity check. Never bundled in APK. |
| Backend uses MongoDB ObjectId `_id` field | Low | Map to `id` in Kotlin with `@SerialName("_id")` where needed |
| Two different auth middleware files (`auth.js`, `authMiddleware.js`) | Info | Both use same JWT logic; traderRoutes uses `auth.js`, rest use `authMiddleware.js`. No issue for Android. |
| Backend `multer` expects `file.buffer` but disk storage gives `file.path` | Info | Backend issue (crop controller reads `file.buffer` but multer.diskStorage doesn't populate buffer). This is a pre-existing backend bug. Android sends multipart correctly either way. |
| Compose BOM / library version drift | Medium | Pin all versions in a `libs.versions.toml` version catalog |

---

## 10. Phase Breakdown & Status Summary

| Phase | Scope | Backend Changes | Status |
|-------|-------|----------------|--------|
| **0** | Repo audit, architecture plan, disparity analysis (`docs/PARITY.md`) | None | **COMPLETED** |
| **1** | Gradle scaffold, theme, all 12 Compose screens, RemoteXxx AI engines, Room & DataStore cache, language picker (5 languages: EN, HI, MR, PA, SA), Settings screen with base URL & engine selector, unit tests with test fixtures | Multer fix, dedicated routes (`/api/traders/advisory`, `/api/iot/insights`, `/api/extract-fields`), dev JSONL logger | **COMPLETED** (APK & tests passing) |
| **2** | CameraX capture, SpeechRecognizer STT, Android TTS, runtime permissions, WorkManager sync | None | Planned next |
| **3** | ViT→LiteRT export script, OnDeviceCropDiseaseEngine, parity test, router fallback | None | Planned |
| **4** | On-device STT/LLM interfaces + download manager + settings UI (no models) | None | Planned |
| **5** | R8, signing, README, architecture diagram, CI workflow | None | Planned |

---

## 11. Build & Signing

- `local.properties`: `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`
- `build.gradle.kts`: reads from `local.properties`, never hardcoded
- `.gitignore`: `*.jks`, `*.keystore`, `local.properties`, `.env`
- `BuildConfig.BASE_URL` set via `buildConfigField` — debug default `http://10.0.2.2:5000` (emulator → host), configurable in Settings screen
- **Debug APK Location**: `android/app/build/outputs/apk/debug/app-debug.apk` (Built & Verified)
- **Unit Test Suite**: `android/app/build/reports/tests/testDebugUnitTest/` (100% Passed)

