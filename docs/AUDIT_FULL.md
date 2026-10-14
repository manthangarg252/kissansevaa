# INDEPENDENT FULL AUDIT REPORT — KissanSevaAI

**Date:** October 13, 2026  
**Auditor:** Independent Senior Reviewer (Security, Backend, Android, ML, Product)  
**Target Repository:** `manthangarg252/kissansevaa` (branch `main` / `fix/pre-phase3-cleanup`)  
**Audit Mode:** Read-Only Verification with Concrete Evidence  

---

## 1. Executive Summary & Scorecard

### Executive Summary
KissanSevaAI demonstrates strong foundational engineering across its core Android app architecture (Clean Architecture, Room offline caching, AndroidKeyStore AES-GCM token encryption, 117-key multilingual localization across 5 Indian languages, and 55 passing unit tests). However, the project is **not ready for production shipment**. Critical blockers prevent deployment: the release Android app targets a non-existent domain (`api.kissansevaai.com` returns NXDOMAIN); the Android login screen ships with hardcoded dev credentials (`farmer@kissan.com` / `Password123!`); backend `/uploads` exposes all user photos publicly without authentication or upload sanitization; the dev logger logs unredacted JWT session tokens in response bodies; the web frontend login is a non-functional mock; and the ML service uses placeholder labels (`class_0`..`class_14`) with unsafe `torch.load`. No legal or agricultural safety disclaimers are present.

### Red / Amber / Green Scorecard (Areas A–K)

| Area | Name | Status | Summary Justification |
| :--- | :--- | :---: | :--- |
| **A** | Git & Repo Hygiene | **AMBER** | Authors strictly 1 person; 0 secrets committed; but 22.1MB PyTorch `.pth` binary tracked in Git. |
| **B** | Build & Supply Chain | **AMBER** | Android & Web build clean from source; 16 web npm audit vulnerabilities (1 critical ws), 3 backend high; compileSdk 36. |
| **C** | Backend (Node/Express) | **RED** | Unauthenticated `/uploads` static file exposure; unsafe multer filename concatenation; unredacted JWTs in response logs; unauthenticated `/api/iot/update` write. |
| **D** | ML Service (FastAPI) | **RED** | Placeholder labels `class_0`..`class_14`; unsafe `torch.load` pickle risk without `weights_only=True`; unauthenticated endpoint. |
| **E** | Android Application | **AMBER** | Excellent Room caching, Keystore encryption, and clean UI; but hardcoded test credentials in `LoginScreen.kt`, and release `BASE_URL` is dead NXDOMAIN. |
| **F** | Web App (React/Vite) | **RED** | `Login.tsx` & `Signup.tsx` are fake `setTimeout` mocks; `api/gemini.js` is an unauthenticated public proxy consuming owner API key. |
| **G** | Product Safety & Compliance | **RED** | Zero medical/pesticide disclaimers; no Privacy Policy; images sent to cloud without DPDP consent notice; no account deletion path. |
| **H** | Docs vs Reality | **AMBER** | Model mismatch (`gemini-1.5-flash` in web vs `gemini-3-flash-preview` in backend); web auth claimed working but is fake mock. |
| **I** | Claims Ledger | **AMBER** | 13 TRUE, 3 FALSE, 2 PARTIAL, 2 NOT VERIFIED (due to missing ~/Desktop photo files). |
| **J** | Real-World Scenarios | **AMBER** | Offline Room caching works; 401 logout works; missing test photos prevent live multipart image upload pipeline verification. |
| **K** | Operations & Phase 3/4 Readiness | **RED** | No CI/CD workflows; no LiteRT mobile model; unbounded Gemini cost exposure with no per-user rate limiting; local disk uploads without cloud backup. |

---

## 2. Suspects Table (S1–S15)

