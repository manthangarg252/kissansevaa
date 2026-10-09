package com.kissansevaai.core.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.kissansevaai.core.repository.IoTRepository
import com.kissansevaai.core.repository.PricesRepository
import com.kissansevaai.core.repository.TradersRepository
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
}

/**
 * Periodic WorkManager worker that synchronizes IoT readings, market prices,
 * and traders directory into Room when network is connected.
 */
class DataSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting background sync for IoT, Market Prices, and Traders...")
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                SyncWorkerEntryPoint::class.java
            )

            // 1. Sync IoT readings into Room
            entryPoint.ioTRepository().getLiveSensorData().collect {
                // Flow emission consumed: triggers network fetch and Room write-through
            }

            // 2. Sync Market Prices into Room
            entryPoint.pricesRepository().getMarketPrices().collect {
                // Flow emission consumed: triggers network fetch and Room write-through
            }

            // 3. Sync Traders directory into Room
            entryPoint.tradersRepository().getTraders().collect {
                // Flow emission consumed: triggers network fetch and Room write-through
            }

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

        /**
         * Schedules the periodic background sync worker.
         * Enqueued with ExistingPeriodicWorkPolicy.KEEP to prevent restarts from double-scheduling.
         */
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
