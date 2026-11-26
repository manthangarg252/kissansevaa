package com.kissansevaai.core.location

data class ReverseGeocodeAddress(
    val village: String = "",
    val city: String = "",
    val district: String = "",
    val state: String = "",
    val country: String = "",
    val postalCode: String = "",
    val geocoderFailed: Boolean = false,
)

data class DetectedLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val isPrecise: Boolean = true,
    val locationSource: String = "gps_precise", // gps_precise | gps_approximate
    val timestampMs: Long = System.currentTimeMillis(),
    val isStale: Boolean = false,
    val village: String = "",
    val city: String = "",
    val district: String = "",
    val state: String = "",
    val country: String = "",
    val postalCode: String = "",
    val geocoderFailed: Boolean = false,
) {
    val displayLocality: String
        get() {
            val parts = listOf(village.ifBlank { city }, district, state).filter { it.isNotBlank() }.distinct()
            return if (parts.isNotEmpty()) parts.joinToString(", ") else "%.4f, %.4f".format(latitude, longitude)
        }

    val sourceBadgeLabel: String
        get() = when (locationSource) {
            "gps_precise" -> accuracyMeters?.let { "GPS Precise (±${it.toInt()}m)" } ?: "GPS Precise"
            "gps_approximate" -> accuracyMeters?.let { "GPS Approximate (±${it.toInt()}m)" } ?: "GPS Approximate"
            else -> locationSource
        }
}

sealed interface LocationDetectionResult {
    data class Success(val location: DetectedLocation) : LocationDetectionResult
    data object PermissionDenied : LocationDetectionResult
    data object LocationServicesDisabled : LocationDetectionResult
    data class PlayServicesUnavailable(val message: String) : LocationDetectionResult
    data class Timeout(val message: String) : LocationDetectionResult
    data class NullOrStaleLocation(val message: String, val cachedAgeMinutes: Long? = null) : LocationDetectionResult
    data class Error(val message: String) : LocationDetectionResult
}

data class SavedFarmLocation(
    val userId: String = "",
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
    val locationSource: String = "", // gps_precise | gps_approximate | manual
    val locationUpdatedAt: String = "",
    val farmSizeAcres: Double? = null,
    val soilType: String = "",
    val irrigationType: String = "",
    val primaryCrops: List<String> = emptyList(),
    val pendingSync: Boolean = false,
) {
    val hasConfirmedLocation: Boolean
        get() = district.isNotBlank() || state.isNotBlank() || (latitude != null && longitude != null)

    val formattedSummary: String
        get() = listOf(village.ifBlank { city }, district, state)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
            .ifBlank {
                if (latitude != null && longitude != null) "%.4f, %.4f".format(latitude, longitude) else ""
            }
}

interface LocationDetector {
    fun hasLocationPermission(): Boolean
    fun hasFineLocationPermission(): Boolean
    fun isLocationEnabled(): Boolean
    suspend fun detectCurrentLocation(): LocationDetectionResult
}

interface ReverseGeocoder {
    suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseGeocodeAddress
}