| ID | Hypothesis | Verdict | Evidence |
| :--- | :--- | :---: | :--- |
| **S1** | A crop analysis response had "language":"en" while text was in Punjabi. Does requested language reach Gemini and response? | **CONFIRMED** | In `backend/services/geminiVisionService.js:100-142`, `getTreatmentDetails` fallback returns hardcoded English strings. In `cropController.js:53`, `res.json` echoes `req.body.language` regardless of the language returned by model or fallback. Furthermore, `language` is not stored in MongoDB `CropAnalysis` schema. |
| **S2** | `imageUrl` in livestock/crop is `http://127.0.0.1:<port>/uploads/...` which phone cannot load. `/uploads` publicly served without auth or limits? | **CONFIRMED** | In `livestockController.js:55`, `imageUrl: ${req.protocol}://${req.get('host')}/uploads/${file.filename}` resolves to `127.0.0.1` (unreachable from Android device/emulator). In `app.js:38`, `app.use('/uploads', express.static(...))` serves files completely unauthenticated. Multer in `cropRoutes.js:29` has no `fileSize` limit and no `fileFilter` MIME check. |
| **S3** | `api.kissansevaai.com` appears in code. Is it release `BASE_URL`? Does it resolve? | **CONFIRMED** | `android/app/build.gradle.kts:46` sets release `BASE_URL = "https://api.kissansevaai.com"`. Shell execution `host api.kissansevaai.com` returns `Host api.kissansevaai.com not found: 3(NXDOMAIN)`. Release builds will fail immediately with `UnknownHostException`. |
| **S4** | `/api/iot/update` has no auth and writes data. Any other unauthenticated write routes? | **CONFIRMED** | In `backend/routes/iotRoutes.js:9`, `router.post('/update', updateStats)` lacks `protect` middleware. In `iotController.js:12`, it mutates in-memory `liveIoTData` with arbitrary request body. Other write routes (`/register`, `/login`) are intentionally public; all other POST routes are protected. |
| **S5** | Dev scripts contain dummy login (`farmer@kissan.com` / `Password123!`). Is it reachable in production code paths? | **CONFIRMED** | Not only is it in `backend/scripts/startDevServer.js:32-37`, but in `android/app/src/main/kotlin/com/kissansevaai/feature/auth/LoginScreen.kt:38-39`, `email` and `password` default to `"farmer@kissan.com"` and `"Password123!"` via `mutableStateOf(...)`, shipping pre-filled directly in release APK. |
| **S6** | User text is placed into Gemini prompts: prompt injection & schema enforcement. | **CONFIRMED** | User input is string-interpolated without sanitization or delimiter isolation in `geminiVisionService.js:48` (`location`, `soilType`), `livestockController.js:27` (`animalType`), `fieldExtractionController.js:37` (`text`), and `chatController.js:12` (`mode`). `chat` endpoint has no JSON schema enforcement and returns unconstrained LLM text. |
| **S7** | README says one Gemini model; code uses another. Docs contradict code (AGP 8.8.2 vs 8.9.1, CameraX 1.4.1 vs 1.6.2). | **CONFIRMED** | `README.md:113, 452` claims "Gemini 3 Flash"; backend uses `gemini-3-flash-preview`; web proxy `api/gemini.js:26` uses `gemini-1.5-flash`. In `libs.versions.toml:2, 17`, AGP is `8.9.1` and CameraX is `1.6.2`, whereas older audit docs claimed 8.8.2 and 1.4.1. |
| **S8** | `ml-service` has placeholder class names (`class_0`..`class_14`). Does backend ever call it or is it dead? | **CONFIRMED** | In `ml-service/app.py:28`, `CLASS_NAMES = [f"class_{i}" for i in range(15)]`. Backend calls `http://localhost:8000/predict-crop` via `modelService.js:15`. When down, it returns `is_unknown: true`. When up, it returns `class_X` which is forwarded to Gemini `getTreatmentDetails` prompt as the detected disease name. |
| **S9** | Two auth middleware files exist (`auth.js`, `authMiddleware.js`). Do they behave identically? | **CONFIRMED** | Both verify JWT and attach `req.user`. However, `backend/routes/traderRoutes.js:4` imports `auth.js`, while all other 10 routes import `authMiddleware.js`. In `auth.js:24`, `res.status(401).json(...)` lacks a `return` keyword, whereas `authMiddleware.js:34` explicitly returns. |
| **S10** | `EngineRouter`'s device-tier logic may be unused and `ON_DEVICE` may not fall back to remote. | **CONFIRMED** | In `AiEngine.kt:28-70`, `EngineRouter` contains no device-tier inspection code. No ViewModel in the app invokes `EngineRouter` (it is dead code outside unit tests). In `infer()`, `ON_DEVICE` mode explicitly catches exceptions and falls back to `remoteEngine.infer(input)`. |
| **S11** | Some tests may over-mock and assert little (`ImageCompressorTest` has 1 test; `TextToSpeechTest` 2). | **CONFIRMED** | `ImageCompressorTest.kt` contains 1 test that only asserts `assertEquals(1280, ImageCompressor.MAX_LONG_EDGE)` and `assertEquals(80, ImageCompressor.JPEG_QUALITY)` (pure tautology on static constants). `TextToSpeechTest.kt` heavily mocks the constructor rather than real speech output. |
| **S12** | Compose BOM / Navigation / Coil pinned old because newer versions need compileSdk 37. | **REFUTED** | `compileSdk` in `build.gradle.kts` is 36 (Android 16 Baklava). Android SDK 37 does not exist. Compose BOM `2025.02.00`, Navigation `2.8.8`, and Coil `3.1.0` are current stable releases fully compatible with SDK 36. |
| **S13** | App was only ever tested against mock AI path or dummy image. Do real photos produce sensible output? | **NOT VERIFIED** | Test photos `~/Desktop/test_leaf.jpg` and `~/Desktop/test_cow.jpg` are absent from the filesystem. As per audit protocol rules, real photo tests without real image files cannot be executed. |
| **S14** | Full-screen images, dashboard photos, and trader banners heavy on rural mobile data. | **REFUTED** | All 18 Unsplash image URLs use CDN query parameters `w=400&q=70` (thumbnails) or `w=800&q=75` (backgrounds), reducing payloads to ~20-40 KB. Coil 3 automatically caches all responses on disk. All 18 URLs returned HTTP 200 OK. |
| **S15** | Farming/livestock advice shown with no disclaimer, or mock output displayed as real. | **CONFIRMED** | Zero disclaimers exist in Android UI, Web UI, or locale files regarding pesticide safety, chemical dosage, veterinary prescriptions, or financial loan calculation. Mock outputs do show a "Demo data" badge, but real/mock outputs lack legal liability protection. |

