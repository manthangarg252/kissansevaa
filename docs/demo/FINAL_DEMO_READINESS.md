# KissanSevaAI — Final Demo Readiness Report (November 28, 2026)

## Overall Recommendation: **CONDITIONAL GO**

The local USB demonstration stack (`MongoDB :27017` + `Node.js Express :5050` + `FastAPI ML :8000` with **V1 DINOv2-S/14 active** and **V3 Hybrid in shadow mode**) is healthy, tested, and preserving all existing farmer records in `backend/data/db`.

### Current Readiness Status (`DEMO_READY: YES`)

1. **Samsung Galaxy A35 (`RZCXC17RFEW`) Connected & Updated APK Installed:**
   The updated [app-debug.apk](../../android/app/build/outputs/apk/debug/app-debug.apk) (containing all 7 bug fixes from this session, including the cold-restart login fix and Planner Task Edit UI) has been installed on `RZCXC17RFEW` (`Performing Streamed Install: Success`), `adb reverse tcp:5050 tcp:5050` and `adb reverse tcp:8000 tcp:8000` are active, and `./scripts/check-local-demo.sh` reports `DEMO_READY: YES`.
2. **Gemini Free-Tier Daily Quota (`429 RESOURCE_EXHAUSTED`):**
   As of `2026-11-28 08:11 IST`, the configured `GEMINI_API_KEY` is still rate-limited (`429 generate_content_free_tier_requests quota exceeded`). All deterministic features (DINOv2 V1 crop scan + uncertainty gating, Livestock emergency escalation + vet safety notice, Open-Meteo 7-day weather, live AGMARKNET mandi rates, Government Schemes, Crop Planner, Voice Auto-Fill, GPS location, Sarvam Helpline, and Offline Sync) continue working cleanly. Chatbot now also surfaces `groundedContextSummary` (live Mandi/Weather facts) beneath the honest quota notice.

---

## Verified Local Services & Regression Suites

| Component | Command / Endpoint | Latest Result |
| --- | --- | --- |
| FastAPI ML Service | `cd ml-service && venv/bin/pytest tests/ -v` | **16/16 passed** (`1.43s`, `ACTIVE_CROP_MODEL=v1`, `ENABLE_V3_SHADOW=true`) |
| Node.js Express Backend | `cd backend && node --test tests/*.test.js` | **14/14 passed** (`4.54s`), including new Planner Task Edit & Hindi Schemes tests |
| Android JVM Unit Tests | `./android/gradlew -p android testDebugUnitTest` | **84/84 passed** (`0 failed`, `0 skipped`) |
| Android Debug APK | `./android/gradlew -p android assembleDebug` | **Built cleanly** at `android/app/build/outputs/apk/debug/app-debug.apk` |
| Persistent MongoDB (`:27017`) | `./scripts/check-local-demo.sh` | **Healthy & intact** (`backend/data/db` preserved; no `MongoMemoryServer` used for demo) |
| Live AGMARKNET Mandi API | `GET http://127.0.0.1:5050/api/ai/status` | **Live (`live_agmarknet`)** |
| Live Open-Meteo Weather | `GET http://127.0.0.1:5050/health` + Weather UI | **Live (`open-meteo`)**, no key required |
| USB Device (`RZCXC17RFEW`) | `adb devices` + `check-local-demo.sh` | **Authorized over USB, updated APK installed, `adb reverse` active (`5050`, `8000`)** |

---

## Key Paths & Checkpoints

- **Git Checkpoint Tag:** `demo-checkpoint-20261127` (`84be6b0`) before demo fixes
- **Working Debug APK:** `android/app/build/outputs/apk/debug/app-debug.apk`
- **One-Click Scripts:** [scripts/start-local-demo.sh](../../scripts/start-local-demo.sh), [scripts/check-local-demo.sh](../../scripts/check-local-demo.sh), [scripts/stop-local-demo.sh](../../scripts/stop-local-demo.sh)
