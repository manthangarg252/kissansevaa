# KissanSevaAI — Stage 3 Final Live Demo Script

**Demo Readiness Target:** November 26, 2026 (`VERIFIED LIVE` on `SM-A356E` / `RZCXC17RFEW` + Local Services)  
**Pre-Set State:** Physical phone (`RZCXC17RFEW`) and `emulator-5554` already have `app-debug.apk` (`25,695,489` bytes) installed, `adb reverse` active, and `farmer@kissan.com` signed into the English Dashboard (`Nagpur, Maharashtra`).

---

## 1. Host Startup Sequence (If Rebooted Before Demo)

Run each service from `/Users/manthangarg/Desktop/kissansevaai-main`:

1. **Start FastAPI ML Service (`:8000` — V1 Production Active + V3 Shadow Mode):**
   ```bash
   cd /Users/manthangarg/Desktop/kissansevaai-main/ml-service && V3_LOAD_FULL_FAISS=true venv/bin/python3 app.py
   ```
   Verify: `curl -s http://127.0.0.1:8000/health` returns `"active_model":"v1"` and `"shadow_mode_enabled":true`.

2. **Start Persistent MongoDB (`:27017`) + Node.js Backend (`:5050`):**
   ```bash
   cd /Users/manthangarg/Desktop/kissansevaai-main && npm --prefix backend run dev:persistent
   ```
   Confirm log shows `Persistent MongoDB ready` (`backend/data/db`) and backend running on `:5050` without resetting farmer data.

3. **Connect Physical Galaxy A35 (`RZCXC17RFEW`) & Forward Ports:**
   - Keep `RZCXC17RFEW` unlocked via USB.
   - Run:
     ```bash
     /Users/manthangarg/Library/Android/sdk/platform-tools/adb -s RZCXC17RFEW reverse tcp:5050 tcp:5050
     /Users/manthangarg/Library/Android/sdk/platform-tools/adb -s RZCXC17RFEW reverse tcp:8000 tcp:8000
     ```
   - *(Reinstall APK only if wiped:)* `adb -s RZCXC17RFEW install -r -d android/app/build/outputs/apk/debug/app-debug.apk`
   - Sign in on **किसान ID / PIN** tab (`farmer@kissan.com` / `Password123!`).

---

## 2. Step-by-Step Live Stage Walkthrough (~5 Minutes)

1. **Personalized Farmer Dashboard & Open-Meteo Spraying Weather:**
   - Show `"Hello, Farmer Friend!"`, `"Nagpur, Maharashtra • Clear Sky"`, `"Spraying Advisory • AI Alert"`, and IoT farm telemetry cards (`Soil Moisture 45.0%`, `27.8°C`, `64.0%`, `6.7 pH`).
   - Tap the **Listen** button on the spray card or open **Weather Alerts** to inspect 7-day Open-Meteo metrics (`OPEN-METEO`, `Spraying Window: Suitable`).
2. **Crop Disease Detection (DINOv2 V1 + Treatment Safety Gate):**
   - Open **Crop Disease Detection** -> tap **Gallery** (select `wheat_rust_stage3.jpg`) or **Wheat Yellow Rust** sample -> optionally tap **Fill with Voice** on Location & Soil Type -> tap **Start Detection**.
   - Highlight the honest distinction between **Cosine Similarity** and **k-NN Neighbor Agreement**, **Needs Review** trigger, and **Treatment Prescriptions Withheld** (both organic and chemical hidden when ambiguous).
   - Point out the **Gemini Second Opinion** consent checkbox (never silent overwrite). *Note: if `GEMINI_API_KEY` still holds an exhausted free-tier quota, frame the `429 quota_exhausted` card as honest service-status transparency while V1 DINOv2 remains operational.*
3. **Livestock Care (Clinical Red-Flag Escalation `1962`):**
   - Open **Livestock Care** -> set `Fever = Yes`, `Visible lesions = ON`, `Appetite = Reduced` -> tap **Run Diagnostics**.
   - Highlight immediate **`URGENT VETERINARY ESCALATION RECOMMENDED`**, prescription withholding for safety, and **`1962`** helpline persistence even without Gemini cloud access.
4. **Live/Cached AGMARKNET Mandi Rates & Compare:**
   - Open **Live Mandi Prices** -> show `crops`, `vegetables`, `fruits` filter chips (`total: 43`, `15/18/10`), toggle Saved star, and open **Compare Mandis** spreads (`sourceStatus` displayed transparently with zero fabricated random numbers).
5. **Verified Government Schemes:**
   - Open **Government Schemes** -> show bookmarks (`PM-KISAN`, `PMFBY`) -> open **Eligibility Check** pre-populated from Nagpur farmer profile.
6. **Smart Crop Planner & Offline Room -> Reconnect Sync:**
   - Open **Crop Planner** showing active crops (`Millet`, `Gram`, `Barley`, `Wheat`) and weather-aware tasks.
   - *(Live Offline Demo)* Remove USB reverse ONLY (`adb -s RZCXC17RFEW reverse --remove-all` — never toggle phone Wi-Fi/hotspot!), create a quick offline plan, force-stop & reopen app to show Room persistence (`"1 offline change(s) queued — tap to sync now"`), then re-run `adb -s RZCXC17RFEW reverse tcp:5050 tcp:5050` + `tcp:8000 tcp:8000` and tap sync to demonstrate deduplicated persistence.
7. **Multilingual Switching (`हिन्दी`) & Call-Agent Readiness Note:**
   - Tap language selector pill (`English` -> `हिन्दी`), show localized Dashboard (`लाइव कृषि नेटवर्क`, `नमस्ते, किसान भाई!`), and restore `English`.
   - Optionally open `http://localhost:5050/api/integrations/call-agent/contract` on laptop browser to show all 6 grounded Sarvam voice tools ready for webhook wiring.
