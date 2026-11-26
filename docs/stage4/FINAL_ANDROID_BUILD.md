# KissanSevaAI Stage 4 — Final Android Build Report

## 1. Build Artifacts & Checksums

- **Artifact Path**: [app-debug.apk](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/build/outputs/apk/debug/app-debug.apk)
- **File Size**: `25M` (built November 27, 2026 at 02:23 IST via `./android/gradlew -p android assembleDebug --offline --console=plain`)
- **SHA-256**: `a1bfe7626fd071d27c4f0c3fda142bdd68c0df1d0cfc56d0dbce4adad8f65120`

## 2. Database & Manifest Delta Summary

- **Room Database Version**: Upgraded from `4` to `5` (`MIGRATION_4_5` defined in [DatabaseModule.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/core/database/DatabaseModule.kt)) adding `farm_profiles` (`FarmProfileEntity`), `crop_plans.sowingDate`, `crop_plans.customTask`, `crop_history.cropHint`, and `crop_history.symptomDescription`.
- **AndroidManifest Permissions**:
  - Retained: `INTERNET`, `ACCESS_NETWORK_STATE`, `CAMERA`, `RECORD_AUDIO`, `READ_MEDIA_IMAGES`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `READ_MEDIA_VISUAL_USER_SELECTED`.
  - Added for Stage 4: `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`.
  - Not requested: `ACCESS_BACKGROUND_LOCATION`.
- **Libraries**: Added `com.google.android.gms:play-services-location:21.3.0`.

## 3. Target Device Installation Status

| Target Device | Status | Notes |
|---|---|---|
| `emulator-5554` (`test_pixel` AVD, API 34 arm64) | **INSTALLED & RUNNING** | Installed via `adb install -r` after final edit; `adb reverse tcp:5050 tcp:5050` & `tcp:8000 tcp:8000` active; signed into `farmer@kissan.com`. |
| Samsung Galaxy A35 (`RZCXC17RFEW`) | **NOT CONNECTED** | Physically disconnected from ADB during Stage 4 build and verification; requires USB/ADB reconnection for handset install and outdoor field test. |
