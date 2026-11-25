# KissanSevaAI — External Call-Agent Integration Contract

Stage 1 intentionally does **not** build a standalone telephony dialer or SIP/IVR call-agent server (per Stage 1 boundary rules), and repository inspection confirmed zero pre-existing call-agent code.

Instead, KissanSevaAI exposes a lightweight integration adapter in `backend/routes/callAgentRoutes.js` so an external voice/IVR partner (e.g., Twilio, Exotel, Knowlarity, or Bhashini voice bridge) can fetch grounded real-time agricultural summaries safely.

---

## Endpoints

### 1. Contract Discovery
- **GET `/api/integrations/call-agent/contract`**
- Returns integration readiness status, required header (`X-Call-Agent-Secret`), and supported intents (`mandi_rate_lookup`, `weather_spray_check`, `scheme_summary`).

### 2. Grounded Advisory Webhook
- **POST `/api/integrations/call-agent/webhook`**
- **Authentication:** `X-Call-Agent-Secret: <CALL_AGENT_WEBHOOK_SECRET>` (returns HTTP `503 not_configured` until configured in `backend/.env`; returns `401` on invalid secret).
- **Request Body:**
```json
{
  "intent": "mandi_rate_lookup | weather_spray_check | scheme_summary",
  "crop": "Wheat",
  "state": "Maharashtra",
  "location": "Nagpur"
}
```
- **Response (`200 OK`):** Concise plain-text `spokenSummary` ready for telephony TTS playback along with structured payload data.
