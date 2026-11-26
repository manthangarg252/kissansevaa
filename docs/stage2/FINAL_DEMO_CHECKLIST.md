# KissanSevaAI — Stage 2 Final Demo Checklist

**Target Demo Date:** November 26, 2026  
**Demo Readiness Status:** Ready for end-to-end deterministic, ML V1, Weather, Mandi, Schemes, Offline Room Sync, and Veterinary Red-Flag demonstrations. *(Supply a billed `GEMINI_API_KEY` if live generative Gemini responses are required on stage.)*

---

## Pre-Demo Environment Startup

- [ ] **1. Start FastAPI ML Service (`:8000` — V1 Production + V3 Shadow):**
  ```bash
  cd /Users/manthangarg/Desktop/kissansevaai-main/ml-service
  V3_LOAD_FULL_FAISS=true venv/bin/python3 app.py
  ```
  Verify `curl -s http://127.0.0.1:8000/health` returns `"active_model":"v1"` and `"shadow_mode_enabled":true`.

- [ ] **2. Start Node.js + In-Memory MongoDB Dev Server (`:5050`):**
  ```bash
  cd /Users/manthangarg/Desktop/kissansevaai-main
  node backend/scripts/startDevServer.js
  ```
  Wait for `[Dev Server] Test user created: farmer@kissan.com / Password123!` and `[MarketDataService] Fetched ... live records from Data.gov.in`.
  *(Optional for live Gemini answers: ensure valid quota on `GEMINI_API_KEY` inside `backend/.env` before starting Node.)*

- [ ] **3. Connect Android Device or Emulator & Forward Ports:**
  - Verify device appears via `adb devices -l` (and unlock screen PIN if using physical Samsung Galaxy A35 `RZCXC17RFEW`).
  - Install latest APK if not already installed:
    ```bash
    adb install -r android/app/build/outputs/apk/debug/app-debug.apk
    ```
  - Forward USB/emulator localhost ports:
    ```bash
    adb reverse tcp:5050 tcp:5050
    adb reverse tcp:8000 tcp:8000
    ```

- [ ] **4. Log In on App Launch:**
  - Switch Login tab to **किसान ID / PIN**
  - Credentials pre-populated: `farmer@kissan.com` / `Password123!` -> tap **Sign In**

---

## Live Demo Flow (Safe & Repeatable)

1. **Dashboard & Personalized Weather:**
   - Point out `"Nagpur, Maharashtra • Clear Sky"` powered live by Open-Meteo (`Spraying Window: Suitable`).
   - Tap Weather banner to inspect 7-day spray/rain advisories.
2. **Crop Disease V1 Safety Demonstration:**
   - Open **Crop Disease Detection** -> pick a sample leaf or Gallery image -> tap **Start Detection**.
   - Highlight honest **DINOv2-S/14** retrieval metrics (**Cosine Similarity** vs **k-NN Neighbor Agreement**), top-5 matches, `Needs Review` gate, and **withholding both organic & chemical treatments** when uncertain.
   - Show explicit **Gemini Second Opinion** consent checkbox gate (never silent overwrite).
3. **Livestock Red-Flag Clinical Triage:**
   - Open **Livestock Care** -> select `Fever = Yes` + `Visible lesions = ON` -> tap **Run Diagnostics**.
   - Show immediate **`URGENT VETERINARY ESCALATION RECOMMENDED`** alert and `1962` emergency helpline working even if cloud Gemini quota is exhausted.
4. **Live Agmarknet Mandi Prices & Arbitrage Compare:**
   - Open **Live Mandi Prices** -> highlight `Source: live agmarknet • 71 listings`.
   - Filter by **Veg / Fruit**, toggle Favorite star, tap **Compare Mandis** to show market price spreads.
5. **Verified Government Schemes:**
   - Open **Government Schemes** -> bookmark `PM-KISAN` -> switch to **Eligibility Check** showing auto-filled farm profile (`Maharashtra`, `4` acres).
6. **Offline-First Room & Sync Deduplication:**
   - Open **Crop Planner** -> toggle airplane/offline mode (`adb reverse --remove-all` + `svc wifi disable`), create a new plan and check off a task -> show `"pending sync"` queue surviving app restart -> reconnect & sync cleanly without duplicates.
