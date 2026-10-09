package com.kissansevaai.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.kissansevaai.core.model.Trader
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "traders")
data class TraderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val location: String,
    val dealsIn: List<String>,
    val phone: String,
    val rating: Double,
    val reviewCount: Int,
    val imageUrl: String? = null,
) {
    fun toDomain(): Trader = Trader(
        id = id,
        name = name,
        location = location,
        dealsIn = dealsIn,
        phone = phone,
        rating = rating,
        reviewCount = reviewCount,
        imageUrl = imageUrl,
    )
}

fun Trader.toEntity(): TraderEntity = TraderEntity(
    id = id ?: name,
    name = name,
    location = location,
    dealsIn = dealsIn,
    phone = phone,
    rating = rating,
    reviewCount = reviewCount,
    imageUrl = imageUrl,
)

@Dao
interface TraderDao {
    @Query("SELECT * FROM traders ORDER BY rating DESC")
    fun getAllTraders(): Flow<List<TraderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(traders: List<TraderEntity>)

    @Query("DELETE FROM traders")
    suspend fun clearAll()
}
