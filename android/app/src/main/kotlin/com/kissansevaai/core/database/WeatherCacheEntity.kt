package com.kissansevaai.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val locationKey: String,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val provider: String = "open-meteo",
    val temperatureC: Double = 0.0,
    val humidityPct: Double = 0.0,
    val windSpeedKmh: Double = 0.0,
    val precipitationMm: Double = 0.0,
    val conditionLabel: String = "",
    val advisoriesJson: String = "[]",
    val dailyJson: String = "[]",
    val aiSummary: String? = null,
    val fetchedAt: String = "",
    val expiresAt: String = "",
    val isStale: Boolean = false,
)

@Dao
interface WeatherDao {
    @Query("SELECT * FROM weather_cache WHERE locationKey = :key LIMIT 1")
    fun getByLocation(key: String): Flow<WeatherCacheEntity?>

    @Query("SELECT * FROM weather_cache ORDER BY fetchedAt DESC LIMIT 1")
    fun getLatest(): Flow<WeatherCacheEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WeatherCacheEntity)

    @Query("DELETE FROM weather_cache")
    suspend fun clearAll()
}
