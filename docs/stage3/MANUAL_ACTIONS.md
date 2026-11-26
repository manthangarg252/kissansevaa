# KissanSevaAI — Stage 3 Manual & Operator Actions Required

Everything solvable autonomously within local code, ML V1/V3 shadow serving, persistent local MongoDB, backend unit/integration tests, and Galaxy A35 / Emulator builds is complete. Only the external or physical human steps below remain outside autonomous execution:

---

## 1. Restore Live Google Gemini Quota (`BLOCKED` Dependency)

- **Why Required:** Configured `GEMINI_API_KEY` in `backend/.env` returns HTTP `429 quota_exhausted` for `gemini-3-flash-preview` across Vision, Livestock enrichment, Chatbot, Schemes AI explain, and Call-Agent `general_farming_advice`.
- **Exact Steps:**
  1. Enable billing / allocate active quota in Google AI Studio / Google Cloud for your project, or generate a new API key.
  2. Replace `GEMINI_API_KEY=<new_key>` in `/Users/manthangarg/Desktop/kissansevaai-main/backend/.env`.
  3. Restart `:5050` (`npm --prefix backend run dev:persistent`).
  4. Verify via one `GET /api/ai/status` check after sending one Chatbot question (`successCount >= 1`).

---

## 2. Complete External Sarvam Call-Agent Dashboard Wiring (`IMPLEMENTED BUT UNVERIFIED` Live Call)

- **Why Required:** KissanSevaAI's 6 webhook tools and HMAC/shared-secret auth (`backend/services/callAgentService.js`) are verified offline against MongoDB, but no live inbound Sarvam telephony call has hit the endpoint yet.
- **Exact Steps:**
  1. Set secrets in `/Users/manthangarg/Desktop/kissansevaai-main/backend/.env`:
     - `CALL_AGENT_WEBHOOK_SECRET=<strong-random-secret>`
     - `SARVAM_WEBHOOK_SIGNING_SECRET=<hmac-secret-from-sarvam>` (and `SARVAM_API_KEY` if needed).
  2. Expose local backend `:5050` over HTTPS (Cloudflare Tunnel, ngrok, or production server).
  3. Restart Node `:5050` and check `GET /api/integrations/call-agent/contract` shows `"status": "webhook_configured_ready"`.
  4. In your existing Sarvam Call Agent dashboard, configure the 6 custom tools (`crop_assistance`, `livestock_questions`, `weather_forecasts`, `mandi_prices`, `government_schemes`, `general_farming_advice`) pointing to `/api/integrations/call-agent/tools/<toolName>` with auth/HMAC headers (`X-Sarvam-Signature` + `X-Sarvam-Timestamp` or `X-Call-Agent-Secret`).
  5. Place a real inbound phone call to your Sarvam number and inspect Node server logs for `200 OK` tool responses.

---

## 3. Physical Human Microphone & Speaker Audio Check

- **Why Required:** On-device `SpeechRecognizer` (`en/hi/mr/pa`) and `TextToSpeech` (`en-IN/hi-IN/mr-IN/pa-IN/sa-IN`) passed instrumentation diagnostics on `SM-A356E` and triggered `"Listening for form details..."` in UI, while actual spoken speech recognition accuracy and handset speaker playback could not be heard autonomously over ADB.
- **Exact Steps:** Before stage presentation, unlock `SM-A356E` (`RZCXC17RFEW`), speak a sample phrase into **Fill with Voice** on the Crop Disease or Crop Planner screen, and confirm **Listen** / **Read aloud** audio is audible through the speaker at demo volume.

---

## 4. Keep USB Tunnels & Phone Unlocked During Local Stage Demo

- Keep `SM-A356E` (`RZCXC17RFEW`) unlocked and connected over USB (`adb -s RZCXC17RFEW reverse tcp:5050 tcp:5050` + `tcp:8000 tcp:8000`) whenever demonstrating against localhost Mac services (`:5050` and `:8000`). Do **not** disable the phone's cellular hotspot during live demos if Mac relies on it for Open-Meteo/AGMARKNET internet access.
- **Pre-Existing Uncommitted Deletions Note:** Three legacy model files (`ml-service/models/best_vit_tiny_patch16_224.pth`, `classes.json`, `vector_metadata.json`) remain unstaged in the working tree from before Stage 2/3 and are intentionally untouched.
