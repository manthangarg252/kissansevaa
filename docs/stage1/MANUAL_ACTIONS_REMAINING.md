# KissanSevaAI — Manual Actions & External Dependencies Remaining

1. **Refresh / Upgrade `GEMINI_API_KEY` Quota in `backend/.env`:**
   - Current key reaches Google Gemini API cleanly, but `gemini-3-flash` Free Tier hit its `20` requests/day quota (`429 RESOURCE_EXHAUSTED`) during testing.
   - Update `GEMINI_API_KEY` (or upgrade billing in Google AI Studio) and optionally set `GEMINI_MODEL` / `GEMINI_FALLBACK_MODELS` in `backend/.env` before a live AI-heavy demo.

2. **Unlock Physical Device (`SM-A356E` / `RZCXC17RFEW`) for Interactive Hands-On QA:**
   - The Samsung Galaxy A35 connected over USB has a screen lock PIN/biometric enabled, so interactive on-screen UI tapping cannot proceed automatically until unlocked by the device owner.
   - Once unlocked, launch KissanSevaAI and log in with `farmer@kissan.com` / `Password123!` (ADB reverse ports `5050` and `8000` already configured over USB).

3. **Connect Persistent Production MongoDB (`MONGO_URI`) When Deploying Beyond Local Dev Server:**
   - `startDevServer.js` uses `mongodb-memory-server` for zero-setup local demos (state resets when Node dev process restarts).
   - For persistent multi-session storage across restarts, start local `mongod` or configure Atlas `MONGO_URI` and launch `node backend/server.js`.

4. **Monitor `DATA_GOV_IN_API_KEY` Rate Quota:**
   - `api.data.gov.in` returned HTTP `429` on repeated rapid probes after fetching live records earlier; automatic 5-minute cooldown prevents quota lockouts while preserving cached rates.

5. **Provision External Telephony Partner (Optional / Post-Stage 1):**
   - To enable automated outbound/inbound farmer phone calls, configure an external telephony/IVR provider webhook calling `POST /api/integrations/call-agent/webhook` and set `CALL_AGENT_WEBHOOK_SECRET` in `backend/.env`.
