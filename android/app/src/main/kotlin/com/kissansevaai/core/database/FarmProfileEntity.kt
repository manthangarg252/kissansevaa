package com.kissansevaai.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.kissansevaai.core.location.SavedFarmLocation
import com.kissansevaai.core.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "farm_profiles")
data class FarmProfileEntity(
    @PrimaryKey val userId: String,
    val farmerName: String = "",
    val farmName: String = "",
    val village: String = "",
    val city: String = "",
    val district: String = "",
    val state: String = "",
    val country: String = "",
    val postalCode: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationSource: String = "",
    val locationUpdatedAt: String = "",
    val farmSizeAcres: Double? = null,
    val soilType: String = "",
    val irrigationType: String = "",
    val primaryCropsJson: String = "[]",
    val pendingSync: Boolean = false,
) {
    fun toDomain(json: Json = Json { ignoreUnknownKeys = true }): SavedFarmLocation {
        val crops = try {
            json.decodeFromString<List<String>>(primaryCropsJson)
        } catch (_: Exception) {
            emptyList()
        }
        return SavedFarmLocation(
            userId = userId,
            farmerName = farmerName,
            farmName = farmName,
            village = village,
            city = city,
            district = district,
            state = state,
            country = country,
            postalCode = postalCode,
            latitude = latitude,
            longitude = longitude,
            locationSource = locationSource,
            locationUpdatedAt = locationUpdatedAt,
            farmSizeAcres = farmSizeAcres,
            soilType = soilType,
            irrigationType = irrigationType,
            primaryCrops = crops,
            pendingSync = pendingSync,
        )
    }
}

fun SavedFarmLocation.toEntity(json: Json = Json { ignoreUnknownKeys = true }): FarmProfileEntity = FarmProfileEntity(
    userId = userId,
    farmerName = farmerName,
    farmName = farmName,
    village = village,
    city = city,
    district = district,
    state = state,
    country = country,
    postalCode = postalCode,
    latitude = latitude,
    longitude = longitude,
    locationSource = locationSource,
    locationUpdatedAt = locationUpdatedAt,
    farmSizeAcres = farmSizeAcres,
    soilType = soilType,
    irrigationType = irrigationType,
    primaryCropsJson = json.encodeToString(primaryCrops),
    pendingSync = pendingSync,
)

fun UserProfile.toSavedFarmLocation(pendingSync: Boolean = false): SavedFarmLocation = SavedFarmLocation(
    userId = id,
    farmerName = name,
    farmName = farmName,
    village = village,
    city = city,
    district = district,
    state = state,
    country = country,
    postalCode = postalCode,
    latitude = latitude,
    longitude = longitude,
    locationSource = locationSource.ifBlank { if (latitude != null && longitude != null) "manual" else "" },
    locationUpdatedAt = locationUpdatedAt.orEmpty(),
    farmSizeAcres = farmSizeAcres,
    soilType = soilType,
    irrigationType = irrigationType,
    primaryCrops = primaryCrops,
    pendingSync = pendingSync,
)

@Dao
interface FarmProfileDao {
    @Query("SELECT * FROM farm_profiles WHERE userId = :userId LIMIT 1")
    fun observeProfile(userId: String): Flow<FarmProfileEntity?>

    @Query("SELECT * FROM farm_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getProfileOnce(userId: String): FarmProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: FarmProfileEntity)

    @Query("DELETE FROM farm_profiles WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
