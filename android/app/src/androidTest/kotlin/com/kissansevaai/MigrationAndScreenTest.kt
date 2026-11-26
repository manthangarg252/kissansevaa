package com.kissansevaai

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kissansevaai.core.database.CropAnalysisEntity
import com.kissansevaai.core.database.CropPlanEntity
import com.kissansevaai.core.database.DatabaseModule
import com.kissansevaai.core.database.KissanDatabase
import com.kissansevaai.core.database.PlannerTaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MigrationAndScreenTest {

    @Test
    fun realRoomMigration3To4PreservesExistingCropDataAndOpensCleanly() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test_migration_v3_v4.db"
            val dbFile: File = context.getDatabasePath(dbName)
            if (dbFile.exists()) dbFile.delete()

            // Construct authentic v3 schema SQLite file directly before opening with Room v4
            val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            sqlite.version = 3
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS schemes (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, description TEXT NOT NULL, eligibility TEXT NOT NULL, benefits TEXT NOT NULL, link TEXT, phone TEXT)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS loan_products (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, description TEXT NOT NULL, benefits TEXT NOT NULL, eligibility TEXT NOT NULL, link TEXT, provider TEXT NOT NULL, interestRate TEXT NOT NULL, maxAmount TEXT NOT NULL, tenure TEXT NOT NULL)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS traders (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, location TEXT NOT NULL, dealsIn TEXT NOT NULL, phone TEXT NOT NULL, rating REAL NOT NULL, reviewCount INTEGER NOT NULL, imageUrl TEXT)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS market_prices (id TEXT NOT NULL PRIMARY KEY, crop TEXT NOT NULL, price REAL NOT NULL, modalPrice REAL, minPrice REAL, maxPrice REAL, unit TEXT NOT NULL, market TEXT NOT NULL, state TEXT NOT NULL, trend TEXT NOT NULL, updatedAt TEXT, lastUpdated TEXT)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS crop_history (id TEXT NOT NULL PRIMARY KEY, source TEXT, cropName TEXT, diseaseName TEXT NOT NULL, confidence REAL, severity TEXT, summary TEXT, organicTreatment TEXT NOT NULL, chemicalTreatment TEXT NOT NULL, prevention TEXT NOT NULL, language TEXT, imagePath TEXT, createdAt TEXT, topCosineSimilarity REAL, neighborAgreement REAL, needsReview INTEGER NOT NULL DEFAULT 0, treatmentWithheld INTEGER NOT NULL DEFAULT 0, warning TEXT, confidenceType TEXT, predictionSource TEXT, advisorySource TEXT)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS chat_messages (id TEXT NOT NULL PRIMARY KEY, sender TEXT NOT NULL, text TEXT NOT NULL, timestamp INTEGER NOT NULL)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS iot_readings (id TEXT NOT NULL PRIMARY KEY, temperature REAL, humidity REAL, soilMoisture REAL, phLevel REAL, timestamp TEXT)")
            sqlite.execSQL("INSERT INTO crop_history (id, source, cropName, diseaseName, organicTreatment, chemicalTreatment, prevention, needsReview, treatmentWithheld) VALUES ('legacy_1', 'dinov2', 'Wheat', 'Yellow Rust', '[]', '[]', '[]', 1, 1)")
            sqlite.close()

            val roomDb = Room.databaseBuilder(context, KissanDatabase::class.java, dbName)
                .addMigrations(
                    DatabaseModule.MIGRATION_1_2,
                    DatabaseModule.MIGRATION_2_3,
                    DatabaseModule.MIGRATION_3_4,
                    DatabaseModule.MIGRATION_4_5,
                )
                .build()

            val migrated = roomDb.cropAnalysisDao().getAllHistory().first()
            assertEquals(1, migrated.size)
            assertEquals("Yellow Rust", migrated.first().diseaseName)
            assertTrue(migrated.first().needsReview)

            // Verify new v4 tables (CropPlan + PlannerTask) write & read on real SQLite
            roomDb.cropPlanDao().insert(
                CropPlanEntity(id = "plan_m1", userId = "u1", cropName = "Wheat", locationName = "Nagpur")
            )
            roomDb.plannerTaskDao().insertAll(
                listOf(PlannerTaskEntity(id = "task_m1", planId = "plan_m1", userId = "u1", title = "Scout Wheat"))
            )
            assertEquals(1, roomDb.plannerTaskDao().getPendingTasks("u1").first().size)

            roomDb.close()
            dbFile.delete()
        }
    }
}
