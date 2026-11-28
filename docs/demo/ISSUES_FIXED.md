# KissanSevaAI — Issues Discovered & Fixed

1. **Cold-Restart Auth Reset (`AuthViewModel.kt`, `NavGraph.kt`)**
   - **Root Cause:** `AuthViewModel.isLoggedIn` initialized its `StateFlow<Boolean>` to `false` while `DataStore` loaded the saved JWT asynchronously from disk. `KissanNavGraph` locked `startDestination = Screen.Login` on Frame 1 before `DataStore` emitted `true`, causing cold restarts (including offline restarts) to land on `LoginScreen`.
   - **Fix:** Added nullable `isLoggedInState: StateFlow<Boolean?>` starting at `null`, rendering a clean `SurfaceBg` loading indicator until `DataStore` resolves, then mounting `NavHost` at `Screen.Dashboard` or `Screen.Login`.

2. **Dashboard "Today's Mandi Rates" Duplicate Apple Rows (`DashboardViewModel.kt`)**
   - **Root Cause:** `DashboardViewModel.loadData()` took `res.data.take(4)` on the unfiltered alphabetical market feed, displaying four `Apple` rows (`3 × Mukkom Market, Keralam` + `1 × Kolhapur`) on a Maharashtra farmer's Dashboard.
   - **Fix:** Sorted listings prioritizing the farmer's saved state and `primaryCrops`, then applied `distinctBy { it.crop.trim().lowercase() }.take(4)`.

3. **Missing Observation Dates on Live Market Prices (`Models.kt`, `LivePricesScreen.kt`)**
   - **Root Cause:** Backend AGMARKNET records returned `arrivalDate` (`"27/11/2026"`), which `MarketPrice` did not deserialize and `LivePricesScreen` did not render.
   - **Fix:** Added `arrivalDate: String? = null` to `MarketPrice` and rendered `Obs: <date>` cleanly under each mandi location (`Stage7DemoRegressionTest` verifies deserialization).

4. **Missing Task Edit UI & Backend Field Updates in Smart Crop Planner (`plannerController.js`, `syncController.js`, `Models.kt`, `CropPlannerEntities.kt`, `PlannerRepository.kt`, `CropPlannerScreen.kt`)**
   - **Root Cause:** `PATCH /api/planner/tasks/:taskId` and `syncController.js` only accepted `status` and `reminderEnabled`, and `CropPlannerScreen` lacked an Edit action/dialog on scheduled tasks.
   - **Fix:** Extended `updatePlannerTask` and offline `planner_task_update` sync to support `title`, `description`, `scheduledDate`, and `reminderEnabled`; added `PlannerTaskDao.updateDetails`, `PlannerRepository.updateTaskDetails`, and an Edit button + dialog on every task card (`stage7DemoFixes.test.js` & `Stage7DemoRegressionTest` pass).

5. **Squeezed `"Notes: Local"` Badge on Crop Result Header (`CropDiseaseScreen.kt`)**
   - **Root Cause:** Inside a constrained `Row`, the third status pill wrapped vertically (`Note / s: / Loca / l`).
   - **Fix:** Switched the status badge container to `FlowRow` with `maxLines = 1, softWrap = false` on badge labels.

6. **English-Only Scheme Cards & Tabs in Hindi Mode (`schemeCatalogService.js`, `Models.kt`, `SchemesScreen.kt`)**
   - **Root Cause:** `SchemesScreen` hardcoded `"Verified Schemes"`, `"Eligibility Check"`, `"All States"`, `"Saved"`, `"Explain"`, and `"Portal"`, and `VERIFIED_SCHEMES` had only English strings.
   - **Fix:** Added `nameHi`, `categoryHi`, `descriptionHi`, and `benefitsHi` across all 10 schemes in `schemeCatalogService.js` (`stage7DemoFixes.test.js` verifies all 10 schemes), added matching fields to `Scheme` (`Stage7DemoRegressionTest`), and localized `SchemesScreen` labels/cards for `lang.startsWith("hi")`.

7. **Chatbot Quota Fallback Grounding & Markdown Display (`ChatRepository.kt`, `ChatbotScreen.kt`)**
   - **Root Cause:** When Gemini returned HTTP `429`/`503` with `groundedContextSummary`, `ChatRepository` only saved the raw error string, and `ChatbotScreen` rendered `**bold**` / `* ` asterisks literally.
   - **Fix:** Parsed `groundedContextSummary` & `groundedSources` from HTTP error JSON in `ChatRepository` so live Mandi/Weather context displays beneath the honest quota notice, and added `formatChatMarkdown` (`AnnotatedString`) in `ChatbotScreen`.
