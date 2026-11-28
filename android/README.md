# KissanSevaAI — Native Android App

Modern native Android application built with **Kotlin 2.1**, **Jetpack Compose (Material 3)**, **Hilt**, **Room v5**, **WorkManager**, and **CameraX**.

---

## Key Architecture & Stack

- **UI & Theme:** Jetpack Compose (`BOM 2025.02.00`), Material 3 minimalist sans-serif typography (`KissanTypography`), Type-safe Navigation Compose (`Screen` sealed interface).
- **DI & Async:** Hilt (`2.51.1`), Kotlin Coroutines & Flow (`1.10.1`).
- **Offline Storage & Sync:**
  - **Room DB (`2.6.1`, schema v5)** across **15 local entities**: `SchemeEntity`, `LoanProductEntity`, `TraderEntity`, `MarketPriceEntity`, `CropAnalysisEntity`, `ChatMessageEntity`, `IoTReadingEntity`, `LivestockAnalysisEntity`, `WeatherCacheEntity`, `CropPlanEntity`, `PlannerTaskEntity`, `SavedSchemeEntity`, `MarketFavoriteEntity`, `SyncOperationEntity`, `FarmProfileEntity`.
  - **Account-Scoped Cold Restart Persistence:** Nullable `isLoggedInState: StateFlow<Boolean?>` waits for DataStore JWT verification before routing. `MIGRATION_4_5` prevents cross-account leakage on sign-out/switch.
  - **WorkManager (`2.10.0`):** `DataSyncWorker` flushes offline mutations to `POST /api/sync/batch`.
- **Voice & Location:**
  - Offline-first multilingual `SpeechRecognizer` + `AgriculturalVoiceParser` supporting Hindi, English, Punjabi, Marathi, and Bhojpuri (`HybridFieldExtractor`).
  - `TextToSpeech` readout on Dashboard Weather card, Crop Disease result card, and Chatbot responses.
  - Multi-layer GPS detector (`FusedLocationProviderClient` High/Balanced -> OS `LocationManager` -> cached fallback + `Geocoder`) with explicit farm confirmation modal and manual fallback.
- **Networking & Media:** Retrofit (`2.11.0`), OkHttp (`4.12.0`), Kotlinx Serialization (`1.8.0`), Coil (`3.1.0`), CameraX (`1.6.2`).

---

## Implemented Screens (16 Modules)

`Login` • `Signup` • `Dashboard` • `CropDisease` • `Livestock` • `Weather` • `CropPlanner` • `MarketInsights` • `IoTMonitoring` • `Schemes` • `Loans` • `Traders` • `Carbon` • `LivePrices` • `Chatbot` • `Settings`

---

## Configuration (`gradle.properties` / BuildConfig)

| Property / Env Var | Purpose | Default / Constraint |
| --- | --- | --- |
| `SARVAM_HELPLINE_NUMBER` | E.164 helpline number displayed on Dashboard (`ACTION_DIAL`) | Blank if unconfigured |
| `KISSAN_PROD_API_URL` | Release HTTPS API endpoint (`release` build enforces `https://`, rejects localhost/private IPs) | `https://api.kissansevaai.com` |
| Debug `BASE_URL` | Local backend target over ADB reverse or emulator | `http://10.0.2.2:5050` |

---

## Build, Test & USB Device Install

```bash
# Run JVM unit tests (84 tests) & build debug APK
./gradlew testDebugUnitTest assembleDebug

# Install onto USB-connected phone (example device serial)
adb -s <DEVICE_SERIAL> install -r app/build/outputs/apk/debug/app-debug.apk

# Bridge localhost ports for physical device testing
adb -s <DEVICE_SERIAL> reverse tcp:5050 tcp:5050
adb -s <DEVICE_SERIAL> reverse tcp:8000 tcp:8000
```
