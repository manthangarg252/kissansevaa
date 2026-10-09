# KissanSevaAI — Feature Parity Matrix (React → Android)

> Every React page, component, and sub-feature mapped to its Compose equivalent with implementation status.

---

## Legend

| Status | Meaning |
|--------|---------|
| ⬜ | Not started |
| 🟡 | In progress |
| ✅ | Complete |
| 🔵 | Deferred to later phase |
| ➖ | Not applicable / intentionally skipped |

---

## Pages

| # | React Page | File | Compose Screen | Phase | Status | Notes |
|---|-----------|------|----------------|-------|--------|-------|
| 1 | Login | `Login.tsx` | `LoginScreen` | 1 | ⬜ | Email/password + language picker on auth screens |
| 2 | Signup | `Signup.tsx` | `SignupScreen` | 1 | ⬜ | Name/email/password + language picker |
| 3 | Dashboard | `Dashboard.tsx` | `DashboardScreen` | 1 | ⬜ | 9 feature cards in LazyVerticalGrid |
| 4 | Crop Disease | `CropDisease.tsx` | `CropDiseaseScreen` | 1 | ⬜ | Gallery image picker (Phase 1), CameraX (Phase 2) |
| 5 | Livestock | `Livestock.tsx` | `LivestockScreen` | 1 | ⬜ | Two tabs: Detection + AI Assistant chat |
| 6 | Market Insights | `MarketInsights.tsx` | `MarketInsightsScreen` | 1 | ⬜ | Farm profile form → AI recommendation text |
| 7 | IoT Monitoring | `IoTMonitoring.tsx` | `IoTMonitoringScreen` | 1 | ⬜ | Live gauges + AI insights + historical chart |
| 8 | Government Schemes | `GovernmentSchemes.tsx` | `SchemesScreen` | 1 | ⬜ | Scheme cards (Room-cached) + AI modal |
| 9 | Loans & Credit | `LoansPage.tsx` | `LoansScreen` | 1 | ⬜ | Loan products + EMI calculator + AI advisor |
| 10 | Traders Directory | `TradersDirectory.tsx` | `TradersScreen` | 1 | ⬜ | Search/filter + AI advisory chat panel |
| 11 | Carbon Credits | `CarbonCredits.tsx` | `CarbonCreditsScreen` | 1 | ⬜ | Farm form → AI carbon estimation |
| 12 | Live Market Prices | `LiveMarketPrices.tsx` | `LivePricesScreen` | 1 | ⬜ | Filterable price table with search |
| 13 | Chatbot | `Chatbot.tsx` | `ChatbotScreen` | 1 | ⬜ | Full chat UI with voice controls |
| 14 | — (new) | — | `SettingsScreen` | 1 | ⬜ | Base URL, language, engine mode, model downloads |

---

## Shared Components

| # | React Component | File | Compose Composable | Phase | Status | Notes |
|---|----------------|------|--------------------|-------|--------|-------|
| 1 | Layout (Navbar + FAB) | `Layout.tsx` | `MainScaffold` | 1 | ⬜ | TopAppBar with language pills + Chatbot FAB |
| 2 | AnalysisResultCard | `AnalysisResultCard.tsx` | `AnalysisResultCard` | 1 | ⬜ | Crop/livestock analysis display card |
| 3 | VoiceFormFillButton | `VoiceFormFillButton.tsx` | `VoiceFormFillButton` | 2 | ⬜ | STT → AI extraction → form fill |
| 4 | MicButton | `MicButton.tsx` | `MicButton` | 2 | ⬜ | Simple mic toggle |
| 5 | TTSButton | `TTSButton.tsx` | `TtsButton` | 2 | ⬜ | Read-aloud button |
| 6 | VoiceControls | `VoiceControls.tsx` | `VoiceControls` | 2 | ⬜ | Combined mic + TTS row |

---

## Feature Capabilities

### AI Features (via backend)

| # | Feature | Frontend Source | Backend Endpoint | Remote Engine | Phase | Status |
|---|---------|----------------|-----------------|---------------|-------|--------|
| 1 | Crop disease diagnosis | `geminiService.ts:analyzeCropDisease` | `POST /api/crop/analyze` | `RemoteCropDiseaseEngine` | 1 | ⬜ |
| 2 | Crop scan history | — | `GET /api/crop/history` | (direct API call) | 1 | ⬜ |
| 3 | Livestock health analysis | `geminiService.ts:analyzeLivestockDisease` | `POST /api/livestock/analyze` | `RemoteLivestockEngine` | 1 | ⬜ |
| 4 | AI Chatbot | `geminiService.ts:getGeminiChatResponse` | `POST /api/chat` | `RemoteChatEngine` | 1 | ⬜ |
| 5 | Market recommendations | `geminiService.ts:getMarketRecommendations` | `POST /api/market/recommendations` | `RemoteAdvisoryEngine` | 1 | ⬜ |
| 6 | Scheme recommendations | `geminiService.ts:getSchemeRecommendations` | `POST /api/schemes/recommendations` | `RemoteAdvisoryEngine` | 1 | ⬜ |
| 7 | Carbon credit estimation | `geminiService.ts:estimateCarbonCredits` | `POST /api/carbon/estimate` | `RemoteAdvisoryEngine` | 1 | ⬜ |
| 8 | Loan advisor | `geminiService.ts:getLoanAdvice` | `POST /api/loans/advisor` | `RemoteLoanAdvisorEngine` | 1 | ⬜ |
| 9 | Trader advisory | `geminiService.ts:getTraderAdvisory` | `POST /api/chat` (mode: Trader Advisory) | `RemoteAdvisoryEngine` | 1 | ⬜ |
| 10 | IoT sensor insights | `geminiService.ts:getIoTInsights` | `POST /api/chat` (mode: IoT Insights) | `RemoteAdvisoryEngine` | 1 | ⬜ |
| 11 | Voice field extraction | `geminiService.ts:extractFieldsFromSpeech` | `POST /api/chat` (mode: Field Extraction) | `RemoteFieldExtractor` | 2 | ⬜ |

