# KissanSevaAI Stage 4 — Remaining Manual Actions

Because automation ran against headless `emulator-5554` (`-no-audio`) while the physical Samsung Galaxy A35 (`RZCXC17RFEW`) was disconnected and Gemini free-tier cloud quota remained exhausted (`429`), the following manual actions remain before physical field deployment:

1. **Install Final Debug APK on Physical Samsung Galaxy A35 (`RZCXC17RFEW`)**
   - Connect the handset over USB debugging (or wireless ADB), apply `adb -s RZCXC17RFEW reverse tcp:5050 tcp:5050 && adb -s RZCXC17RFEW reverse tcp:8000 tcp:8000`, and run:
     ```bash
     adb -s RZCXC17RFEW install -r android/app/build/outputs/apk/debug/app-debug.apk
     ```

2. **Physical Handset Acoustic Voice Recognition QA**
   - Test live spoken utterances into `VoiceInputField` and `FillByVoiceButton` across Hindi (`hi-IN`), English (`en-IN`), Punjabi (`pa-IN`), Marathi (`mr-IN`), and Bhojpuri (`hi-IN` fallback notice) in real ambient farm noise on Crop Planner, Livestock Care, and Farm Profile screens.
   - Verify Android on-device Google Speech Services / Soda offline language packs are downloaded on the handset if offline voice recognition without internet is desired.
   - Verify speaker clarity of Text-to-Speech advisory readouts (`Read aloud` buttons).

3. **Physical Handset GPS + Reverse Geocoder Verification**
   - Tap `Detect` / `Retry Detection` on Dashboard and Settings outdoors on the Galaxy A35 to verify real GNSS/cell coordinates, Android `Geocoder` resolution of Indian district/village/state, explicit `"Use this location for your farm?"` confirmation, and persistence into Weather, Mandi, Schemes, and Crop Planner.

4. **Gemini API Quota / Billing Decision (Non-Blocking)**
   - Deterministic `AgriculturalVoiceParser` already handles Stage 4 voice form filling offline cleanly without Gemini. Re-enabling cloud secondary AI extraction via `RemoteFieldExtractor` only requires supplying a higher-quota Gemini API key in `backend/.env`.
