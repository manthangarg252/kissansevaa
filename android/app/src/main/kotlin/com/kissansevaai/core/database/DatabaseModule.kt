package com.kissansevaai.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Preserved from Phase 1
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE crop_history ADD COLUMN topCosineSimilarity REAL")
            db.execSQL("ALTER TABLE crop_history ADD COLUMN neighborAgreement REAL")
            db.execSQL("ALTER TABLE crop_history ADD COLUMN needsReview INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE crop_history ADD COLUMN treatmentWithheld INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE crop_history ADD COLUMN warning TEXT")
            db.execSQL("ALTER TABLE crop_history ADD COLUMN confidenceType TEXT")
            db.execSQL("ALTER TABLE crop_history ADD COLUMN predictionSource TEXT")
            db.execSQL("ALTER TABLE crop_history ADD COLUMN advisorySource TEXT")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Extend market_prices safely
            db.execSQL("ALTER TABLE market_prices ADD COLUMN category TEXT")
            db.execSQL("ALTER TABLE market_prices ADD COLUMN sourceBadge TEXT")

            // Extend crop_history with user isolation + Stage 1 second-opinion reconciliation
            db.execSQL("ALTER TABLE crop_history ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE crop_history ADD COLUMN secondOpinionSource TEXT")
            db.execSQL("ALTER TABLE crop_history ADD COLUMN secondOpinionCropName TEXT")
            db.execSQL("ALTER TABLE crop_history ADD COLUMN secondOpinionDiseaseName TEXT")
            db.execSQL("ALTER TABLE crop_history ADD COLUMN secondOpinionConfidence REAL")
            db.execSQL("ALTER TABLE crop_history ADD COLUMN secondOpinionSummary TEXT")
            db.execSQL("ALTER TABLE crop_history ADD COLUMN reconciliationStatus TEXT")
            db.execSQL("ALTER TABLE crop_history ADD COLUMN reconciliationNote TEXT")

            // Extend chat_messages with user/session scoping & grounding metadata
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN sessionId TEXT NOT NULL DEFAULT 'default'")
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN category TEXT NOT NULL DEFAULT 'general'")
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN groundedSources TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN aiAvailable INTEGER NOT NULL DEFAULT 1")

            // Livestock clinical history table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS livestock_history (
                    id TEXT NOT NULL PRIMARY KEY,
                    userId TEXT NOT NULL,
                    animalType TEXT NOT NULL,
                    conditionName TEXT,
                    confidence REAL,
                    source TEXT,
                    severity TEXT,
                    urgentVetEscalation INTEGER NOT NULL,
                    escalationReason TEXT,
                    prescriptionsWithheld INTEGER NOT NULL,
                    safetyNotice TEXT,
                    symptoms TEXT NOT NULL,
                    immediateAction TEXT,
                    longTermCare TEXT,
                    vetConsultationAdvice TEXT,
                    imagePath TEXT,
                    aiAvailable INTEGER NOT NULL,
                    serviceStatus TEXT,
                    createdAt TEXT
                )
                """.trimIndent()
            )

            // Weather cache table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS weather_cache (
                    locationKey TEXT NOT NULL PRIMARY KEY,
                    locationName TEXT NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    provider TEXT NOT NULL,
                    temperatureC REAL NOT NULL,
                    humidityPct REAL NOT NULL,
                    windSpeedKmh REAL NOT NULL,
                    precipitationMm REAL NOT NULL,
                    conditionLabel TEXT NOT NULL,
                    advisoriesJson TEXT NOT NULL,
                    dailyJson TEXT NOT NULL,
                    aiSummary TEXT,
                    fetchedAt TEXT NOT NULL,
                    expiresAt TEXT NOT NULL,
                    isStale INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // Smart crop planner tables
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS crop_plans (
                    id TEXT NOT NULL PRIMARY KEY,
                    userId TEXT NOT NULL,
                    cropName TEXT NOT NULL,
                    variety TEXT NOT NULL,
                    stage TEXT NOT NULL,
                    soilType TEXT NOT NULL,
                    irrigationType TEXT NOT NULL,
                    farmSizeAcres REAL NOT NULL,
                    locationName TEXT NOT NULL,
                    startDate TEXT NOT NULL,
                    status TEXT NOT NULL,
                    generationSource TEXT NOT NULL,
                    aiAdvisoryNote TEXT,
                    updatedAt TEXT NOT NULL,
                    pendingSync INTEGER NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS planner_tasks (
                    id TEXT NOT NULL PRIMARY KEY,
                    planId TEXT NOT NULL,
                    userId TEXT NOT NULL,
                    dayOffset INTEGER NOT NULL,
                    scheduledDate TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL,
                    category TEXT NOT NULL,
                    priority TEXT NOT NULL,
                    status TEXT NOT NULL,
                    completedAt TEXT,
                    weatherAdjusted INTEGER NOT NULL,
                    weatherReason TEXT NOT NULL,
                    reminderEnabled INTEGER NOT NULL,
                    pendingSync INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // Saved schemes table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS saved_schemes (
                    id TEXT NOT NULL PRIMARY KEY,
                    userId TEXT NOT NULL,
                    schemeId TEXT NOT NULL,
                    status TEXT NOT NULL,
                    notes TEXT NOT NULL,
                    savedAt TEXT NOT NULL,
                    pendingSync INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // Market favorites table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS market_favorites (
                    id TEXT NOT NULL PRIMARY KEY,
                    userId TEXT NOT NULL,
                    crop TEXT NOT NULL,
                    market TEXT NOT NULL,
                    state TEXT NOT NULL,
                    category TEXT NOT NULL,
                    createdAt TEXT NOT NULL,
                    pendingSync INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // Idempotent offline write-sync queue
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_operations (
                    operationId TEXT NOT NULL PRIMARY KEY,
                    userId TEXT NOT NULL,
                    opType TEXT NOT NULL,
                    payloadJson TEXT NOT NULL,
                    status TEXT NOT NULL,
                    retryCount INTEGER NOT NULL,
                    lastError TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KissanDatabase {
        return Room.databaseBuilder(
            context,
            KissanDatabase::class.java,
            "kissansevaai.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }

    @Provides
    fun provideSchemeDao(db: KissanDatabase): SchemeDao = db.schemeDao()

    @Provides
    fun provideLoanProductDao(db: KissanDatabase): LoanProductDao = db.loanProductDao()

    @Provides
    fun provideTraderDao(db: KissanDatabase): TraderDao = db.traderDao()

    @Provides
    fun provideMarketPriceDao(db: KissanDatabase): MarketPriceDao = db.marketPriceDao()

    @Provides
    fun provideCropAnalysisDao(db: KissanDatabase): CropAnalysisDao = db.cropAnalysisDao()

    @Provides
    fun provideChatDao(db: KissanDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideIoTDao(db: KissanDatabase): IoTDao = db.ioTDao()

    @Provides
    fun provideLivestockDao(db: KissanDatabase): LivestockDao = db.livestockDao()

    @Provides
    fun provideWeatherDao(db: KissanDatabase): WeatherDao = db.weatherDao()

    @Provides
    fun provideCropPlanDao(db: KissanDatabase): CropPlanDao = db.cropPlanDao()

    @Provides
    fun providePlannerTaskDao(db: KissanDatabase): PlannerTaskDao = db.plannerTaskDao()

    @Provides
    fun provideSavedSchemeDao(db: KissanDatabase): SavedSchemeDao = db.savedSchemeDao()

    @Provides
    fun provideMarketFavoriteDao(db: KissanDatabase): MarketFavoriteDao = db.marketFavoriteDao()

    @Provides
    fun provideSyncOperationDao(db: KissanDatabase): SyncOperationDao = db.syncOperationDao()
}
