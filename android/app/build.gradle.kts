plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kissansevaai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kissansevaai"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Configurable backend base URL
        buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:5000\"")

        // Authorized Sarvam farmer helpline configuration (blank if unconfigured)
        val sarvamHelplineNumber = (project.findProperty("SARVAM_HELPLINE_NUMBER") as? String)?.trim().orEmpty()
        buildConfigField(
            "String",
            "SARVAM_HELPLINE_NUMBER",
            "\"${sarvamHelplineNumber.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )

        // Room schema export
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:5050\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release signing configured via gradle.properties or CI env vars
            // Pass -PKISSAN_PROD_API_URL=https://api.yourdomain.com or set KISSAN_PROD_API_URL env var
            val rawProdUrl = ((project.findProperty("KISSAN_PROD_API_URL") as? String)
                ?: System.getenv("KISSAN_PROD_API_URL")
                ?: "https://api.kissansevaai.com")
                .trim()
                .trimEnd('/')
            val lowerProdUrl = rawProdUrl.lowercase()
            require(lowerProdUrl.startsWith("https://")) {
                "Release KISSAN_PROD_API_URL must use https:// (got '$rawProdUrl')"
            }
            require(
                !lowerProdUrl.contains("localhost") &&
                    !lowerProdUrl.contains("127.0.0.1") &&
                    !lowerProdUrl.contains("10.0.2.2") &&
                    !lowerProdUrl.contains("192.168.") &&
                    !Regex("""https://10\.\d+\.\d+\.\d+""").containsMatchIn(lowerProdUrl)
            ) {
                "Release KISSAN_PROD_API_URL must not target a local or private dev address (got '$rawProdUrl')"
            }
            buildConfigField(
                "String",
                "BASE_URL",
                "\"${rawProdUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // AndroidX Core
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(libs.appcompat)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Location (Google Play Services FusedLocationProviderClient)
    implementation(libs.play.services.location)


    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.core.testing)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
