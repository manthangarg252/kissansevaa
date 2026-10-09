package com.kissansevaai.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.kissansevaai.core.model.SensorData
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "iot_readings")
data class IoTReadingEntity(
    @PrimaryKey val id: String = "latest",
    val temperature: Double?,
    val humidity: Double?,
    val soilMoisture: Double?,
    val phLevel: Double?,
    val timestamp: String?,
) {
    fun toDomain(): SensorData = SensorData(
        temperature = temperature,
        humidity = humidity,
        soilMoisture = soilMoisture,
        phLevel = phLevel,
        timestamp = timestamp,
    )
}

fun SensorData.toEntity(): IoTReadingEntity = IoTReadingEntity(
    id = "latest",
    temperature = temperature,
    humidity = humidity,
    soilMoisture = soilMoisture,
    phLevel = phLevel,
    timestamp = timestamp ?: System.currentTimeMillis().toString(),
)

@Dao
interface IoTDao {
    @Query("SELECT * FROM iot_readings WHERE id = 'latest' LIMIT 1")
    fun getLatest(): Flow<IoTReadingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: IoTReadingEntity)

    @Query("DELETE FROM iot_readings")
    suspend fun clearAll()
}
