# KissanSevaAI Stage 5 — Simple Sarvam AI Phone Helpline UI

**Classification Highlights:**
- Configuration entry `SARVAM_HELPLINE_NUMBER` & country-code validator (`SarvamHelplineConfig`): **VERIFIED BY AUTOMATED TEST** (`Stage5HelplineAndSchemaUnitTest`)
- Dashboard UI card (`Talk to Our AI Farming Assistant`, description, basic-phone badge, `+917965861355`, `Call Now`, `Copy Number`, Hindi localization, `>= 48dp` touch targets, configuration-required guard): **VERIFIED ON EMULATOR** (`Stage5HelplineUiTest` `2/2` + interactive screenshots `01c_helpline_polished.png`, `02_helpline_copied.png`)
- `Intent.ACTION_DIAL` opening Android Dialer prefilled with `+91 79 6586 1355` (no `CALL_PHONE` permission, no auto-dialed call): **VERIFIED ON EMULATOR** (`03_dialer_prefilled.png` + instrumentation `Intent` assertion)
- Live end-to-end voice conversation on physical handset dialing `+917965861355`: **IMPLEMENTED BUT UNVERIFIED** (requires user/tester placing a live phone call without automation spending carrier minutes)

---

## 1. Architecture & Scope Compliance

- Zero backend Sarvam integration changes: no webhooks configured, no SIP/IVR added, no call recording, no Sarvam API credentials required, and existing unrelated backend files left intact.
- **Configuration**:
  - [android/gradle.properties](file:///Users/manthangarg/Desktop/kissansevaai-main/android/gradle.properties): `SARVAM_HELPLINE_NUMBER=+917965861355` (read directly from the user-authorized helpline screenshot).
  - [android/app/build.gradle.kts](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/build.gradle.kts): Exposes `BuildConfig.SARVAM_HELPLINE_NUMBER` without inventing any default fallback number.
  - [HelplineConfig.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/core/common/HelplineConfig.kt): `SarvamHelplineConfig.resolve()` strips formatting spaces/dashes, requires E.164 country code (`^\+[1-9]\d{9,14}$` and 10 digits after `+91`), and enters an explicit `isConfigured = false` configuration-required state if unset or invalid.

## 2. Dashboard UI & Localization

Implemented in [HelplineCard.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/ui/component/HelplineCard.kt), embedded in [DashboardScreen.kt](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/kotlin/com/kissansevaai/feature/dashboard/DashboardScreen.kt), and localized in [strings.xml](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/res/values/strings.xml) and [values-hi/strings.xml](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/res/values-hi/strings.xml):

- Heading: **`Talk to Our AI Farming Assistant`** / **`हमारे AI कृषि सहायक से बात करें`**
- Description: **`Get farming assistance by calling our AI helpline. Available on smartphones and basic mobile phones.`** (plus localized Hindi equivalent and basic keypad phone badge).
- Configured Display Number: **`+917965861355`**
- **`Call Now`** (`heightIn(min = 48.dp)`): Launches `Intent(Intent.ACTION_DIAL, Uri.parse("tel:+917965861355"))` inside an `ActivityNotFoundException` safety wrapper; verified opening Google Dialer with `+91 79 6586 1355` prefilled on `emulator-5554` (`03_dialer_prefilled.png`). `CALL_PHONE` permission is **not** requested in [AndroidManifest.xml](file:///Users/manthangarg/Desktop/kissansevaai-main/android/app/src/main/AndroidManifest.xml).
- **`Copy Number`** (`heightIn(min = 48.dp)`): Copies `+917965861355` to system `ClipboardManager` and renders `"Helpline number copied: +917965861355"` (`02_helpline_copied.png`).