---

## 3. Findings Table (Sorted by Severity)

| ID | Severity | Area | Finding | Evidence | Verification Label | Suggested Fix | Effort |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: |
| **F-01** | **BLOCKER** | Android / Net | Release `BASE_URL` targets dead domain `api.kissansevaai.com` | `android/app/build.gradle.kts:46`; `host api.kissansevaai.com` yields `NXDOMAIN` | VERIFIED-BY-RUNNING | Point `BASE_URL` in release to production backend server domain or configure via CI build parameters. | **S** |
| **F-02** | **BLOCKER** | Android / Sec | Default dev credentials hardcoded and pre-filled in release `LoginScreen` | `android/app/src/main/kotlin/com/kissansevaai/feature/auth/LoginScreen.kt:38-39` (`email = "farmer@kissan.com"`, `password = "Password123!"`) | VERIFIED-BY-READING | Change default state variables to empty strings `mutableStateOf("")`. | **S** |
| **F-03** | **BLOCKER** | Backend / Sec | Unsanitized response logging exposes raw JWT tokens in log files | `backend/middleware/requestLogger.js:62` (`responseBody: body`); raw JWTs found in `backend/logs/_login.jsonl` | VERIFIED-BY-READING | Run `sanitizeObj(body)` on `responseBody` before serialization in `requestLogger.js`. | **S** |
| **F-04** | **BLOCKER** | Web / Sec | Unauthenticated public Gemini proxy endpoint | `api/gemini.js:1-85` accepts arbitrary prompts and images with zero authentication, consuming owner's Google API quota | VERIFIED-BY-READING | Add JWT authentication middleware or restrict endpoint access, or route web requests through backend `/api/chat`. | **M** |
| **F-05** | **BLOCKER** | Web / Auth | Web Frontend Login and Signup are fake `setTimeout` mocks | `pages/Login.tsx:32-35` and `pages/Signup.tsx` use `setTimeout` and hardcode `localStorage.setItem('token', 'mock-token')`, never hitting backend | VERIFIED-BY-READING | Implement real Axios/Fetch calls to `POST /api/auth/login` and `POST /api/auth/register` storing real JWT tokens. | **M** |
| **F-06** | **HIGH** | Backend / Sec | Public unauthenticated static file exposure of user uploaded images | `backend/app.js:38` (`app.use('/uploads', express.static(...))`); no token required to access any uploaded image | VERIFIED-BY-READING | Add authentication middleware to `/uploads` or generate signed expiring URLs via cloud object storage (S3/GCS). | **M** |
| **F-07** | **HIGH** | Backend / Sec | Unsafe Multer configuration: missing size limits and unescaped filename | `backend/routes/cropRoutes.js:26-29` concatenates `Date.now() + file.originalname` without path sanitization; missing `limits.fileSize` | VERIFIED-BY-READING | Sanitize filenames with `path.basename` / UUID, enforce 5MB `fileSize` limit, and validate MIME types via `fileFilter`. | **S** |
| **F-08** | **HIGH** | Backend / Sec | Unauthenticated state poisoning on `/api/iot/update` | `backend/routes/iotRoutes.js:9` router has no `protect` middleware; mutates in-memory `liveIoTData` from any HTTP caller | VERIFIED-BY-READING | Add `protect` or IoT API key header authentication to `/api/iot/update`. | **S** |
| **F-09** | **HIGH** | ML / Sec | Insecure PyTorch model deserialization | `ml-service/app.py:56` (`torch.load(MODEL_PATH)`) executes without `weights_only=True`, vulnerable to arbitrary code execution | VERIFIED-BY-READING | Specify `weights_only=True` in `torch.load()`. | **S** |
| **F-10** | **HIGH** | ML / AI | Placeholder labels in ML service break diagnostic accuracy | `ml-service/app.py:28` (`CLASS_NAMES = [f"class_{i}" for i in range(15)]`); backend forwards `class_X` to Gemini prompt | VERIFIED-BY-READING | Create and load real `labels.json` matching the 15 crop disease training classes. | **M** |
| **F-11** | **HIGH** | Product / Legal | Complete absence of medical, agricultural, and financial disclaimers | `grep -rni "disclaimer" android/ locales/ pages/` returns 0 occurrences across entire repository | VERIFIED-BY-RUNNING | Add prominent disclaimers on Crop, Livestock, Loan, and Scheme screens advising expert consultation before taking action. | **M** |
| **F-12** | **HIGH** | Privacy | Violation of India DPDP Act and Play Store Data Safety requirements | User farm photos and livestock data are transmitted to Google Cloud Gemini without user consent notice or deletion path | VERIFIED-BY-READING | Add privacy consent modal during registration, publish a Privacy Policy URL, and implement `DELETE /api/auth/me` user data deletion endpoint. | **L** |
| **F-13** | **HIGH** | AI / Cost | Unbounded Gemini API calls per user (cost exposure) | `backend/controllers/chatController.js` and `cropController.js` lack rate-limiting middleware or per-user daily token quotas | VERIFIED-BY-READING | Apply `express-rate-limit` (e.g. 20 requests/hour/user) on all Gemini-backed routes. | **M** |
| **F-14** | **MEDIUM** | Backend / AI | Prompt injection vulnerabilities via unescaped string interpolation | `fieldExtractionController.js:37` (`Spoken text: "${text}"`), `chatController.js:12` (`Mode: ${mode}`) directly interpolate raw user input | VERIFIED-BY-READING | Use XML delimiter boundaries (e.g. `<user_input>...</user_input>`) and input length caps. | **S** |
| **F-15** | **MEDIUM** | Backend / Quality | Duplicated auth middleware with inconsistent error handling | `middleware/auth.js:24` lacks `return` on 401; `traderRoutes.js` imports `auth.js` while all others import `authMiddleware.js` | VERIFIED-BY-READING | Delete `backend/middleware/auth.js` and consolidate all routes onto `authMiddleware.js`. | **S** |
| **F-16** | **MEDIUM** | Backend / Arch | Unreachable `imageUrl` returned to mobile clients | `livestockController.js:55` constructs `imageUrl` with `req.get('host')` pointing to `127.0.0.1` | VERIFIED-BY-READING | Store relative path `/uploads/:id` or configure a dedicated base asset URL from environment variables. | **S** |
| **F-17** | **MEDIUM** | Android / UI | Hardcoded string in Compose UI | `VoiceFormFillButton.kt:100` uses `Text("Filling fields...", color = Emerald400)` instead of `stringResource` | VERIFIED-BY-READING | Move string literal to `strings.xml` across all 5 locale files. | **S** |
| **F-18** | **MEDIUM** | Git / Hygiene | Large binary checkpoint tracked in Git repository | `ml-service/models/best_vit_tiny_patch16_224.pth` is 22,170,490 bytes (22.1 MB) tracked directly in Git history | VERIFIED-BY-RUNNING | Move large model weights to Git LFS or external cloud artifact storage (Hugging Face / S3). | **M** |
| **F-19** | **MEDIUM** | Test / Quality | Tautological unit test in Android test suite | `ImageCompressorTest.kt:8-12` only asserts that two static constants equal 1280 and 80 | VERIFIED-BY-READING | Implement real bitmap compression testing with downscaling, JPEG byte verification, and EXIF orientation checks. | **M** |
| **F-20** | **LOW** | Android / Code | Dead abstraction in `EngineRouter` | `EngineRouter.kt` is never referenced by ViewModels or production code; claimed device-tier logic is missing | VERIFIED-BY-READING | Wire `EngineRouter` into `CropRepository` or remove the unused class. | **S** |
| **F-21** | **LOW** | Docs | Model name discrepancies across README, Web, and Backend | README says "Gemini 3 Flash", web uses `gemini-1.5-flash`, backend uses `gemini-3-flash-preview` | VERIFIED-BY-READING | Standardize model version across all tiers and update documentation. | **S** |

