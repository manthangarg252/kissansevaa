package com.kissansevaai

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kissansevaai.core.ai.SpeechToText
import com.kissansevaai.core.database.CropPlanEntity
import com.kissansevaai.core.database.DatabaseModule
import com.kissansevaai.core.database.FarmProfileEntity
import com.kissansevaai.core.database.KissanDatabase
import com.kissansevaai.core.location.DetectedLocation
import com.kissansevaai.core.location.SavedFarmLocation
import com.kissansevaai.ui.component.FarmLocationConfirmationDialog
import com.kissansevaai.ui.component.VoiceInputField
import com.kissansevaai.ui.theme.KissanSevaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Stage4ComposeAndMigrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class StubSpeechToText(private val available: Boolean = true) : SpeechToText {
        override val isRecognitionAvailable: Boolean get() = available
        override fun isLanguageSupported(langCode: String): Boolean = available && !langCode.startsWith("sa")
        override fun getUnsupportedReason(langCode: String): String? =
            if (!available) "Speech recognition service unavailable on device" else null
        override fun startListening(
            langCode: String,
            onResult: (String) -> Unit,
            onError: (String) -> Unit,
            onReadyForSpeech: (() -> Unit)?,
            onPartialResult: ((String) -> Unit)?,
            onCancelled: (() -> Unit)?,
        ) {
            onReadyForSpeech?.invoke()
            onPartialResult?.invoke("gehun")
            onResult("gehun")
        }
        override fun stopListening() {}
        override fun destroy() {}
    }

    @Test
    fun realRoomMigration4To5PreservesPlansAndIsolatesMultiAccountFarmLocations() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test_migration_v4_v5.db"
            val dbFile: File = context.getDatabasePath(dbName)
            if (dbFile.exists()) dbFile.delete()

            // Create authentic v4 SQLite schema before opening with Room v5
            val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            sqlite.version = 4
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS schemes (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, description TEXT NOT NULL, eligibility TEXT NOT NULL, benefits TEXT NOT NULL, link TEXT, phone TEXT)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS loan_products (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, description TEXT NOT NULL, benefits TEXT NOT NULL, eligibility TEXT NOT NULL, link TEXT, provider TEXT NOT NULL, interestRate TEXT NOT NULL, maxAmount TEXT NOT NULL, tenure TEXT NOT NULL)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS traders (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, location TEXT NOT NULL, dealsIn TEXT NOT NULL, phone TEXT NOT NULL, rating REAL NOT NULL, reviewCount INTEGER NOT NULL, imageUrl TEXT)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS market_prices (id TEXT NOT NULL PRIMARY KEY, crop TEXT NOT NULL, price REAL NOT NULL, modalPrice REAL, minPrice REAL, maxPrice REAL, unit TEXT NOT NULL, market TEXT NOT NULL, state TEXT NOT NULL, trend TEXT NOT NULL, updatedAt TEXT, lastUpdated TEXT, category TEXT, sourceBadge TEXT)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS crop_history (id TEXT NOT NULL PRIMARY KEY, source TEXT, cropName TEXT, diseaseName TEXT NOT NULL, confidence REAL, severity TEXT, summary TEXT, organicTreatment TEXT NOT NULL, chemicalTreatment TEXT NOT NULL, prevention TEXT NOT NULL, language TEXT, imagePath TEXT, createdAt TEXT, topCosineSimilarity REAL, neighborAgreement REAL, needsReview INTEGER NOT NULL DEFAULT 0, treatmentWithheld INTEGER NOT NULL DEFAULT 0, warning TEXT, confidenceType TEXT, predictionSource TEXT, advisorySource TEXT, userId TEXT NOT NULL DEFAULT '', secondOpinionSource TEXT, secondOpinionCropName TEXT, secondOpinionDiseaseName TEXT, secondOpinionConfidence REAL, secondOpinionSummary TEXT, reconciliationStatus TEXT, reconciliationNote TEXT)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS chat_messages (id TEXT NOT NULL PRIMARY KEY, sender TEXT NOT NULL, text TEXT NOT NULL, timestamp INTEGER NOT NULL, userId TEXT NOT NULL DEFAULT '', sessionId TEXT NOT NULL DEFAULT 'default', category TEXT NOT NULL DEFAULT 'general', groundedSources TEXT NOT NULL DEFAULT '[]', aiAvailable INTEGER NOT NULL DEFAULT 1)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS iot_readings (id TEXT NOT NULL PRIMARY KEY, temperature REAL, humidity REAL, soilMoisture REAL, phLevel REAL, timestamp TEXT)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS livestock_history (id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, animalType TEXT NOT NULL, conditionName TEXT, confidence REAL, source TEXT, severity TEXT, urgentVetEscalation INTEGER NOT NULL, escalationReason TEXT, prescriptionsWithheld INTEGER NOT NULL, safetyNotice TEXT, symptoms TEXT NOT NULL, immediateAction TEXT, longTermCare TEXT, vetConsultationAdvice TEXT, imagePath TEXT, aiAvailable INTEGER NOT NULL, serviceStatus TEXT, createdAt TEXT)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS weather_cache (locationKey TEXT NOT NULL PRIMARY KEY, locationName TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, provider TEXT NOT NULL, temperatureC REAL NOT NULL, humidityPct REAL NOT NULL, windSpeedKmh REAL NOT NULL, precipitationMm REAL NOT NULL, conditionLabel TEXT NOT NULL, advisoriesJson TEXT NOT NULL, dailyJson TEXT NOT NULL, aiSummary TEXT, fetchedAt TEXT NOT NULL, expiresAt TEXT NOT NULL, isStale INTEGER NOT NULL)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS crop_plans (id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, cropName TEXT NOT NULL, variety TEXT NOT NULL, stage TEXT NOT NULL, soilType TEXT NOT NULL, irrigationType TEXT NOT NULL, farmSizeAcres REAL NOT NULL, locationName TEXT NOT NULL, startDate TEXT NOT NULL, status TEXT NOT NULL, generationSource TEXT NOT NULL, aiAdvisoryNote TEXT, updatedAt TEXT NOT NULL, pendingSync INTEGER NOT NULL)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS planner_tasks (id TEXT NOT NULL PRIMARY KEY, planId TEXT NOT NULL, userId TEXT NOT NULL, dayOffset INTEGER NOT NULL, scheduledDate TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, category TEXT NOT NULL, priority TEXT NOT NULL, status TEXT NOT NULL, completedAt TEXT, weatherAdjusted INTEGER NOT NULL, weatherReason TEXT NOT NULL, reminderEnabled INTEGER NOT NULL, pendingSync INTEGER NOT NULL)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS saved_schemes (id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, schemeId TEXT NOT NULL, status TEXT NOT NULL, notes TEXT NOT NULL, savedAt TEXT NOT NULL, pendingSync INTEGER NOT NULL)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS market_favorites (id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, crop TEXT NOT NULL, market TEXT NOT NULL, state TEXT NOT NULL, category TEXT NOT NULL, createdAt TEXT NOT NULL, pendingSync INTEGER NOT NULL)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS sync_operations (operationId TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, opType TEXT NOT NULL, payloadJson TEXT NOT NULL, status TEXT NOT NULL, retryCount INTEGER NOT NULL, lastError TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            sqlite.execSQL("INSERT INTO crop_plans VALUES ('v4_p1', 'farmer_a', 'Cotton', '', 'vegetative', 'Black', 'Drip', 3.0, 'Jalgaon', '2026-11-20', 'active', 'rules_only', NULL, '2026-11-20', 0)")
            sqlite.close()

            val db1 = Room.databaseBuilder(context, KissanDatabase::class.java, dbName)
                .addMigrations(DatabaseModule.MIGRATION_1_2, DatabaseModule.MIGRATION_2_3, DatabaseModule.MIGRATION_3_4, DatabaseModule.MIGRATION_4_5)
                .build()

            val preservedPlans = db1.cropPlanDao().getPlans("farmer_a").first()
            assertEquals(1, preservedPlans.size)
            assertEquals("Cotton", preservedPlans.first().cropName)
            assertEquals("", preservedPlans.first().sowingDate)

            // Write new v5 FarmProfile for farmer_a & farmer_b and reopen DB to verify restart survival + multi-account isolation
            db1.farmProfileDao().upsert(
                FarmProfileEntity(
                    userId = "farmer_a",
                    farmerName = "Ramesh",
                    farmName = "Satpura Plot",
                    district = "Ludhiana",
                    state = "Punjab",
                    latitude = 30.901,
                    longitude = 75.8573,
                    locationSource = "gps_precise",
                )
            )
            db1.cropPlanDao().insert(
                CropPlanEntity(
                    id = "v5_p2", userId = "farmer_a", cropName = "Wheat",
                    sowingDate = "2026-11-10", customTask = "Check zinc", locationName = "Ludhiana, Punjab",
                )
            )
            db1.close()

            // Reopen database simulating process restart
            val db2 = Room.databaseBuilder(context, KissanDatabase::class.java, dbName)
                .addMigrations(DatabaseModule.MIGRATION_1_2, DatabaseModule.MIGRATION_2_3, DatabaseModule.MIGRATION_3_4, DatabaseModule.MIGRATION_4_5)
                .build()

            val loadedA = db2.farmProfileDao().getProfileOnce("farmer_a")
            assertNotNull(loadedA)
            assertEquals("Ludhiana", loadedA!!.district)
            assertNull(db2.farmProfileDao().getProfileOnce("farmer_b"))
            val restartedPlan = db2.cropPlanDao().getPlans("farmer_a").first().find { it.id == "v5_p2" }
            assertEquals("2026-11-10", restartedPlan?.sowingDate)
            db2.close()
            dbFile.delete()
        }
    }

    @Test
    fun farmLocationConfirmationDialogShowsPromptConfirmChangeAndRetry() {
        var confirmedLocation: SavedFarmLocation? = null
        var retried = false
        val detected = DetectedLocation(
            latitude = 30.901,
            longitude = 75.8573,
            accuracyMeters = 15f,
            isPrecise = true,
            locationSource = "gps_precise",
            village = "Bassi",
            district = "", // Missing district from geocoder must not be invented
            state = "Punjab",
            geocoderFailed = false,
        )
        composeRule.setContent {
            KissanSevaTheme {
                FarmLocationConfirmationDialog(
                    detected = detected,
                    existingSaved = SavedFarmLocation(userId = "u1", district = "Indore", state = "Madhya Pradesh", locationUpdatedAt = "2026-11-25"),
                    initialManualMode = false,
                    lang = "en",
                    speechToText = StubSpeechToText(),
                    onConfirm = { confirmedLocation = it },
                    onRetryDetection = { retried = true },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Use this location for your farm?").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm").assertIsDisplayed()
        composeRule.onNodeWithText("Change Location").assertIsDisplayed()
        composeRule.onNodeWithText("Retry Detection").assertIsDisplayed()

        composeRule.onNodeWithText("Confirm").performClick()
        assertNotNull(confirmedLocation)
        assertEquals("Punjab", confirmedLocation!!.state)
        assertEquals("", confirmedLocation!!.district)

        composeRule.onNodeWithText("Retry Detection").performClick()
        assertTrue(retried)
    }

    @Test
    fun voiceInputFieldHandlesUnavailableServiceWithoutCrash() {
        composeRule.setContent {
            KissanSevaTheme {
                VoiceInputField(
                    value = "",
                    onValueChange = {},
                    label = "Crop Name",
                    fieldKey = "cropName",
                    lang = "en",
                    speechToText = StubSpeechToText(available = false),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Speak to fill Crop Name").assertIsDisplayed()
    }
}
