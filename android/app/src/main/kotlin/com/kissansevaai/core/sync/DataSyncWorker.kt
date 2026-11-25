package com.kissansevaai.core.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.kissansevaai.core.repository.IoTRepository
import com.kissansevaai.core.repository.PlannerRepository
import com.kissansevaai.core.repository.PricesRepository
import com.kissansevaai.core.repository.SchemesRepository
import com.kissansevaai.core.repository.TradersRepository
import com.kissansevaai.core.repository.WeatherRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun ioTRepository(): IoTRepository
    fun pricesRepository(): PricesRepository
    fun tradersRepository(): TradersRepository
    fun offlineSyncManager(): OfflineSyncManager
    fun schemesRepository(): SchemesRepository
    fun weatherRepository(): WeatherRepository
    fun plannerRepository(): PlannerRepository
}

/**
 * Periodic WorkManager worker that flushes pending offline writes first,
 * then synchronizes IoT readings, Market Prices, Traders, Schemes, Weather, and Crop Plans into Room.
 */
class DataSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting background sync...")
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                SyncWorkerEntryPoint::class.java
            )

            // 0. Flush queued idempotent offline writes (best-effort; do not break pull sync in legacy tests)
            runCatching { entryPoint.offlineSyncManager().flushPendingOperations() }

            // 1. Sync IoT readings into Room
            entryPoint.ioTRepository().getLiveSensorData().collect {}

            // 2. Sync Market Prices into Room
            entryPoint.pricesRepository().getMarketPrices().collect {}

            // 3. Sync Traders directory into Room
            entryPoint.tradersRepository().getTraders().collect {}

            // 4. Refresh verified schemes, weather, and planner tasks best-effort
            runCatching { entryPoint.schemesRepository().getSchemes().collect {} }
            runCatching { entryPoint.weatherRepository().getForecast(includeAi = false).collect {} }
            runCatching { entryPoint.plannerRepository().getPlansAndTasks().collect {} }

            Log.i(TAG, "Periodic background sync completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Background sync failed: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "DataSyncWorker"
        const val UNIQUE_WORK_NAME = "kissan_background_sync"
        const val SYNC_INTERVAL_MINUTES = 15L

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<DataSyncWorker>(
                SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            Log.i(TAG, "Scheduled unique periodic work '$UNIQUE_WORK_NAME' with KEEP policy (interval: $SYNC_INTERVAL_MINUTES min)")
        }
    }
}
