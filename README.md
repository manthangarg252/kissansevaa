# KissanSevaAI 🌾

**Offline-First, Multilingual AI Agricultural Platform for Indian Farmers**

KissanSevaAI combines computer vision crop disease retrieval (**DINOv2-S/14 ONNX + FAISS**), safety-first veterinary triage, offline multilingual voice auto-fill, live meteorological & mandi market intelligence, bilingual government scheme discovery, and cloud/USB deployment into an end-to-end native Android + Node.js/MongoDB + FastAPI architecture.

---

## Core Capabilities

- **Crop Disease Detection (V1 Production + V3 Shadow R&D):**
  - On-server **DINOv2-S/14 ONNX** feature extraction (`dinov2_vits14_cls_mean.onnx`, `768`-dim CLS + mean patch embeddings) + **FAISS `IndexFlatIP`** cosine similarity search across **67 crop-disease classes** (`2,144` reference embeddings).
  - Clean minimalist result card displaying top predicted disease, crop, severity, text-to-speech (`Read aloud`), Organic & Chemical treatment tips, and prevention strategies.
  - Versioned **V3 Hybrid Candidate** (`CalibratedClassifierCV(LinearSVC, C=2.0)` + Full FAISS weighted fusion + 6-signal open-set OOD gate) runs strictly in **shadow mode** (`ENABLE_V3_SHADOW=true`) logging comparison telemetry without modifying production predictions.
- **Safety-First Livestock Care:**
  - Deterministic clinical red-flag escalation engine covering **7 species** (`Cattle`, `Buffalo`, `Goat`, `Sheep`, `Poultry`, `Pig`, `Other`) across English and Hindi/regional keywords.
  - Intentionally withholds unverified prescription antibiotics, steroids, and injectable dosages while highlighting `URGENT VETERINARY ESCALATION RECOMMENDED` and **Pashu Helpline `1962`**.
- **Multilingual Smart Voice Auto-Fill (Stage 4):**
  - Offline-first Android `SpeechRecognizer` (`EXTRA_PARTIAL_RESULTS`) + deterministic `AgriculturalVoiceParser` supporting **Hindi (Devanagari & Romanized), English, Punjabi, Marathi, and Bhojpuri**.
  - Normalizes regional crop/soil/irrigation terms, converts `hectare` -> acres, flags ambiguous units (`bigha`), and requires farmer confirmation before saving.
- **Automatic Farm Location & GPS Personalization:**
  - Multi-layer location pipeline (`FusedLocationProviderClient` High Accuracy -> Balanced Wi-Fi/Cell indoor fallback -> OS `LocationManager` -> cached last fix + Android `Geocoder`) with explicit farmer confirmation (`"Use this location for your farm?"`) and manual district/state fallback.
- **Live Weather, AGMARKNET Mandi Rates & Bilingual Schemes:**
  - **Open-Meteo Weather:** 7-day forecast + Spraying Window suitability (`Temperature`, `Humidity`, `Wind Speed`) + offline cache (`WeatherCache`).
  - **Live AGMARKNET Mandi Rates:** Observation dates (`Obs:`), state/commodity search, `Compare Mandis` spread analysis, Dashboard prioritization/deduplication by farmer crops, and saved favorites.
  - **Verified Government Schemes:** Curated catalog (`2026.11`, 10 schemes including `PM-KISAN`, `PMFBY`, `KCC`, `PM-KUSUM`, `Soil Health Card`) with full **English + Hindi** cards, eligibility checker, document lists, and official portal links.
- **Smart 7-Day Crop Planner & Offline Sync:**
  - Room DB v5 (**15 local entities**, account-isolated `MIGRATION_4_5`) + WorkManager `DataSyncWorker` syncing queued offline task completions, task edits (`title`, `description`, `scheduledDate`, `reminderEnabled`), and bookmarks to `/api/sync/batch`.
- **Grounded AI Chatbot & Inclusive Helpline:**
  - Multi-turn Gemini AI assistant (`gemini-3-flash-preview` via `@google/genai`) grounded in live farmer profile, weather, crop history, livestock triage, and market data, with honest quota fallback (`groundedContextSummary`).
  - Independent **Sarvam AI Telephone Helpline (`+917965861355`)** accessible from Dashboard (`Call Now` via `Intent.ACTION_DIAL` + `Copy Number`) for smartphones and basic keypad phones.

---

## System Architecture

