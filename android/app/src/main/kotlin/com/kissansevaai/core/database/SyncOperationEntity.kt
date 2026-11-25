package com.kissansevaai.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sync_operations")
data class SyncOperationEntity(
    @PrimaryKey val operationId: String,
    val userId: String = "",
    val opType: String,
    val payloadJson: String,
    val status: String = "pending", // pending | synced | failed
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface SyncOperationDao {
    @Query("SELECT * FROM sync_operations WHERE status IN ('pending', 'failed') AND (userId = :userId OR :userId = '') ORDER BY createdAt ASC")
    suspend fun getPendingOperations(userId: String = ""): List<SyncOperationEntity>

    @Query("SELECT COUNT(*) FROM sync_operations WHERE status IN ('pending', 'failed') AND (userId = :userId OR :userId = '')")
    fun observePendingCount(userId: String = ""): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(op: SyncOperationEntity)

    @Query("UPDATE sync_operations SET status = :status, retryCount = :retryCount, lastError = :lastError, updatedAt = :updatedAt WHERE operationId = :operationId")
    suspend fun updateStatus(operationId: String, status: String, retryCount: Int, lastError: String?, updatedAt: Long)

    @Query("DELETE FROM sync_operations WHERE status = 'synced'")
    suspend fun pruneSynced()

    @Query("DELETE FROM sync_operations WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
