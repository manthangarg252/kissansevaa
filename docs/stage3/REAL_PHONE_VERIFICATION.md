# KissanSevaAI — Stage 3 Real Phone & Emulator Verification

**Verification Date:** November 26, 2026  
**Physical Device Tested:** Samsung Galaxy A35 (`SM-A356E`), serial `RZCXC17RFEW`, Android 17 (`1080x2340`), unlocked (`showing=false`, `SCREEN_STATE_ON`)  
**Emulator Comparison Target:** Android 14 Emulator `test_pixel` (`emulator-5554`, `sdk_gphone64_arm64`, `arm64-v8a`)  
**APK Installed & Verified:** [`android/app/build/outputs/apk/debug/app-debug.apk`](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/build/outputs/apk/debug/app-debug.apk) (`25,695,489` bytes)

---

## 1. Physical-Device Bug Diagnosed and Fixed (`NetworkModule.kt`)

During Stage 3 bring-up on `RZCXC17RFEW`, DataStore retained a stale LAN URL (`http://192.168.1.7:5050`) from an earlier network session. Because `hostFallbackInterceptor` had been registered *before* `authInterceptor` in OkHttp, `authInterceptor` rewrote the request host back to `192.168.1.7` on every retry after `hostFallbackInterceptor` switched to `127.0.0.1` over `adb reverse`.

