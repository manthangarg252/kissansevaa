# KissanSevaAI Stage 5 — Final Demonstration Script

## 1. Pre-Demo Checklist (2 Minutes Before Demo)

1. Confirm background services are healthy:
   ```bash
   curl -sS http://localhost:8000/health
   curl -sS http://localhost:5050/api/ai/status
   ```
   - Expect ML service: `"active_model": "v1"`, `"shadow_mode_enabled": true`, `67` classes.
   - Expect Node service: `"activeModel": "gemini-3-flash-preview"`, `"sourceStatus": "live_agmarknet"`, `"provider": "open-meteo"`.
2. Ensure USB reverse tunneling is active on the unlocked Android device (`RZCXC17RFEW` or `emulator-5554`):
   ```bash
   adb reverse tcp:5050 tcp:5050 && adb reverse tcp:8000 tcp:8000
   ```
3. Launch `KissanSevaAI` (`farmer@kissan.com` / `Password123!`).

---

## 2. Live 5-Minute Walkthrough Sequence

1. **Dashboard, Personalized Location & Sarvam AI Phone Helpline (60s)**
   - Point out dynamic farm header (`My Farm • <District>, <State>`) and live Open-Meteo weather card + Hindi/English language switcher + Text-to-Speech `Listen` readout.
   - Scroll slightly to **"Talk to Our AI Farming Assistant"**, highlight `+917965861355` and the `"Works on smartphones & basic keypad mobile phones"` badge.
   - Tap **`Copy Number`** (shows confirmation toast/banner) and **`Call Now`** (opens the phone dialer with `+917965861355` prefilled via `Intent.ACTION_DIAL`, without auto-placing a call or needing `CALL_PHONE`).

2. **Automatic Farm Location Detection (45s)**
   - Tap **`Retry Detection` / `Detect Farm Location`**, show `"Use this location for your farm?"` contrasting **`Current Phone Location`** vs **`Saved Farm Location (Current)`** (`Confirm`, `Change Location`, `Retry Detection`).

3. **Crop Disease Detection + Consented Gemini Second Opinion (60s)**
   - Open **Crop Disease Detection**, select a leaf photo (or tap **Wheat Yellow Rust** sample), submit for **V1 DINOv2-S/14** primary screening showing distinct cosine similarity, neighbor consensus, and withheld prescriptions when uncertain.
   - Check the explicit farmer-consent box and tap **Get Gemini Second Opinion** to compare V1 primary vs Gemini Vision reconciliation (`Agreed` / `Disagreed` / `Inconclusive` / `Non-Leaf`).

4. **Livestock Veterinary Triage & Emergency 1962 Escalation (45s)**
   - Open **Livestock Care**, demonstrate **`Fill by Voice`**, toggle fever/appetite/milk yield drop, submit photo + intake, and highlight urgent escalation alerts + withheld antibiotic/dosage notice + one-tap **`1962`** helpline button.

5. **Voice-Assisted Crop Planner, Weather, Mandi, Schemes & Chatbot (60s)**
   - Open **Smart Crop Planner**, demonstrate **`Fill by Voice`** extracting crop, `Sowing Date`, and field size into the editable confirmation card.
   - Show **Live Mandi Prices** (`live agmarknet` + state filtering), **Government Schemes** (`Verified Schemes`), and **AI Assistant** chatbot answering grounded EN/HI queries.
