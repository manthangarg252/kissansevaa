# KissanSevaAI — Stage 2 Real Device & Emulator Test Results

**Verification Date:** November 26, 2026  
**APK Under Test:** `android/app/build/outputs/apk/debug/app-debug.apk` (`25,695,489` bytes, built after final edits)

---

## 1. Device Inventory & Connectivity Matrix

| Target | Serial / AVD | OS / ABI | Stage 2 Status | Notes |
|---|---|---|---|---|
| **Android Emulator** | `emulator-5554` (`test_pixel`, `sdk_gphone64_arm64`) | Android 14 (API 34, `arm64-v8a`) | **VERIFIED ON EMULATOR** | Installed clean `app-debug.apk`; `adb reverse tcp:5050 tcp:5050` and `tcp:8000 tcp:8000` active; `2/2` instrumentation tests passed + full interactive 14-screen walkthrough + genuine offline restart & sync completed. |
| **Samsung Galaxy A35** | `RZCXC17RFEW` (`SM-A356E`) | Physical Android | **BLOCKED (LOCKED / DISCONNECTED)** | Stage 1 APK installed earlier while locked (`mScreenState=OFF_LOCKED`, `mDreamingLockscreen=true`). During Stage 2 final verification (`adb devices -l`), device was disconnected from USB (`not found`). Security lock was never bypassed. |

---

## 2. Instrumented Tests on `test_pixel(AVD) - 14` (`emulator-5554`)

Command executed after final code edits:
```bash
ANDROID_SERIAL=emulator-5554 \
JAVA_HOME=/Users/manthangarg/.jdks/jdk-21.0.12.1+1/Contents/Home \
  ./android/gradlew -p android testDebugUnitTest assembleDebug connectedDebugAndroidTest --offline --console=plain
```

Observed results in `android/app/build/outputs/androidTest-results/connected/debug/TEST-test_pixel(AVD) - 14-_app-.xml`:
- **`2 tests, 2 passed, 0 failures, 0 errors, 0 skipped`**
  1. `MigrationAndScreenTest#realRoomMigration3To4PreservesExistingCropDataAndOpensCleanly`: Verified SQLite schema v3 -> v4 upgrade via `MIGRATION_3_4`, legacy crop scan row survival, and `2`-user isolation queries (`CropPlanDao`, `MarketFavoriteDao`, `SavedSchemeDao`, `SyncQueueDao`).
  2. `PlatformVoiceDiagnosticTest#reportRealPlatformVoiceSupport`:
     - `SpeechRecognizer.isRecognitionAvailable(context): true`
     - `TextToSpeech` bound to `com.google.android.tts` (`version googletts.google-speech-apk_20230123.02_p3.506463867`), `onInit status: 0`
     - `en-IN`: `LANG_COUNTRY_AVAILABLE (1)` (`true`)
     - `hi-IN`: `LANG_COUNTRY_AVAILABLE (1)` (`true`)
     - `mr-IN`: `LANG_COUNTRY_AVAILABLE (1)` (`true`)
     - `pa-IN`: `LANG_COUNTRY_AVAILABLE (1)` (`true`)
     - `sa-IN` (Sanskrit): `LANG_NOT_SUPPORTED (-2)` (`false`)

*Post-test hygiene verified:* Reinstalled `app-debug.apk` cleanly (`adb -s emulator-5554 install -r ...`) *after* `connectedDebugAndroidTest` because instrumentation teardown clears app session state.

---

## 3. End-to-End Interactive Walkthrough (`emulator-5554`)

All screenshots archived under `/Users/manthangarg/.gemini/antigravity/brain/e4e34924-fb31-42d1-b5c5-9167a5462c8b/screenshots/stage2/`:

1. **Login & Dashboard Personalization (`01_login.png`, `02_dashboard.png`, `02b_dashboard_toolkit.png`):**
   - Signed in via ID/PIN tab (`farmer@kissan.com`).
   - Verified `"18°C • Nagpur, Maharashtra"` with fixed non-clipped `"Clear Sky"` pill and fixed crisp `"View All →"` in Crop Planner / Mandi snapshot cards.
