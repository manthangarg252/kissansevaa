package com.kissansevaai.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.kissansevaai.core.model.MarketPrice
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "market_prices")
data class MarketPriceEntity(
    @PrimaryKey val id: String,
    val crop: String,
    val price: Double,
    val modalPrice: Double?,
    val minPrice: Double?,
    val maxPrice: Double?,
    val unit: String,
    val market: String,
    val state: String,
    val trend: String,
    val updatedAt: String?,
    val lastUpdated: String?,
) {
    fun toDomain(): MarketPrice = MarketPrice(
        id = id,
        crop = crop,
        price = price,
        modalPrice = modalPrice,
        minPrice = minPrice,
        maxPrice = maxPrice,
        unit = unit,
        market = market,
        state = state,
        trend = trend,
        updatedAt = updatedAt,
        lastUpdated = lastUpdated,
    )
}

fun MarketPrice.toEntity(): MarketPriceEntity = MarketPriceEntity(
    id = id ?: "${crop}_${market}_${state}",
    crop = crop,
    price = effectivePrice,
    modalPrice = modalPrice,
    minPrice = minPrice,
    maxPrice = maxPrice,
    unit = unit,
    market = market,
    state = state,
    trend = trend,
    updatedAt = updatedAt,
    lastUpdated = lastUpdated,
)

@Dao
interface MarketPriceDao {
    @Query("SELECT * FROM market_prices ORDER BY crop ASC")
    fun getAllMarketPrices(): Flow<List<MarketPriceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(prices: List<MarketPriceEntity>)

    @Query("DELETE FROM market_prices")
    suspend fun clearAll()
}
