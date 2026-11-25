package com.kissansevaai.core.sync

import android.util.Log
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.database.SyncOperationDao
import com.kissansevaai.core.model.SyncBatchRequest
import com.kissansevaai.core.model.SyncOperationItem
import com.kissansevaai.core.network.KissanApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineSyncManager @Inject constructor(
    private val api: KissanApi,
    private val syncDao: SyncOperationDao,
    private val dataStore: DataStoreManager,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observePendingCount(): Flow<Int> = syncDao.observePendingCount("")

    suspend fun flushPendingOperations(): Int {
        val userId = dataStore.userId.first().orEmpty()
        if (userId.isBlank()) return 0

        val pending = syncDao.getPendingOperations(userId)
        if (pending.isEmpty()) return 0

        val batchItems = pending.mapNotNull { entity ->
            try {
                val payload = json.decodeFromString<Map<String, JsonElement>>(entity.payloadJson)
                SyncOperationItem(
                    operationId = entity.operationId,
                    opType = entity.opType,
                    payload = payload,
                )
            } catch (e: Exception) {
                syncDao.updateStatus(
                    operationId = entity.operationId,
                    status = "failed",
                    retryCount = entity.retryCount + 1,
                    lastError = "Invalid payload JSON: ${e.message}",
                    updatedAt = System.currentTimeMillis(),
                )
                null
            }
        }

        if (batchItems.isEmpty()) return 0

        return try {
            val response = api.syncBatch(SyncBatchRequest(operations = batchItems))
            val now = System.currentTimeMillis()
            var syncedCount = 0
            for (result in response.results) {
                val opId = result.operationId ?: continue
                if (result.status == "applied" || result.status == "already_applied") {
                    syncDao.updateStatus(opId, "synced", 0, null, now)
                    syncedCount++
                } else {
                    val prev = pending.find { it.operationId == opId }
                    syncDao.updateStatus(
                        opId,
                        "failed",
                        (prev?.retryCount ?: 0) + 1,
                        result.error,
                        now,
                    )
                }
            }
            syncDao.pruneSynced()
            Log.i("OfflineSyncManager", "Flushed ${syncedCount}/${batchItems.size} queued operations")
            syncedCount
        } catch (e: Exception) {
            Log.w("OfflineSyncManager", "Batch sync deferred: ${e.message}")
            0
        }
    }
}