```text
+-----------------------------------+
|      Native Android App           |
| Kotlin 2.1 • Jetpack Compose (M3) |
| Hilt • Room v5 • WorkManager      |
| CameraX • Voice STT/TTS • GPS     |
+-----------------+-----------------+
                  | HTTP / HTTPS (JSON + Multipart)
                  | (USB Demo: adb reverse 5050 & 8000)
                  v
+-----------------------------------+       +----------------------------------+
|   Node.js + Express Backend       |------>|    FastAPI ML Service (:8000)    |
| Port 5050/5000 • JWT + bcryptjs   | HTTP  | ONNX Runtime (CPU) + FAISS       |
| 16 REST Route Modules • Multer    |       | V1 DINOv2 Active • V3 Shadow     |
| Gemini SDK • AGMARKNET • Meteo    |       +----------------------------------+
+-----------------+-----------------+
                  | Mongoose ODM
                  v
+-----------------------------------+
|      Persistent MongoDB 8         |
| Port 27017 • 15 Collections       |
+-----------------------------------+
```

For production cloud deployment (`docker-compose.prod.yml`), **Caddy 2** terminates automatic Let's Encrypt HTTPS TLS (`:80` / `:443`) in front of non-root `backend`, read-only `ml-service`, and `--auth`-protected `mongodb` on a private Docker bridge network.

---

## Repository Structure

```text
kissansevaai-main/
├── android/                  # Active Native Android Kotlin + Jetpack Compose application
├── backend/                  # Express.js REST API, Mongoose models, Gemini/Weather/Mandi/Sync services
├── ml-service/               # FastAPI DINOv2-S/14 ONNX + FAISS inference service & V3 shadow adapter
├── scripts/                  # One-click local USB demo scripts (start/check/stop-local-demo.sh)
├── infra/                    # Production Caddyfile, MongoDB auth init, backup/deploy/rollback scripts
├── docker-compose.prod.yml   # Single-VM production orchestration (Caddy + Backend + ML + MongoDB)
├── .env.prod.example         # Production environment template (no secrets)
└── docs/                     # Stage 1–6 engineering reports & Stage 7 demo runbooks (docs/demo/)
```

*(Root legacy React/Vite files from early prototyping remain archived at top level; active development targets `android/`, `backend/`, `ml-service/`, and `infra/`.)*

---

## Quick Start (Local Development & USB Phone Demo)

### 1. Prerequisites
- **Node.js** `>= 20`, **Python** `3.11+` (`ml-service/venv`), **MongoDB** (`mongod`), **JDK 21**, **Android SDK Platform-Tools (`adb`)**
- Model assets in `ml-service/model_assets/`:
  `dinov2_vits14_cls_mean.onnx`, `crop_index.faiss`, `train_labels.npy`, `classes.json`, `model_meta.json`

### 2. Configure Environment
```bash
cp backend/.env.example backend/.env
# Fill in JWT_SECRET, GEMINI_API_KEY, and optional DATA_GOV_IN_API_KEY
```

### 3. One-Click Local USB Demo Stack
Connect an Android phone with USB debugging enabled and run from the repository root:
```bash
./scripts/start-local-demo.sh   # Starts ML (:8000), Persistent MongoDB (:27017), Backend (:5050), adb reverse
./scripts/check-local-demo.sh   # Verifies health, V1 active / V3 shadow flags, and USB reverse tunnels
./scripts/stop-local-demo.sh    # Cleanly stops services while preserving MongoDB farmer data
```
For full demonstration steps and recovery commands, see [LOCAL_DEMO_RUNBOOK.md](docs/demo/LOCAL_DEMO_RUNBOOK.md).

---

## Running Tests

```bash
# FastAPI ML service (16 tests)
cd ml-service && venv/bin/pytest tests/ -v

# Node.js backend (uses isolated in-memory MongoDB)
cd backend && NODE_ENV=test node --test tests/*.test.js

# Android JVM unit tests & debug APK
./android/gradlew -p android testDebugUnitTest assembleDebug
```

*(Set `JAVA_HOME` to JDK 21 if your system default differs.)*

---

## Module Documentation

- **Android App:** [android/README.md](android/README.md)
- **Backend API:** [backend/README.md](backend/README.md)
- **ML Inference Service:** [ml-service/README.md](ml-service/README.md)
- **Cloud Deployment Guides:** [docs/stage6/01_CLOUD_ARCHITECTURE.md](docs/stage6/01_CLOUD_ARCHITECTURE.md)
- **Demo Verification & Fixes:** [docs/demo/FINAL_DEMO_READINESS.md](docs/demo/FINAL_DEMO_READINESS.md)
