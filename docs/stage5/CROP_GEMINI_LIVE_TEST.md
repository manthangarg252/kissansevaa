# KissanSevaAI Stage 5 — Live Crop Second Opinion & Chatbot Verification

**Classification Highlights:**
- V1 DINOv2-S/14 primary inference preserved (`v1` active, `v3` shadow): **VERIFIED LIVE**
- Farmer-consented Gemini Vision Second Opinion (`/api/crop/second-opinion`) on Wheat Rust macro, Apple camera leaf, and Non-Leaf UI screenshot: **VERIFIED LIVE**
- Explicit consent gate (`farmerConsented=false` -> `400`) and non-leaf withholding: **VERIFIED LIVE**
- `inconclusive` reconciliation status & Android `CropDiseaseScreen` rendering: **VERIFIED BY AUTOMATED TEST** (Android unit + backend unit) and **VERIFIED ON EMULATOR** (`CropDiseaseScreen` build/layout)
- Grounded Gemini Chatbot across 6 domains (Crop EN, Planner HI, Livestock HI, Weather EN, Mandi HI, Schemes EN) & multi-turn persistence: **VERIFIED LIVE** (until free-tier 20 RPD limit hit on follow-up burst; deterministic fallback verified)

---

## 1. Crop Second Opinion Live Test Results

Executed live against `:5050` (`gemini-3-flash-preview`) and `:8000` (`dinov2_vits14` V1 active):

| Sample Input | V1 DINOv2 Primary (`/api/crop/analyze`) | Consent Gate | Gemini Second Opinion (`/api/crop/second-opinion`) | Reconciliation & Safety |
|---|---|---|---|---|
| `1795644825381-wheat_rust_macro.jpg` | `Wheat Black Rust` (`sim=0.81607`, `agree=0.4`, `needs_review=true`, `treatmentWithheld=true`, `predictionSource="dinov2"`) | Verified `farmerConsented=false` → HTTP `400` | HTTP `200`, `secCrop="Wheat"`, `secDisease="Wheat Yellow Rust (Stripe Rust)"`, `secConfidence=0.95`, linear pustule visual description | `primaryPreserved=true`, `reconciliationStatus="agreed"` (`crop_vision` + `crop_advisory` succeeded live) |
| `1795644832118-samsung_cam_apple.jpg` | `Apple scab` (`sim=0.69691`, `agree=1.0`, `predictionSource="dinov2"`) | Consented (`true`) | HTTP `200`, `secCrop="Apple"`, `secDisease="Apple Scab"`, `secConfidence=0.82`, circular dark-brown lesion visual summary | `primaryPreserved=true`, `reconciliationStatus="agreed"` |
| App Dashboard UI Screenshot (`01_dashboard.png`) | `Wheat Aphid` (`sim=0.21464`, `agree=0.4`, `needs_review=true`, `treatmentWithheld=true`) | Consented (`true`) | HTTP `200`, `isPlantLeaf=false`, `secCrop="None"`, `secDisease="Non-Leaf Image"`, `secConfidence=0.15` | `primaryPreserved=true`, `reconciliationStatus="unsupported_or_non_leaf"`, **`treatmentWithheld=true`** |

- Updated [cropController.js](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/controllers/cropController.js), [geminiVisionService.js](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/services/geminiVisionService.js), and [CropDiseaseScreen.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/feature/crop/CropDiseaseScreen.kt) to add an explicit **`inconclusive`** reconciliation state (`status: 'inconclusive'`) when Gemini flags low certainty/unclear evidence, withhold treatments cleanly, and label any Gemini score explicitly as a non-calibrated visual screening heuristic.

---

## 2. Live Agricultural Chatbot Results (`POST /api/chat`)

All 6 domain categories plus a multi-turn follow-up were executed live against `:5050` with grounded tool injection in English and Hindi (`gemini-3-flash-preview` active):

- **`crop_en` (Crop EN, HTTP `200`)**: `groundedSources: ["farm_profile", "weather(cache)", "crop_history(2)"]` — explained Finger Test & wheat rust checks grounded to Nagpur location.
- **`planner_hi` (Planner HI, HTTP `200`)**: `groundedSources: ["farm_profile", "weather(cache)", "crop_planner"]` — responded in Hindi referencing the database task `"28 नवंबर 2026: बाजरा - पहली निराई और गुड़ाई"`.
- **`livestock_hi` (Livestock HI, HTTP `200`)**: `groundedSources: ["farm_profile", "livestock_history(2)"]` — advised contacting veterinary hospital / `1962` helpline in Hindi for 2-day buffalo fever + milk drop.
- **`weather_en` (Weather EN, HTTP `200`)**: `groundedSources: ["farm_profile", "weather(live)"]` — cited actual live Open-Meteo telemetry for Nagpur (`22.3°C`, `55%` RH, `7.2 km/h` wind) before recommending spray precautions.
- **`mandi_hi` (Mandi HI, HTTP `200`)**: `groundedSources: ["farm_profile", "mandi_prices(5)"]` — cited live AGMARKNET rates (`Paddy ₹2800/quintal`, `Cabbage ₹1800/quintal`, `Black pepper ₹74000/quintal`) without inventing figures.
- **`schemes_en` (Schemes EN, HTTP `200`)**: `groundedSources: ["farm_profile"]` — summarized verified `PM-KISAN` and `PMFBY` details for Maharashtra.
- **`followup_en` (Multi-turn EN, HTTP `200`)**: Retrieved prior session turns and answered follow-up referencing the user's 4-acre Nagpur land size; `GET /api/chat/history` confirmed `5` saved session turns (`1,251–1,567` tokens/turn).