### Data Features (CRUD / read-only)

| # | Feature | Backend Endpoint | Room Cache | Phase | Status |
|---|---------|-----------------|------------|-------|--------|
| 1 | Live market prices | `GET /api/market` | Yes | 1 | ⬜ |
| 2 | Traders list | `GET /api/traders` | Yes | 1 | ⬜ |
| 3 | IoT live data | `GET /api/iot/live` | Yes | 1 | ⬜ |
| 4 | Government schemes (static) | Hardcoded in `constants.tsx` | Pre-populated Room | 1 | ⬜ |
| 5 | Loan products (static) | Hardcoded in `constants.tsx` | Pre-populated Room | 1 | ⬜ |

### Native Capabilities

| # | Feature | Phase | Status | Notes |
|---|---------|-------|--------|-------|
| 1 | CameraX photo capture | 2 | ⬜ | Crop + livestock screens |
| 2 | Gallery image picker | 1 | ⬜ | ActivityResultContracts |
| 3 | Android SpeechRecognizer (STT) | 2 | ⬜ | Temporary, replaced Phase 4 |
| 4 | Android TextToSpeech (TTS) | 2 | ⬜ | Sanskrit → Hindi fallback |
| 5 | Runtime permissions | 2 | ⬜ | Camera, microphone, storage |
| 6 | WorkManager background sync | 2 | ⬜ | Periodic price/IoT refresh |
| 7 | Image downscale + compress | 2 | ⬜ | Before upload; max 1024px, JPEG 80% |

### On-Device AI (Phases 3-4)

| # | Feature | Engine | Phase | Status | Notes |
|---|---------|--------|-------|--------|-------|
| 1 | On-device crop disease | `OnDeviceCropDiseaseEngine` | 3 | 🔵 | ViT-Tiny → LiteRT |
| 2 | ViT export script | `ml/export_vit.py` | 3 | 🔵 | ai-edge-torch, ONNX fallback |
| 3 | Parity test (on-device vs server) | Unit test | 3 | 🔵 | Compare predictions on sample images |
| 4 | Router fallback (low confidence) | `EngineRouter` | 3 | 🔵 | If on-device confidence < threshold → remote |
| 5 | On-device STT (IndicWhisper) | `OnDeviceSpeechToText` | 4 | 🔵 | Interface + downloader only |
| 6 | On-device LLM chat (Gemma) | `OnDeviceChatEngine` | 4 | 🔵 | Interface + downloader only |
| 7 | Model download manager | `ModelDownloadManager` | 4 | 🔵 | Progress, resume, SHA256 check |
| 8 | Device capability detection | `DeviceTier` | 4 | 🔵 | RAM/SoC-based tier (LOW/MED/HIGH) |

### Auth & Settings

| # | Feature | Phase | Status | Notes |
|---|---------|-------|--------|-------|
| 1 | JWT login/register | 1 | ⬜ | Token stored in DataStore |
| 2 | Auto-login (saved token) | 1 | ⬜ | Check token validity on app start |
| 3 | Logout | 1 | ⬜ | Clear DataStore + Room user data |
| 4 | In-app language picker | 1 | ⬜ | 5 languages, `AppCompatDelegate.setApplicationLocales` |
| 5 | Base URL override (debug) | 1 | ⬜ | Settings screen + BuildConfig |
| 6 | Engine mode selection | 1 | ⬜ | Per-capability: auto/remote/ondevice |

### i18n

| # | Language | Code | Script | Locale File | strings.xml | Status |
|---|---------|------|--------|-------------|-------------|--------|
| 1 | English | `en` | Latin | `en.json` | `values/strings.xml` | ⬜ |
| 2 | Hindi | `hi` | Devanagari | `hi.json` | `values-hi/strings.xml` | ⬜ |
| 3 | Marathi | `mr` | Devanagari | `mr.json` | `values-mr/strings.xml` | ⬜ |
| 4 | Punjabi | `pa` | Gurmukhi | `pa.json` | `values-pa/strings.xml` | ⬜ |
| 5 | Sanskrit | `sa` | Devanagari | `sa.json` | `values-sa/strings.xml` | ⬜ |

### Hardening (Phase 5)

| # | Feature | Status |
|---|---------|--------|
| 1 | R8 / ProGuard shrinking | 🔵 |
| 2 | Release signing (local.properties) | 🔵 |
| 3 | README with setup instructions | 🔵 |
| 4 | Mermaid architecture diagram | 🔵 |
| 5 | GitHub Actions CI (build + lint + test) | 🔵 |
| 6 | Unit tests (JSON parsing, mappers, router) | 🔵 |
| 7 | UI tests (main flows) | 🔵 |

---

*This matrix will be updated after each phase is completed.*
