# Stage 6 — Android Cloud Release Configuration & Security

## 1. Gradle Cloud Endpoint Injection (`KISSAN_PROD_API_URL`)

[android/app/build.gradle.kts](../../android/app/build.gradle.kts) reads `KISSAN_PROD_API_URL` from either Gradle property `-PKISSAN_PROD_API_URL=...` or environment variable `KISSAN_PROD_API_URL` when configuring the `release` build type (default fallback placeholder `https://api.kissansevaai.com`):

```bash
export JAVA_HOME=/Users/manthangarg/.jdks/jdk-21.0.12.1+1/Contents/Home
./android/gradlew -p android assembleRelease \
  -PKISSAN_PROD_API_URL=https://api.yourdomain.com
```

Gradle configuration fails immediately if a release URL:
- Does not begin with `https://`
- Contains `localhost`, `127.0.0.1`, `10.0.2.2`, `192.168.*`, or `10.x.x.x`

Debug builds remain unchanged (`http://10.0.2.2:5050` for emulator, `http://127.0.0.1:5050` for ADB USB debugging, and editable server settings in debug UI).

## 2. Runtime Release Guards (`CloudUrlValidator`)

1. [CloudUrlValidator.kt](../../android/app/src/main/kotlin/com/kissansevaai/core/network/CloudUrlValidator.kt) validates production URLs (HTTPS required; rejects loopback, emulator gateway, `.local`, and RFC-1918 private IPs).
2. [NetworkModule.kt](../../android/app/src/main/kotlin/com/kissansevaai/core/network/NetworkModule.kt):
   - Attaches `hostFallbackInterceptor` (`127.0.0.1`, `10.0.2.2`, `192.168.1.7`) **only** when `BuildConfig.DEBUG` is `true`.
   - Ignores `dataStore.baseUrl` in release (`!BuildConfig.DEBUG`) so leftover local URL preferences can never redirect release traffic.
   - Uses a `12s` connect timeout in release (vs `4s` in debug) to tolerate mobile cellular TLS handshakes.
3. [AuthInterceptor.kt](../../android/app/src/main/kotlin/com/kissansevaai/core/network/AuthInterceptor.kt): performs URL rewriting only when `BuildConfig.DEBUG` is `true`.
4. [LoginScreen.kt](../../android/app/src/main/kotlin/com/kissansevaai/feature/auth/LoginScreen.kt) and [SettingsScreen.kt](../../android/app/src/main/kotlin/com/kissansevaai/feature/settings/SettingsScreen.kt): hide the local `Server Settings` dialog and `Base URL` editor in release builds (`if (BuildConfig.DEBUG)`).
5. [network_security_config.xml](../../android/app/src/main/res/xml/network_security_config.xml) enforces `cleartextTrafficPermitted="false"` with system CA trust anchors (`Let's Encrypt` via Caddy works out of the box on Android 8+ / `minSdk = 26`).

## 3. Signing Notes Before Play Store / Farmer Distribution

`assembleRelease` validates release compilation, R8/ProGuard shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`), and `BuildConfig.BASE_URL` cleanly, producing an **unsigned** `app-release-unsigned.apk` until you attach your Android release keystore (`signingConfigs.release` via `local.properties` or CI secrets — never commit `.jks`/`.keystore` files).
