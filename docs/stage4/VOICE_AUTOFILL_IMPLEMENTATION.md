# KissanSevaAI Stage 4 — Smart Voice Auto-Fill Implementation

## 1. Architecture Overview

Stage 4 upgrades KissanSevaAI's voice pipeline from single-purpose speech buttons into a reusable, offline-first, lifecycle-safe, and editable multilingual form-filling system across Android screens without modifying the V1 crop classification model or core backend contracts.

### Core Speech & Parsing Pipeline
1. **Locale & Partial-Result Speech Transport**
   - [SpeechToText.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/core/ai/SpeechToText.kt) defines `RecognitionLocaleResolution`, `resolveRecognitionLocale(...)`, `onPartialResult`, `onCancelled`, and `cancelListening()` with backward-compatible default parameters.
   - [AndroidSpeechToText.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/core/ai/ondevice/AndroidSpeechToText.kt) enables `RecognizerIntent.EXTRA_PARTIAL_RESULTS = true`, forwards incremental transcripts via `onPartialResults`, resolves Bhojpuri (`bho`) with an explicit `hi-IN` fallback notice rather than silent substitution, and suppresses post-cancel error callbacks during `cancelListening()` / `destroy()`.

2. **Deterministic Offline Multilingual Parser**
   - [AgriculturalVoiceParser.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/core/ai/voice/AgriculturalVoiceParser.kt) parses spoken transcripts offline across Hindi (Devanagari & Romanized), English, Punjabi, Marathi, Bhojpuri, and code-mixed agricultural speech into structured `VoiceExtractionOutcome` records (`extractedFields`, `ambiguousFields`, `warnings`, `usedAiFallback`).
   - Normalizes crop synonyms across languages (e.g., `gehun` / `गेहूं` / `kanak` → `Wheat`; `dhaan` / `धान` → `Paddy`; `makka` / `ਮੱਕੀ` → `Maize`; `kapas` → `Cotton`; `aloo` / `बटाटा` → `Potato`; `tamatar` → `Tomato`), soil types (`Black`, `Loamy`, `Sandy`, `Clay`, `Red Loam`, `Silty`), irrigation sources (`Drip`, `Sprinkler`, `Canal`, `Tubewell / Borewell`, `Rainfed`), animals (`Buffalo`, `Cattle`, `Goat`, `Sheep`, `Poultry`), states, districts, village names, and numeric words.
   - Converts `hectare` deterministically to acres while flagging regional units such as `bigha` or unitless area numbers in `ambiguousFields` for farmer confirmation instead of guessing.
   - Extracts explicit sowing dates (`YYYY-MM-DD`) and flags vague expressions (`kuch din`, unanchored dates) or medication dosage/body-temperature claims for manual review instead of auto-filling unsafe veterinary values.

3. **Deterministic-First Hybrid Extraction**
   - [HybridFieldExtractor.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/core/ai/voice/HybridFieldExtractor.kt) (bound in [AiModule.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/core/ai/AiModule.kt)) always executes `AgriculturalVoiceParser.parse(...)` locally first and only attempts optional `RemoteFieldExtractor` enrichment when requested fields remain unfilled on multi-word inputs. Cloud Gemini errors (`429` quota exhaustion or offline state) are caught gracefully without blocking deterministic extraction.

## 2. Reusable Jetpack Compose Controls

- [VoiceInputComponents.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/ui/component/VoiceInputComponents.kt): Provides **`VoiceInputField`**, featuring:
  - 48dp accessible microphone target with runtime `RECORD_AUDIO` permission request on tap.
  - Inline listening indicator with live partial transcription preview, language switcher (`hi`, `en`, `pa`, `mr`, `bho`), `Stop`, `Cancel`, and `Retry` controls.
  - Editable confirmation preview prior to committing dictated values, plus `DisposableEffect` cleanup calling `cancelListening()` on composable disposal.
- [VoiceFormFillButton.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/ui/component/VoiceFormFillButton.kt): Upgrades **`FillByVoiceButton`** so multi-field forms show an editable review sheet displaying extracted fields and any ambiguity warnings before applying values — **never auto-submitting forms** directly from speech.
- [VoiceControls.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/ui/component/VoiceControls.kt): Updates `MicButton` with `48.dp` minimum touch targets, `onPartialResult` forwarding, and lifecycle-safe listener cleanup.

## 3. Screen Integrations

- **Crop Planner** ([CropPlannerScreen.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/feature/planner/CropPlannerScreen.kt)): `FillByVoiceButton` + individual `VoiceInputField`s including `Crop Name*`, `Sowing Date (YYYY-MM-DD)`, `Soil Type`, field area, irrigation, and `customTask`.
- **Livestock Care** ([LivestockScreen.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/feature/livestock/LivestockScreen.kt)): `FillByVoiceButton` + individual mics for `Animal Type`, `Approximate Age / Group`, symptoms, and duration without auto-submitting triage requests.
- **Settings / Farm Profile** ([SettingsScreen.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/feature/settings/SettingsScreen.kt)): `FillByVoiceButton` + mics for `Farmer Name`, `Farm Name`, `Village / Locality`, `District`, `State`, `Field Area (Acres)`, `Soil Type`, irrigation, and crops.
- **Crop Disease Detection** ([CropDiseaseScreen.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/feature/crop/CropDiseaseScreen.kt)): `FillByVoiceButton` + voice inputs for `Crop Name`, `Symptom Description`, location, and soil hints (persisted to `crop_history` and `CropAnalysis`).
- **Live Mandi Prices** ([LivePricesScreen.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/feature/prices/LivePricesScreen.kt)): Crop search and `Search Market / District` `VoiceInputField`s.
- **Government Schemes** ([SchemesScreen.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/feature/schemes/SchemesScreen.kt)): Voice catalog search (`Search schemes by name or benefit…`) and voice-assisted eligibility inputs.
- **Weather & Spray Window** ([WeatherScreen.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/feature/weather/WeatherScreen.kt)): Spoken `District or City` `VoiceInputField`.
- **AI Chatbot** ([ChatbotScreen.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/feature/chat/ChatbotScreen.kt)): Live partial transcript preview bar alongside existing voice input and TTS readout.
- Localized strings updated in [strings.xml](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/res/values/strings.xml) and [values-hi/strings.xml](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/res/values-hi/strings.xml).
