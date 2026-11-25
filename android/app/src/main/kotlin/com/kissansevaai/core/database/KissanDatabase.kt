package com.kissansevaai.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SchemeEntity::class,
        LoanProductEntity::class,
        TraderEntity::class,
        MarketPriceEntity::class,
        CropAnalysisEntity::class,
        ChatMessageEntity::class,
        IoTReadingEntity::class,
        LivestockAnalysisEntity::class,
        WeatherCacheEntity::class,
        CropPlanEntity::class,
        PlannerTaskEntity::class,
        SavedSchemeEntity::class,
        MarketFavoriteEntity::class,
        SyncOperationEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class KissanDatabase : RoomDatabase() {
    abstract fun schemeDao(): SchemeDao
    abstract fun loanProductDao(): LoanProductDao
    abstract fun traderDao(): TraderDao
    abstract fun marketPriceDao(): MarketPriceDao
    abstract fun cropAnalysisDao(): CropAnalysisDao
    abstract fun chatDao(): ChatDao
    abstract fun ioTDao(): IoTDao
    abstract fun livestockDao(): LivestockDao
    abstract fun weatherDao(): WeatherDao
    abstract fun cropPlanDao(): CropPlanDao
    abstract fun plannerTaskDao(): PlannerTaskDao
    abstract fun savedSchemeDao(): SavedSchemeDao
    abstract fun marketFavoriteDao(): MarketFavoriteDao
    abstract fun syncOperationDao(): SyncOperationDao
}
