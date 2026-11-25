package com.kissansevaai.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_schemes")
data class SavedSchemeEntity(
    @PrimaryKey val id: String,
    val userId: String = "",
    val schemeId: String,
    val status: String = "saved",
    val notes: String = "",
    val savedAt: String = "",
    val pendingSync: Boolean = false,
)

@Dao
interface SavedSchemeDao {
    @Query("SELECT * FROM saved_schemes WHERE userId = :userId OR userId = '' ORDER BY savedAt DESC")
    fun getSavedForUser(userId: String): Flow<List<SavedSchemeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SavedSchemeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SavedSchemeEntity>)

    @Query("DELETE FROM saved_schemes WHERE schemeId = :schemeId AND (userId = :userId OR userId = '')")
    suspend fun remove(schemeId: String, userId: String)

    @Query("DELETE FROM saved_schemes WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
