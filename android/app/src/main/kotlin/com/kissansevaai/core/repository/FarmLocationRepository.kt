package com.kissansevaai.core.repository

import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.common.toUserFriendlyMessage
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.database.FarmProfileDao
import com.kissansevaai.core.database.SyncOperationDao
import com.kissansevaai.core.database.SyncOperationEntity
import com.kissansevaai.core.database.toEntity
import com.kissansevaai.core.database.toSavedFarmLocation
import com.kissansevaai.core.location.DetectedLocation
import com.kissansevaai.core.location.LocationDetectionResult
import com.kissansevaai.core.location.LocationDetector
import com.kissansevaai.core.location.SavedFarmLocation
import com.kissansevaai.core.model.FarmProfileUpdateRequest
import com.kissansevaai.core.network.KissanApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth separating ephemeral current phone GPS location from
 * user-confirmed saved farm location persisted in Room and synced to MongoDB.
 */
@Singleton
class FarmLocationRepository @Inject constructor(
    private val api: KissanApi,
    private val farmProfileDao: FarmProfileDao,
    private val syncDao: SyncOperationDao,
    private val dataStore: DataStoreManager,
    private val locationDetector: LocationDetector,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Holds only transient current device location — NEVER auto-overwrites saved farm location.
    private val _currentPhoneLocation = MutableStateFlow<DetectedLocation?>(null)
    val currentPhoneLocation: StateFlow<DetectedLocation?> = _currentPhoneLocation

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSavedFarmLocation(): Flow<SavedFarmLocation?> =
        dataStore.userId.flatMapLatest { uid ->
            val clean = uid.orEmpty()
            if (clean.isBlank()) {
                flowOf(null)
            } else {
                farmProfileDao.observeProfile(clean).map { it?.toDomain(json) }
            }
        }

    suspend fun getSavedFarmLocationOnce(): SavedFarmLocation? {
        val uid = dataStore.userId.first().orEmpty()
        if (uid.isBlank()) return null
        return farmProfileDao.getProfileOnce(uid)?.toDomain(json)
    }

    suspend fun detectCurrentPhoneLocation(): LocationDetectionResult {
        val res = locationDetector.detectCurrentLocation()
        if (res is LocationDetectionResult.Success) {
            _currentPhoneLocation.value = res.location
        }
        return res
    }

    suspend fun refreshFromBackend(): SavedFarmLocation? = withContext(Dispatchers.IO) {
        val uid = dataStore.userId.first().orEmpty()
        if (uid.isBlank()) return@withContext null
        try {
            val remote = api.getMe()
            val mapped = remote.toSavedFarmLocation(pendingSync = false).let { incoming ->
                val local = farmProfileDao.getProfileOnce(uid)?.toDomain(json)
                if (local != null && local.pendingSync) {
                    // Never clobber un-synced local farmer edits
                    local
                } else {
                    incoming.copy(userId = uid)
                }
            }
            farmProfileDao.upsert(mapped.toEntity(json))
            mapped
        } catch (_: Exception) {
            farmProfileDao.getProfileOnce(uid)?.toDomain(json)
        }
    }

    /**
     * Explicit user confirmation to save & sync farm location or farm profile fields.
     */
    suspend fun confirmAndSaveFarmProfile(
        updatedLocation: SavedFarmLocation,
    ): Resource<SavedFarmLocation> = withContext(Dispatchers.IO) {
        val uid = dataStore.userId.first().orEmpty().ifBlank { updatedLocation.userId }
        if (uid.isBlank()) {
            return@withContext Resource.Error("Sign in first to save your farm profile.")
        }

        val nowIso = currentIsoTimestamp()
        val toPersist = updatedLocation.copy(
            userId = uid,
            locationUpdatedAt = nowIso,
            locationSource = updatedLocation.locationSource.ifBlank {
                if (updatedLocation.latitude != null && updatedLocation.longitude != null) "gps_precise" else "manual"
            },
            pendingSync = true,
        )

        farmProfileDao.upsert(toPersist.toEntity(json))

        val req = FarmProfileUpdateRequest(
            name = toPersist.farmerName.takeIf { it.isNotBlank() },
            farmName = toPersist.farmName,
            state = toPersist.state,
            district = toPersist.district,
            city = toPersist.city,
            village = toPersist.village,
            country = toPersist.country,
            postalCode = toPersist.postalCode,
            latitude = toPersist.latitude,
            longitude = toPersist.longitude,
            locationSource = toPersist.locationSource,
            locationUpdatedAt = toPersist.locationUpdatedAt,
            farmSizeAcres = toPersist.farmSizeAcres,
            soilType = toPersist.soilType,
            irrigationType = toPersist.irrigationType,
            primaryCrops = toPersist.primaryCrops,
        )

        try {
            val syncedRemote = api.updateProfile(req)
            val committed = syncedRemote.toSavedFarmLocation(pendingSync = false).copy(userId = uid)
            farmProfileDao.upsert(committed.toEntity(json))
            Resource.Success(committed)
        } catch (e: Exception) {
            val opId = "profile_${uid}_${System.currentTimeMillis()}"
            val payload = buildMap<String, JsonElement> {
                if (toPersist.farmerName.isNotBlank()) put("name", JsonPrimitive(toPersist.farmerName))
                put("farmName", JsonPrimitive(toPersist.farmName))
                put("state", JsonPrimitive(toPersist.state))
                put("district", JsonPrimitive(toPersist.district))
                put("city", JsonPrimitive(toPersist.city))
                put("village", JsonPrimitive(toPersist.village))
                put("country", JsonPrimitive(toPersist.country))
                put("postalCode", JsonPrimitive(toPersist.postalCode))
                toPersist.latitude?.let { put("latitude", JsonPrimitive(it)) }
                toPersist.longitude?.let { put("longitude", JsonPrimitive(it)) }
                put("locationSource", JsonPrimitive(toPersist.locationSource))
                put("locationUpdatedAt", JsonPrimitive(toPersist.locationUpdatedAt))
                toPersist.farmSizeAcres?.let { put("farmSizeAcres", JsonPrimitive(it)) }
                put("soilType", JsonPrimitive(toPersist.soilType))
                put("irrigationType", JsonPrimitive(toPersist.irrigationType))
                put("primaryCrops", JsonArray(toPersist.primaryCrops.map { JsonPrimitive(it) }))
            }
            syncDao.enqueue(
                SyncOperationEntity(
                    operationId = opId,
                    userId = uid,
                    opType = "profile_update",
                    payloadJson = json.encodeToString(payload),
                )
            )
            Resource.Success(toPersist)
        }
    }

    private fun currentIsoTimestamp(): String {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        df.timeZone = TimeZone.getTimeZone("UTC")
        return df.format(Date())
    }
}
