package com.kissansevaai.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // No schema changes to chat_messages, assuming changes were to other tables
            // Just satisfy the Room migration requirement to avoid destructive migration
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KissanDatabase {
        return Room.databaseBuilder(
            context,
            KissanDatabase::class.java,
            "kissansevaai.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
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
}