- **Fix Applied:** [`NetworkModule.kt`](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/core/network/NetworkModule.kt#L40-L108)
  - Moved `.addInterceptor(authInterceptor)` **before** `.addInterceptor(hostFallbackInterceptor)` so `hostFallbackInterceptor`'s target host persists across retries.
  - Added `@Volatile private var lastWorkingFallbackHost: String? = null` with `2500ms` connect timeout on fallback candidates (`["127.0.0.1", "10.0.2.2", "192.168.1.7"]`) so subsequent requests self-heal immediately over USB `adb reverse`.

---

## 2. Device Matrix & Post-Final-Edit Test Results

| Target | `testDebugUnitTest` | `connectedDebugAndroidTest` | Interactive End-to-End QA |
|---|---|---|---|
| **Samsung Galaxy A35 (`RZCXC17RFEW`, `SM-A356E - 17`)** | `65/65` JVM unit tests (host) | **`2/2 passed, 0 failed, 0 skipped`** (`TEST-SM-A356E - 17-_app-.xml`) | **VERIFIED LIVE** across all core modules, real Gallery photo, CameraX launch, offline kill/sync, Hindi UI, Chatbot 429, Voice UI |
| **Emulator (`emulator-5554`, `test_pixel(AVD) - 14`)** | `65/65` JVM unit tests (host) | **`2/2 passed, 0 failed, 0 skipped`** (`TEST-test_pixel(AVD) - 14-_app-.xml`) | Reinstalled post-test, port-forwarded (`5050`/`8000`), and signed into `farmer@kissan.com` cleanly (`Stage 2` full 14-screen walkthrough previously completed) |

**Post-Test Hygiene:** Because `connectedDebugAndroidTest` clears app session state, `app-debug.apk` was reinstalled cleanly (`adb install -r -d`), `adb reverse tcp:5050 tcp:5050` & `tcp:8000 tcp:8000` were restored on both targets, and `farmer@kissan.com` was re-authenticated on `RZCXC17RFEW` (`a35_demo_ready.png` showing `Hello, Farmer Friend!`).

---

## 3. Interactive Real-Phone QA (`RZCXC17RFEW`)

Screenshots captured under `/Users/manthangarg/.gemini/antigravity/brain/e4e34924-fb31-42d1-b5c5-9167a5462c8b/screenshots/stage3/`:

1. **Dashboard, Live Weather & IoT Telemetry (`a35_02_dashboard.png`, `a35_demo_ready.png`):**
   - Verified `Hello, Farmer Friend!`, Open-Meteo `27–28°C • Nagpur, Maharashtra • Clear Sky`, `Spraying Advisory • AI Alert`, and IoT telemetry (`45.0% Optimal`, `27.8°C`, `64.0%`, `6.7 pH`).
2. **Crop Disease Detection — Sample Leaf, Real Gallery Photo & Live CameraX (`a35_03_crop_v1.png`, `a35_04_crop_second_opinion.png`, `a35_13_real_photo_crop.png`, `a35_13b_camera_intent.png`):**
   - **Gallery Photo Selection:** Selected pushed wheat rust leaf photo (`wheat_rust_stage3.jpg`, Android Photo Picker timestamp `26 Nov 2026 03:13`) and ran V1 DINOv2 scan -> backend persisted record `6b07d472fcc5e96bb6b714a9` (`Wheat` / `Wheat Black Rust`, `source: "dinov2"`, `top_cosine_similarity: 0.81628`, `neighbor_agreement: 0.4`, `needs_review: true`). Verified corresponding V3 shadow entry in `ml-service/logs/shadow_comparison.jsonl` (line 18: V3 `svm_linear_cal_C2.0` disagreed and flagged `svm_faiss_disagreement_abstain` without affecting V1 production).
   - **Consent Gate & Second Opinion:** Checked consent checkbox and tapped `Get Gemini Second Opinion` -> surfaced honest `429 quota_exhausted` error card while keeping DINOv2 history intact.
   - **CameraX Launcher:** Tapped `Camera` -> granted runtime camera permission -> live CameraX viewfinder rendered (`Close Camera`, `Switch Camera`, capture shutter, Android green camera indicator pill) without crashes.
3. **Livestock Red-Flag Clinical Triage (`a35_05_livestock.png`):**
   - Verified `URGENT VETERINARY ESCALATION RECOMMENDED`, honest `AI Veterinary Vision Unavailable (quota_exhausted)` banner, and `Veterinary Safety Notice ... 1962`.
4. **Weather Alerts & Live/Cached AGMARKNET Mandi (`a35_06_weather.png`, `a35_07_mandi.png`):**
   - Weather screen showed `Nagpur, Maharashtra • Clear Sky • OPEN-METEO • Humidity: 30% • Spraying Window: Suitable`.
   - Mandi screen showed `33`–`43` listings with honest rate-limit indicator (`Source: seeded reference rate limited` during transient 429 / `cached_agmarknet_rate_limited` after `2026-11-26T06:36:28.008Z` live fetch) and working `Saved`/`Compare` controls.
5. **Smart Crop Planner & Offline Room Queue -> Kill -> Sync (`a35_08_planner.png`, `a35_09_offline_pending.png`, `a35_10_offline_synced.png`):**
   - *Network Safety Rule Observed:* Never disabled phone cellular/Wi-Fi radios (phone shares host cellular hotspot); simulated offline connectivity strictly via `adb -s RZCXC17RFEW reverse --remove-all`.
   - Created `"Millet"` plan offline -> UI displayed `"1 offline change(s) queued — tap to sync now"` and `"Saved offline in Room; full weather plan will sync when online."`.
   - Killed process (`am force-stop com.kissansevaai`) while still disconnected -> relaunched app and navigated back to Crop Planner -> verified `"1 offline change(s) queued — tap to sync now"` and `"Millet"` survived in SQLite Room.
   - Restored `adb reverse tcp:5050 tcp:5050` & `tcp:8000 tcp:8000` -> tapped sync banner -> synced cleanly (`before=3 -> after=4` backend plans: `['Millet', 'Gram', 'Barley', 'Wheat']`), clearing pending banner.
6. **AI Chatbot `429` Quota Card & Hindi Localization (`a35_11_chatbot.png`, `a35_12_settings_hindi.png`):**
   - Submitted `"Wheat leaf care"` in Chatbot -> displayed honest yellow card `"AI daily API quota is exhausted on the configured key. Deterministic tools continue working; update GEMINI_API_KEY or wait for quota reset."`.
   - Switched Dashboard language pill `English -> हिन्दी` -> rendered localized Hindi UI (`लाइव कृषि नेटवर्क`, `नमस्ते, किसान भाई!`, `छिड़काव सलाह`, `बोलें`, `आईओटी फार्म सेंसर`), then restored English for demo readiness.

---

## 4. Voice (STT & TTS) Comparison: Galaxy A35 vs Emulator

### Automated Instrumentation (`PlatformVoiceDiagnosticTest#reportRealPlatformVoiceSupport`)

| Capability / Locale | Galaxy A35 (`SM-A356E - 17`, `12:19:11`) | Emulator (`test_pixel(AVD) - 14`, `12:18:52`) |
|---|---|---|
| `SpeechRecognizer.isRecognitionAvailable(context)` | `true` (`SemSpeechRecognizer` present on device) | `true` |
| STT `'en'`, `'hi'`, `'mr'`, `'pa'` | `true` (`reason: null`) | `true` (`reason: null`) |
| STT `'sa'` (Sanskrit) | `false` (`Voice recognition is not supported in Sanskrit on this device...`) | `false` (same explicit fallback reason) |
| `TextToSpeech.onInit` status | `0` (`SUCCESS`) | `0` (`SUCCESS`) |
| TTS `'en-IN'`, `'hi-IN'`, `'mr-IN'`, `'pa-IN'` | `LANG_COUNTRY_AVAILABLE (1)` (`true`) | `LANG_COUNTRY_AVAILABLE (1)` (`true`) |
| TTS `'sa-IN'` (Sanskrit) | **`LANG_COUNTRY_AVAILABLE (1)` (`true`)** | `LANG_NOT_SUPPORTED (-2)` (`false`, auto-falls back to Hindi TTS) |

### Interactive UI Triggers & Honest Boundary (`RZCXC17RFEW`)
- **`VERIFIED LIVE` (UI/Intent Triggers):** Tapping `"Fill with Voice"` on Crop Disease soil/location form switched UI state cleanly to `"Listening for form details..."` / `"Listening..."` (`a35_14b_voice_soil_form.png`); tapping `"Voice Assistant"` mic navigated into `AI Assistant` with `Start voice input` + `Read aloud` buttons (`a35_14_voice_ui.png`); tapping Dashboard `"Listen"` executed without exceptions.
- **`IMPLEMENTED BUT UNVERIFIED` (Acoustic Hardware Loop):** Because testing was driven autonomously over ADB without human speech into the handset microphone or listening to handset speakers, physical acoustic speech transcription accuracy and speaker audio clarity require a brief human test (noted in [`MANUAL_ACTIONS.md`](file:///Users/manthangarg/Desktop/kissansevaai-main/docs/stage3/MANUAL_ACTIONS.md)).
