# KissanSevaAI — External API Credentials & Live Service Status

**Date:** November 26, 2026  
**Security Rule:** Secret keys are stored locally in `backend/.env` (gitignored) and are **never** printed, logged, or committed to version control.

---

## 1. Environment Variable Inventory

| Variable | Feature(s) Using It | Current Status | Notes & Action Required |
|---|---|---|---|
| `GEMINI_API_KEY` | Centralized Gemini service (`backend/services/geminiService.js`) powering Chatbot, Crop Second Opinion, Crop Enrichment, Livestock Clinical Triage, Weather AI summary, Crop Planner AI note, Scheme Explainer, Loan Advisor, Carbon, Traders, IoT Insights, Voice Field Extraction | Configured, **Quota Exhausted (`429 RESOURCE_EXHAUSTED`)** on `gemini-3-flash` Free Tier (`20` RPD limit reached during baseline testing) | All deterministic features (Weather rules, 7-day Crop Planner, Verified Schemes eligibility, V1 DINOv2 scan, cached Mandi rates) keep working seamlessly. **Action:** Upgrade billing/tier on Google AI Studio or supply a fresh paid `GEMINI_API_KEY` / adjust `GEMINI_MODEL` in `backend/.env` before live Gemini demo turns. |
| `GEMINI_MODEL` | Active default Gemini model (`gemini-3-flash-preview`) | Optional (defaults to `gemini-3-flash-preview`) | Configure another provisioned Gemini model or comma-separated `GEMINI_FALLBACK_MODELS` if desired. |
| `DATA_GOV_IN_API_KEY` / `MARKET_API_KEY` | Live APMC Mandi Price fetcher (`backend/services/marketDataService.js`) | Configured; observed upstream HTTP `429` rate limit during repeated probes after initial fetch | Rate-limit cooldown (5 min) preserves last verified prices (`93` listings earlier fetched) without fabricating random market prices. **Action:** Keep under Data.gov.in rate quota or upgrade Data.gov.in key if higher polling frequency is needed. |
| `ML_SERVICE_URL` | FastAPI DINOv2 Crop Disease Inference (`http://localhost:8000`) | Active & healthy (`V1` production, `V3` shadow mode) | Local service; requires no external API key. |
| **Open-Meteo API** | Live Weather & 7-Day Forecast (`backend/services/weatherService.js`) | **Live `200 OK` verified** (`open-meteo.com`) | Requires **no API key**. Uses `WeatherCache` 30-min MongoDB TTL + offline Room cache. |
| `MONGO_URI` | Production persistent MongoDB database | Configured (dev server uses `mongodb-memory-server` cleanly when run via `startDevServer.js`) | For multi-day production persistence across restarts, ensure local `mongod` or Atlas URI is running. |
| `JWT_SECRET` | User authentication JWT signing | Required in `server.js` (auto-ephemeral in `startDevServer.js`) | Set strong secret in `backend/.env` for persistent tokens across restarts. |
| `CALL_AGENT_WEBHOOK_SECRET` | External telephony call-agent contract (`backend/routes/callAgentRoutes.js`) | Optional / Not configured | **External Telephony Provider Needed:** Stage 1 provides the webhook contract & read-only context endpoints; an external Twilio/Exotel/SIP/IVR partner account is required to place or answer phone calls. |