---

## 4. Claims Ledger Table (I1–I20)

| ID | Claim | Verdict | Evidence |
| :--- | :--- | :---: | :--- |
| **I1** | Built debug and release APKs target SDK 36 (aapt2 output). | **TRUE** | Run `/Users/manthangarg/Library/Android/sdk/build-tools/36.0.0/aapt2 dump badging` on `app-debug.apk` and `app-release-unsigned.apk`. Both output: `compileSdkVersion='36'`, `targetSdkVersion:'36'`, `minSdkVersion:'26'`. |
| **I2** | JWT is encrypted with AndroidKeyStore AES-GCM and there is no androidx.security dependency or import. | **TRUE** | `SecureTokenStorage.kt:17` uses `AndroidKeyStore` with `AES/GCM/NoPadding`. `grep -rn "androidx.security" android/` returns 0 code imports or gradle dependencies. |
| **I3** | Room has 7 cached entities wired through repositories into ViewModels. | **TRUE** | Exactly 7 entities exist in `com.kissansevaai.core.database`: `SchemeEntity`, `LoanProductEntity`, `IoTReadingEntity`, `TraderEntity`, `CropAnalysisEntity`, `MarketPriceEntity`, `ChatMessageEntity`. All 7 have DAOs wired into `KissanDatabase` and tested via `RepositoryCachingTest.kt` (18 passing tests). |
| **I4** | A 401 clears the token and returns to Login (and login/register 401s don't trigger it). | **TRUE** | `UnauthorizedInterceptor.kt:23-31` verifies code == 401, ignores `/login` and `/register`, and calls `dataStore.clearAuth()`. `NavGraph.kt:33-42` observes `isLoggedIn` and pops to `Screen.Login`. Verified by 2 passing tests in `UnauthorizedInterceptorTest.kt`. |
| **I5** | All 5 locales have identical key sets (give the count). | **TRUE** | Web locales (`locales/{en,hi,mr,pa,sa}.ts`): each has exactly 100 identical leaf keys. Android strings (`res/values{,-hi,-mr,-pa,-sa}/strings.xml`): each has exactly 117 identical string keys. 0 untranslated English strings inside non-English files. |
| **I6** | The release build blocks cleartext and its BASE_URL is a real HTTPS URL that resolves. | **FALSE** | Cleartext traffic is blocked, but release `BASE_URL` (`https://api.kissansevaai.com` in `build.gradle.kts:46`) fails DNS resolution with `Host api.kissansevaai.com not found: 3(NXDOMAIN)`. |
| **I7** | Mock AI responses are labelled "mock" and confidence is numeric on every endpoint. | **PARTIAL** | Crop (`geminiVisionService.js:84`) and Livestock (`geminiService.js:50`) return `source: "mock"` and numeric confidence (`0.89`, `0.91`). Chat (`chatController.js`) and Field Extraction (`fieldExtractionController.js`) do not include `source` or `confidence`. |
| **I8** | crop/analyze and livestock/analyze work with real multipart photos and return model-valid JSON in requested language. | **NOT VERIFIED** | Real test images `~/Desktop/test_leaf.jpg` and `~/Desktop/test_cow.jpg` are missing from the filesystem. Mock fallback works, but live real multipart testing could not be executed without real image assets. |
| **I9** | Every Mongoose response carries both _id and id. | **TRUE** | All 8 Mongoose schemas (`User`, `CropAnalysis`, `LivestockAnalysis`, `ChatHistory`, `IoTData`, `Loan`, `LoanRequest`, `Trader`) configure `{ toJSON: { virtuals: true } }`, ensuring `id` virtual getter is serialized alongside `_id`. |
| **I10** | The sync worker updates Room. | **TRUE** | Verified in `DataSyncWorkerTest.kt:93-141` (`doWork syncs data with fake repositories, updates Room contents, and returns Success`). Asserted `roomPrices.size == 1`, `roomTraders.size == 1`, `roomIoT.size == 1` after worker execution. |
| **I11** | Sanskrit STT is disabled with a visible reason and Sanskrit TTS falls back to Hindi with a message. | **TRUE** | Logcat from `PlatformVoiceDiagnosticTest.kt` on emulator: `STT isLanguageSupported('sa'): false` with reason "Voice recognition is not supported in Sanskrit...". TTS fallback verified in `TextToSpeechTest.kt:47-78` (`Sanskrit TTS falls back to Hindi and triggers onFallback callback`). |
| **I12** | ImageCompressor outputs a ≤1280px JPEG at q80 (measured on a real photo). | **NOT VERIFIED** | Real test image `~/Desktop/test_leaf.jpg` is missing. Unit test `ImageCompressorTest.kt` only tests constant definitions. |
| **I13** | The request logger redacts credentials (proved from a log file). | **PARTIAL** | Passwords in request bodies are redacted as `[REDACTED]` in `backend/logs/_login.jsonl`, but response bodies log raw unredacted JWT session tokens because line 62 logs `responseBody: body` directly without `sanitizeObj()`. |
| **I14** | The server refuses to start without JWT_SECRET. | **TRUE** | Tested via shell `JWT_SECRET="" node -e "import('./backend/server.js')"`. Server logs `FATAL ERROR: JWT_SECRET is not defined.` and exits with code 1 (`server.js:4-7`). |
| **I15** | No secret exists in any commit, branch or tag. | **TRUE** | Scanned all 12 commits across all branches and tags with regex patterns for `AIza`, `BEGIN PRIVATE KEY`, `mongodb+srv://`, and `JWT_SECRET=`. 0 secrets found. |
| **I16** | EngineRouter uses device tier and ON_DEVICE falls back to remote. | **PARTIAL** | In `AiEngine.kt:43-55`, `ON_DEVICE` mode explicitly catches exceptions and falls back to `remoteEngine.infer(input)`. However, device tier inspection is completely missing in `EngineRouter`. |
| **I17** | imageUrl values returned to clients are loadable from another device. | **FALSE** | `livestockController.js:55` constructs `http://127.0.0.1:5001/uploads/...`, which is loopback and unreachable from another physical device or phone on the LAN. |
| **I18** | Only images.unsplash.com remains as an image host and every URL returns HTTP 200. | **TRUE** | 18 unique Unsplash URLs across LivePrices, Traders, Signup, Login, and Dashboard tested via HTTP request script. All 18 URLs returned HTTP 200 OK. |
| **I19** | 55 unit tests pass; list how many assert something meaningful. | **TRUE** | 55 unit tests executed and passed (0 failures). 54 tests assert meaningful domain logic, network parsing, and room caching. 1 test (`ImageCompressorTest`) is purely tautological (asserts static constants). |
| **I20** | README claims (models, versions, features) match the code. | **FALSE** | README claims Gemini 3 Flash (web proxy uses 1.5-flash); claims working web auth (web uses fake mock with setTimeout); claims AGP 8.8.2 in older sections (code uses 8.9.1). |

---

## 5. "Could Not Verify" List

1. **Live Multipart Image Diagnostics on Real Agricultural Photos:**
   - **Reason:** Real test photos `~/Desktop/test_leaf.jpg` and `~/Desktop/test_cow.jpg` do not exist on the user's filesystem.
   - **Requirement to Verify:** The user must provide real JPEG/PNG agricultural photos at `~/Desktop/test_leaf.jpg` and `~/Desktop/test_cow.jpg`.
2. **Physical Device Hardware Sensor & BLE Integration:**
   - **Reason:** Only Android virtual emulator `emulator-5554` (API 34) was accessible.
   - **Requirement to Verify:** A physical Android phone connected via ADB to test real hardware camera focus, microphone noise cancellation, and BLE IoT sensor pairing.
3. **End-to-End Production Cloud Hosting & SSL Validation:**
   - **Reason:** Domain `api.kissansevaai.com` is not registered/configured in public DNS.
   - **Requirement to Verify:** A live domain with DNS A/CNAME records and valid TLS certificates pointing to an active deployment.

---

## 6. Top 10 Fixes (Grouped in Small Independent Batches)

### Batch 1: Immediate Security & Startup Fixes (Zero Risk, High Impact)
1. **Fix F-02 (Android Dev Credentials):** In `LoginScreen.kt:38-39`, reset default mutable state values to `""` so release APK does not pre-fill test login credentials.
2. **Fix F-03 (JWT Token Leak in Logs):** In `requestLogger.js:62`, replace `responseBody: body` with `responseBody: sanitizeObj(body)` to stop writing plaintext JWTs to disk.
3. **Fix F-08 (Secure IoT Update Route):** Add `protect` middleware to `POST /api/iot/update` in `backend/routes/iotRoutes.js:9`.
4. **Fix F-09 (Safe PyTorch Deserialization):** Add `weights_only=True` to `torch.load()` in `ml-service/app.py:56`.

### Batch 2: Core Connectivity & Web Integration
5. **Fix F-01 (Release Base URL):** Update `BASE_URL` in `android/app/build.gradle.kts:46` to a valid staging/production endpoint or build-time environment variable.
6. **Fix F-04 & F-05 (Web Auth & Secure Gemini Proxy):** Connect `pages/Login.tsx` and `Signup.tsx` to real backend `/api/auth/login`, and remove or secure the unauthenticated `api/gemini.js` endpoint.
7. **Fix F-07 (Multer Upload Hardening):** Add 5MB `fileSize` limit and strict image MIME-type filter (`image/jpeg`, `image/png`, `image/webp`) in `backend/routes/cropRoutes.js` and `livestockRoutes.js`.

### Batch 3: Compliance, ML & AI Guardrails
8. **Fix F-10 (ML Service Label Mapping):** Add real `labels.json` to `ml-service/` mapping class indices 0..14 to actual crop names and diseases.
9. **Fix F-11 & F-12 (Safety Disclaimers & DPDP Consent):** Add user-facing medical/chemical advisory disclaimers and privacy consent notices across Android and Web apps.
10. **Fix F-13 (Rate Limiting on AI Endpoints):** Implement `express-rate-limit` on all Gemini-backed API endpoints to protect against cost abuse.

---

## 7. Tasks Only the Human Can Perform

1. **Domain Registration & DNS Configuration:** Register `kissansevaai.com` (or your chosen domain) and configure DNS A/CNAME records pointing to your production server for `api.kissansevaai.com`.
2. **Google Play Console & Data Safety Declaration:** Create developer account, generate release upload signing keystore, create store listing assets, and complete the Data Safety questionnaire (camera, microphone, image data).
3. **Legal Disclaimer & Privacy Policy Drafting:** Draft legally compliant Terms of Service and Privacy Policy covering agricultural/pesticide advice disclaimers and India DPDP Act compliance.
4. **Real Agricultural Test Photos:** Take real photos of diseased crop leaves and livestock symptoms (`test_leaf.jpg`, `test_cow.jpg`) to calibrate vision analysis accuracy.
5. **Gemini API Billing & Quota Caps:** Configure budget alerts and monthly spending caps in Google Cloud Console / Google AI Studio for `GEMINI_API_KEY`.
