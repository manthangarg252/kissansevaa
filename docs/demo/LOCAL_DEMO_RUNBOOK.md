# KissanSevaAI — Local USB Demo Runbook (MacBook + Samsung Galaxy A35)

## 1. Pre-Demo Startup (One Command)

1. Plug **Samsung Galaxy A35 (`RZCXC17RFEW`)** into the MacBook via USB cable and unlock the screen.
2. From the repository root, run:
   ```bash
   ./scripts/start-local-demo.sh
   ```
   This starts (without duplicates) FastAPI ML (`:8000`, V1 active + V3 shadow), persistent MongoDB (`:27017` using `backend/data/db`), and Node.js Express (`:5050`), configures `adb reverse tcp:5050 tcp:5050` and `tcp:8000 tcp:8000`, and verifies health.
3. Install the latest verified Debug APK onto `RZCXC17RFEW` (required once before tomorrow's demo):
   ```bash
   /Users/manthangarg/Library/Android/sdk/platform-tools/adb -s RZCXC17RFEW install -r android/app/build/outputs/apk/debug/app-debug.apk
   ```
4. Verify everything is green:
   ```bash
   ./scripts/check-local-demo.sh
   ```

---

## 2. 5-Minute Live Demonstration Sequence

1. **0:00–0:45 — Dashboard, Location & Sarvam Helpline:**
   - Open KissanSevaAI on Galaxy A35 (`farmer@kissan.com` / `Password123!` if prompted).
   - Point out the confirmed farm profile (`Nagpur, Maharashtra`), live Open-Meteo weather card (`Clear Sky`, spray advisory + `Listen` TTS), and the **Sarvam AI Voice Helpline** card (`+917965861355` — tap `Copy Number` or `Call Now` to show instant dialer prefill).
2. **0:45–1:45 — Crop Disease AI (V1 DINOv2-S/14 + Uncertainty Safety):**
   - Open **Crop Disease Detection**, select a leaf photo (or `Wheat Yellow Rust` sample), tap **Start Detection**.
   - Highlight `Model: DINOv2-S/14`, cosine similarity + k-NN consensus disclaimer, and show how uncertain/non-leaf photos withhold organic & chemical prescriptions and gate Gemini Second Opinion behind explicit farmer consent.
3. **1:45–2:30 — Livestock Triage Safety:**
   - Open **Livestock Care**, toggle `Fever: Yes` + `Appetite: None` + `Milk Yield: Stopped` + symptom note, tap **Run Diagnostics**.
   - Highlight `URGENT VETERINARY ESCALATION RECOMMENDED`, `1962` helpline, and `Veterinary Safety Notice` withholding unverified antibiotics/steroids.
4. **2:30–3:30 — Live Mandi Rates, Weather & Hindi Schemes:**
   - Open **Live Mandi Prices** -> show `live agmarknet`, observation date (`Obs:`), `Wheat` search, `Compare Mandis` spread (`₹240/q`), and `Saved` favorites.
   - Open **Weather & Spray Window** -> show 7-day Open-Meteo forecast and `Detect Farm GPS`.
   - Switch top-bar language to **हिन्दी** -> open **Government Schemes** -> show Hindi scheme cards, `पात्रता जांच` (Eligibility Check), and official portal launch.
5. **3:30–4:30 — Smart Crop Planner & Voice Auto-Fill:**
   - Open **Smart Crop Planner** -> tap `Fill by Voice` in `+ New 7-Day Plan` -> show live mic recognition + confirmation editing -> toggle a task complete and tap the `Edit` icon to edit title/date/reminder.
6. **4:30–5:00 — Offline Persistence & Sync:**
   - Toggle Airplane mode/Wi-Fi off briefly -> view cached history & weather, toggle/edit a task -> close & reopen app (stays signed in!) -> restore connectivity -> show automatic background sync.

---

## 3. Emergency Recovery Commands

- **USB cable bumped / network error on phone:**
  ```bash
  /Users/manthangarg/Library/Android/sdk/platform-tools/adb -s RZCXC17RFEW reverse tcp:5050 tcp:5050
  /Users/manthangarg/Library/Android/sdk/platform-tools/adb -s RZCXC17RFEW reverse tcp:8000 tcp:8000
  ./scripts/check-local-demo.sh
  ```
- **Clean restart without losing MongoDB data:**
  ```bash
  ./scripts/stop-local-demo.sh
  ./scripts/start-local-demo.sh
  ```
- **Inspect logs if a service fails:**
  ```bash
  tail -n 50 backend/logs/node-demo.log ml-service/logs/ml-demo.log
  ```
