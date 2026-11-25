package com.kissansevaai.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "market_favorites")
data class MarketFavoriteEntity(
    @PrimaryKey val id: String,
    val userId: String = "",
    val crop: String,
    val market: String = "",
    val state: String = "",
    val category: String = "crop",
    val createdAt: String = "",
    val pendingSync: Boolean = false,
)

@Dao
interface MarketFavoriteDao {
    @Query("SELECT * FROM market_favorites WHERE userId = :userId OR userId = '' ORDER BY crop ASC")
    fun getFavoritesForUser(userId: String): Flow<List<MarketFavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MarketFavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MarketFavoriteEntity>)

    @Query("DELETE FROM market_favorites WHERE crop = :crop AND market = :market AND (userId = :userId OR userId = '')")
    suspend fun remove(crop: String, market: String, userId: String)

    @Query("DELETE FROM market_favorites WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