2. **Weather & Spray Window (`03_weather.png`):**
   - Verified live Open-Meteo Nagpur card (`18°C • Clear Sky • LIVE DATA • Humidity: 61% • Wind: 5.6 km/h • Rain now: 0.0 mm • Spraying Window: Suitable`).
3. **Crop Disease V1 Scan & Second Opinion Gate (`04_crop_initial.png`, `04_crop_v1.png`, `04b_crop_v1_consent.png`, `04c_crop_second_opinion.png`):**
   - Selected real leaf photo from Android Photo Picker (`com.google.android.providers.media.module`) -> clicked `Start Detection` -> received real V1 response: `Wheat Aphid`, `Cosine Similarity 0.244`, `k-NN Neighbor Agreement 20.0%`, disclaimer note, top-5 matches, `Needs Review`, and **red `Treatment Prescriptions Withheld`** safety card.
   - Confirmed `"Get Gemini Second Opinion"` remains disabled until consent checkbox is checked.
4. **Livestock Clinical Triage & Emergency Red Flag (`05_livestock_form.png`, `05_livestock.png`):**
   - Selected `Appetite Reduced`, `Milk Yield Stopped`, `Fever = Yes`, `Visible lesions = ON`, tapped `Run Diagnostics`.
   - Verified `URGENT VETERINARY ESCALATION RECOMMENDED` red alert banner (`Fever combined with blisters/lesions/ulcers...`), honest `429 quota_exhausted` notice handled gracefully, and `1962` helpline action.
5. **Live Mandi Prices & Arbitrage Compare (`06_mandi.png`, `06b_mandi_compare.png`):**
   - Confirmed `Source: live agmarknet • 43+ listings` (expanded to `71` after refresh), toggled `Veg` chip, saved favorite (`FAVORITES: 1`), and verified `Compare Mandis` spread cards (`Spread ₹2670/q`, `Spread ₹2100/q`) render without wrapping after `LivePricesScreen.kt` fix.
6. **Government Schemes & Eligibility (`07_schemes.png`, `07b_schemes_eligibility.png`):**
   - Browsed `10` schemes, bookmarked `PM-KISAN Samman Nidhi` (`SAVED_SCHEMES: 1`), switched to `Eligibility Check` and verified pre-filled profile context (`Maharashtra`, `4` acres, `Cotton & Wheat`, `Black Cotton`).
7. **Smart Crop Planner & Genuine Offline -> Cold-Restart -> Online Sync Test (`08_planner.png`, `09_offline_pending.png`, `10_offline_synced.png`):**
   - **Offline Isolation:** Executed `adb -s emulator-5554 reverse --remove-all` AND `svc wifi disable; svc data disable` so loopback & cellular backends were genuinely unreachable.
   - Created `"Cottonan"` crop plan offline and checked Day 0 task `"Cottonan: Moisture & Pest Check (Offline Plan)"` offline (`09_offline_pending.png` showed `"2 offline change(s) queued — tap to sync now"` + checked strikethrough task).
   - **Cold Restart Survival:** Executed `adb -s emulator-5554 shell am force-stop com.kissansevaai` while still offline, cold-launched app, reopened Crop Planner, and confirmed `"2 offline change(s) queued — tap to sync now"` persisted in SQLite Room.
   - **Online Sync & Dedup Check:** Restored `adb reverse` (`5050`/`8000`) + `svc wifi enable; svc data enable`, tapped sync banner -> banner cleared (`10_offline_synced.png`). Backend query (`GET /api/planner/plans`) verified **exactly `3` plans (`['Cottonan', 'Soybean', 'Wheat']`) with zero duplicates**, **`6` tasks generated for Cottonan**, and **`dayOffset=0` task persisted as `status='completed'`**.
8. **AI Chatbot, Settings, IoT Monitoring & Market Insights (`11_chatbot.png`, `12_settings.png`, `13_iot_monitoring.png`, `14_market_insights.png`):**
   - Verified navigation, profile fields (`Maharashtra / Nagpur / Black / 4.0 / Drip / Canal`), language switcher, `27.8°C / 64.0% / 45.0% / 6.7 pH` live IoT telemetry cards, and Market Insights screen.
