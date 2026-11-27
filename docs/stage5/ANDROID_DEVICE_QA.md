# KissanSevaAI Stage 5 — Android Device QA Report

## 1. Build & Device Attachment Summary

- **Final Debug APK**: [app-debug.apk](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/build/outputs/apk/debug/app-debug.apk)
- **File Size**: `26M` (built November 27, 2026 at 22:42 IST)
- **SHA-256**: `973c010fd63a0fb99856cc05ea678fae7e4759639dd39a9ea29eae5b4b78c6c2`

| Device Target | Connectivity & Reverse Tunneling | APK Installed | Automated Instrumentation | Interactive UI Status |
|---|---|---|---|---|
| Physical Samsung Galaxy A35 (`RZCXC17RFEW`, `SM-A356E`, Android 17) | USB connected (`adb reverse tcp:5050 tcp:5050`, `tcp:8000 tcp:8000`) | **Yes (`Success`)** | **2/2 passed** (`MigrationAndScreenTest`, `PlatformVoiceDiagnosticTest`); 5 Compose UI tests failed on Android 17 framework `NoSuchMethodException: android.hardware.input.InputManager.getInstance []` | **BLOCKED**: Screen locked (`mDreamingLockscreen=true`, `NotificationShade`); interactive UI/mic/GPS walkthrough requires manual unlock |
| Headless Emulator (`emulator-5554`, `test_pixel(AVD) - 14`, Android 14) | Running (`adb reverse tcp:5050 tcp:5050`, `tcp:8000 tcp:8000`) | **Yes (`Success`)** | **7/7 passed** (`0` failures, `0` errors, `0` skipped) after final edit | **VERIFIED ON EMULATOR**: Logged into `farmer@kissan.com`; Helpline Card, `Copy Number`, `ACTION_DIAL` Dialer prefill (`+91 79 6586 1355`), Stage 4 Location & Voice controls verified with `0` `AndroidRuntime:E` crashes |

---

## 2. Classification Per Feature Check

- **APK Installation & `adb reverse` (`5050`, `8000`) on `RZCXC17RFEW` & `emulator-5554`**: **VERIFIED ON PHYSICAL ANDROID** & **VERIFIED ON EMULATOR**
- **Room v3→v4 Migration & `SpeechRecognizer.isRecognitionAvailable(context): true` on Galaxy A35**: **VERIFIED ON PHYSICAL ANDROID** (`OK (2 tests)` in `1.081s`)
- **Room v4→v5 Multi-Account Isolation, `VoiceInputField`, `FarmLocationConfirmationDialog`, `AiHelplineDashboardCard`**: **VERIFIED ON EMULATOR** (`7/7` Compose + Room instrumentation tests)
- **Dashboard Sarvam Helpline Card (`+917965861355`), `Call Now` (`Intent.ACTION_DIAL`), & `Copy Number` (`ClipboardManager`)**: **VERIFIED ON EMULATOR** (see [01c_helpline_polished.png](file:///Users/manthangarg/.gemini/antigravity/brain/e4e34924-fb31-42d1-b5c5-9167a5462c8b/screenshots/stage5/01c_helpline_polished.png), [02_helpline_copied.png](file:///Users/manthangarg/.gemini/antigravity/brain/e4e34924-fb31-42d1-b5c5-9167a5462c8b/screenshots/stage5/02_helpline_copied.png), [03_dialer_prefilled.png](file:///Users/manthangarg/.gemini/antigravity/brain/e4e34924-fb31-42d1-b5c5-9167a5462c8b/screenshots/stage5/03_dialer_prefilled.png))
- **Stage 4 Login, Weather, Live Mandi, Schemes, Crop Planner, Crop Disease, Livestock Intake, Offline Queue Banner**: **VERIFIED ON EMULATOR**
- **Interactive On-Device Walkthrough on Samsung Galaxy A35 (Real Camera Crop/Livestock Upload, Acoustic Mic Speech, Real Outdoor GPS, Speaker TTS, Real Carrier Dial Test)**: **BLOCKED** / **IMPLEMENTED BUT UNVERIFIED** (device lockscreen active during session)
