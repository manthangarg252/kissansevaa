# KissanSevaAI — Stage 2 Unresolved External Blockers & Caveats

**Last Updated:** November 26, 2026

Every issue within local Android, Node.js, FastAPI, SQLite Room, Open-Meteo, and Data.gov.in integration was resolved and verified in Stage 2. The items below remain external dependencies or unverified hardware/provider integrations recorded honestly without simulation or workarounds:

---

## 1. Google Gemini Live API Quota Exhaustion (`BLOCKED`)
- **Impact:** Live generative cloud AI outputs for multi-turn Chatbot answers, Second Opinion Gemini Vision check, Livestock Vision enrichment, Scheme AI explainer, Crop Planner AI refinement, Loans/Carbon/Market Insights/IoT Insights AI summaries, and cloud Voice Field Extraction return HTTP `429 RESOURCE_EXHAUSTED` (`quota_exhausted`).
- **Observed Probe:** `gemini-3-flash-preview` Free Tier `20` RPD limit exhausted (`errorCountsByCode: {"quota_exhausted": 3}` in `/api/ai/status`); `gemini-3.5-flash` & `gemini-3.1-flash-lite-preview` timed out at `8s`; `gemma-4-26b-a4b-it` / `gemma-4-31b-it` hit transient `503 UNAVAILABLE` under JSON schema mode.
- **Mitigation in App:** Deterministic engines (V1 DINOv2+FAISS, Livestock red-flag clinical rules, Open-Meteo Spray Window, 7-day rule-based Crop Planner, 10 curated Schemes, live Agmarknet Mandi rates) operate cleanly and surface explicit honest unavailable banners without hallucinated text or faked confidence.
- **Resolution Needed:** Upgrade billing/quota on `GEMINI_API_KEY` (or rotate to a funded key in `backend/.env`).

## 2. Physical Samsung Galaxy A35 (`RZCXC17RFEW`) Locked / Disconnected (`BLOCKED`)
- **Impact:** While the Stage 1 APK installed earlier on the physical phone (`SM-A356E`) while screen-locked (`mScreenState=OFF_LOCKED`), the device was disconnected (`adb devices -l` did not list `RZCXC17RFEW`) during final Stage 2 APK build & interactive verification. Therefore, Stage 2 final APK testing ran on Android 14 emulator `test_pixel` (`emulator-5554`), not interactively on the unlocked physical handset.
- **Resolution Needed:** Reconnect `SM-A356E` via USB, unlock the screen lock manually, run `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`, and re-run `adb reverse tcp:5050 tcp:5050; adb reverse tcp:8000 tcp:8000`.

## 3. External Telephony / Call-Agent Partner (`IMPLEMENTED BUT NOT LIVE-VERIFIED`)
- **Impact:** `POST /api/integrations/call-agent/webhook` returns `503` until `CALL_AGENT_WEBHOOK_SECRET` and an external SIP/IVR telephony provider (e.g., Twilio, Exotel, Knowlarity) are provisioned (`GET /api/integrations/call-agent/contract` is ready).

## 4. Data.gov.in Agmarknet Burst Rate Limits (`OBSERVED / MITIGATED`)
- **Impact:** Live Agmarknet refresh succeeded in Stage 2 (`71` listings, `sourceStatus: "live_agmarknet"` at `2026-11-26T05:53:35.937Z`), while repeated rapid refresh probes hit upstream HTTP `429`. Automatic 5-minute cooldown prevents quota lockout using cached rates.

## 5. Acoustic Microphone Capture & Persistent MongoDB Daemon (`IMPLEMENTED BUT NOT LIVE-VERIFIED`)
- **Acoustic Voice:** `PlatformVoiceDiagnosticTest` verified `SpeechRecognizer.isRecognitionAvailable = true` and `com.google.android.tts` (`en-IN`, `hi-IN`, `mr-IN`, `pa-IN` supported; `sa-IN` unsupported) on `emulator-5554`, while physical microphone spoken-word transcription and speaker playback were not acoustically tested on the headless emulator.
- **Production DB:** Local demo runs against ephemeral `MongoMemoryServer` (`backend/scripts/startDevServer.js`); multi-restart persistence across Node restarts requires configuring external `mongod` / Atlas `MONGO_URI` in `backend/.env` and running `node backend/server.js`.
