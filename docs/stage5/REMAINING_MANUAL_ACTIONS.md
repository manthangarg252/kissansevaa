# KissanSevaAI Stage 5 — Remaining Manual Actions

Sarvam AI call-agent webhook/SIP/backend setup has been intentionally removed per Stage 5 requirements (the Sarvam helpline operates independently and is exposed on Android only via `SARVAM_HELPLINE_NUMBER=+917965861355` with `Intent.ACTION_DIAL`).

Only the following physical/operational actions remain for human verification:

1. **Unlock & Run Interactive Demo on Samsung Galaxy A35 (`RZCXC17RFEW`)**
   - The final [app-debug.apk](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/build/outputs/apk/debug/app-debug.apk) is already installed on `RZCXC17RFEW` with `adb reverse tcp:5050 tcp:5050` and `tcp:8000 tcp:8000` active, but the handset lockscreen (`mDreamingLockscreen=true`) blocked unattended UI automation.
   - Unlock the phone, open `KissanSevaAI`, and verify visually:
     - **Sarvam Helpline Call Flow**: Tap `Call Now` on the **"Talk to Our AI Farming Assistant"** card, confirm the Samsung dialer opens with `+917965861355`, and place a short human voice test call to verify end-to-end Sarvam telephony audio.
     - **Acoustic Microphone Dictation**: Speak Hindi (`hi-IN`), English (`en-IN`), Punjabi (`pa-IN`), and Marathi (`mr-IN`) utterances into `Fill by Voice` / `VoiceInputField` on Crop Planner, Livestock Care, and Settings in ambient noise, and confirm loudspeaker Text-to-Speech clarity (`Listen`).
     - **Real Outdoor GNSS + Geocoder**: Tap `Detect Farm Location` outdoors, grant permission, confirm `"Use this location for your farm?"`, and verify real district/state updates across Weather, Mandi, Schemes, and Crop Planner.
     - **Physical Camera Capture**: Photograph a real leaf / animal via CameraX and confirm prediction & Gemini screening.

2. **Gemini API Free-Tier Daily Quota (`20` RPD)**
   - The configured free-tier key on `gemini-3-flash-preview` has a `20` requests/day cap (`GenerateRequestsPerDayPerProjectPerModel-FreeTier`).
   - Either wait for the daily quota window reset (~14.5 hours from session exhaustion) before live Gemini testing during the demo, or swap in a higher-quota/billed `GEMINI_API_KEY` in gitignored `backend/.env` and restart Node on `:5050`.
   - Note: Re-verify one HTTP `200` Express `POST /api/livestock/analyze` call once quota resets to observe the HTTP `200` path live end-to-end (direct SDK execution and Android parsing are already verified).
