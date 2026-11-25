# KissanSevaAI — Stage 1 Demo & Run Instructions

**Target Demo Date:** November 26, 2026

---

## 1. Start the Three Backend & ML Tiers

1. **FastAPI DINOv2 ML Service (Port `8000`, V1 Active + V3 Shadow):**
   ```bash
   cd ml-service
   V3_LOAD_FULL_FAISS=true venv/bin/python3 app.py
   ```
   Verify health:
   ```bash
   curl -s http://localhost:8000/health
   # Expected: active_model="v1", shadow_mode_enabled=true, classes=67, reference_images=2144
   ```

2. **Node.js Express Backend + In-Memory Seeded MongoDB (Port `5050`):**
   ```bash
   node backend/scripts/startDevServer.js
   ```
   - Demo account pre-seeded automatically:
     - **Email:** `farmer@kissan.com`
     - **Password:** `Password123!`
     - Pre-seeded farm profile (`Nagpur, Maharashtra`, Black Soil, `4.0` acres, active `Wheat` 7-day crop plan, traders, IoT reading, sample crop history).
   - Verify health:
     ```bash
     curl -s http://localhost:5050/api/ai/status
     curl -s "http://localhost:5050/api/weather/forecast?location=Nagpur"
     ```

---

## 2. Build & Run the Android App

- **Build Debug APK:**
  ```bash
  JAVA_HOME=/Users/manthangarg/.jdks/jdk-21.0.12.1+1/Contents/Home \
    ./android/gradlew -p android assembleDebug
  ```
- **Emulator / Physical Device USB Forwarding (if using physical phone via USB):**
  ```bash
  /Users/manthangarg/Library/Android/sdk/platform-tools/adb reverse tcp:5050 tcp:5050
  /Users/manthangarg/Library/Android/sdk/platform-tools/adb reverse tcp:8000 tcp:8000
  /Users/manthangarg/Library/Android/sdk/platform-tools/adb install -r android/app/build/outputs/apk/debug/app-debug.apk
  ```
- Note: Physical device Samsung Galaxy A35 (`RZCXC17RFEW`) requires user PIN unlock before interactive ADB testing. Android emulator AVD `test_pixel` can be booted if physical unlock is unavailable.

---

## 3. Recommended Demo Walkthrough

1. **Login / Multilingual UI:** Switch language on Login/Dashboard (`en`, `hi`, `bho`, `mr`, `pa`, `sa`), then sign in with `farmer@kissan.com` / `Password123!`.
2. **Weather Intelligence (`Weather Alerts` card/banner):** Opens Open-Meteo live weather for Nagpur (or speak district via Mic button), displays spray suitability banner with TTS audio playback, deterministic weather rules alerts, and 7-day forecast.
3. **Smart Crop Planner (`Crop Planner` toolkit/card):** Shows active Wheat 7-day schedule with checkboxes, weather-adjusted badges, Voice Form Fill plan creator, and WorkManager reminders + offline sync banner when disconnected.
4. **Crop Disease Scan + Explicit Second Opinion:** Captures/selects leaf image -> runs DINOv2 V1 on `:8000`. Shows separated `top_cosine_similarity` & `neighbor_agreement`, withholds both organic & chemical treatments on `needs_review=true`, and lets farmer explicitly consent to request Gemini Vision second opinion & view reconciliation comparison.
5. **Livestock Clinical Assistant:** Structured species + symptom intake (fever, lesions, appetite, milk drop) + immediate red-flag urgent vet escalation (`1962` hotline) + drug prescription safety notice + history persistence.
6. **Live Mandi Prices & Verified Schemes:** Shows Agmarknet source status badge, star favorites, **Compare Mandis** arbitrage view, verified 10-scheme catalog (`lastVerifiedAt`), **Eligibility Check**, and bookmarking.
7. **Tool-Grounded Multi-Turn Chatbot:** Switch category chip (`Mandi Rates`, `Weather`, `Schemes`, `Crop Planner`), ask questions by voice or text, inspect `Grounded: ...` source badges, and play replies aloud via TTS.
