package com.kissansevaai.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AndroidReverseGeocoder @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReverseGeocoder {

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseGeocodeAddress {
        if (!Geocoder.isPresent()) {
            return ReverseGeocodeAddress(geocoderFailed = true)
        }
        val geocoder = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(6_000L) {
                suspendCancellableCoroutine { cont ->
                    try {
                        geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                val first = addresses.firstOrNull()
                                cont.resume(first?.toModel() ?: ReverseGeocodeAddress(geocoderFailed = true))
                            }

                            override fun onError(errorMessage: String?) {
                                cont.resume(ReverseGeocodeAddress(geocoderFailed = true))
                            }
                        })
                    } catch (_: Exception) {
                        cont.resume(ReverseGeocodeAddress(geocoderFailed = true))
                    }
                }
            } ?: ReverseGeocodeAddress(geocoderFailed = true)
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                try {
                    val list = geocoder.getFromLocation(latitude, longitude, 1)
                    list?.firstOrNull()?.toModel() ?: ReverseGeocodeAddress(geocoderFailed = true)
                } catch (_: Exception) {
                    ReverseGeocodeAddress(geocoderFailed = true)
                }
            }
        }
    }

    private fun Address.toModel(): ReverseGeocodeAddress {
        val rawDistrict = subAdminArea?.trim().orEmpty()
        // Never invent district when unavailable; only clean trailing word "District" if present
        val cleanDistrict = rawDistrict.replace(Regex("(?i)\\s+district$"), "").trim()
        val rawVillage = subLocality?.trim().orEmpty().ifBlank { thoroughfare?.trim().orEmpty() }
        return ReverseGeocodeAddress(
            village = rawVillage,
            city = locality?.trim().orEmpty(),
            district = cleanDistrict,
            state = adminArea?.trim().orEmpty(),
            country = countryName?.trim().orEmpty(),
            postalCode = postalCode?.trim().orEmpty(),
            geocoderFailed = false,
        )
    }
}

@Singleton
class AndroidLocationDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reverseGeocoder: ReverseGeocoder,
) : LocationDetector {

    companion object {
        const val MAX_LOCATION_AGE_MS = 15 * 60 * 1000L
        const val DETECTION_TIMEOUT_MS = 12_000L
    }

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override fun hasLocationPermission(): Boolean {
        return hasFineLocationPermission() || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun detectCurrentLocation(): LocationDetectionResult {
        if (!hasLocationPermission()) {
            return LocationDetectionResult.PermissionDenied
        }
        if (!isLocationEnabled()) {
            return LocationDetectionResult.LocationServicesDisabled
        }

        val gmsStatus = try {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        } catch (_: Exception) {
            ConnectionResult.SERVICE_MISSING
        }
        if (gmsStatus != ConnectionResult.SUCCESS) {
            return LocationDetectionResult.PlayServicesUnavailable(
                "Google Play Services is unavailable on this device (code $gmsStatus). Enter district & state manually."
            )
        }

        val preciseGranted = hasFineLocationPermission()
        val request = CurrentLocationRequest.Builder()
            .setPriority(if (preciseGranted) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .setMaxUpdateAgeMillis(60_000L)
            .setDurationMillis(DETECTION_TIMEOUT_MS)
            .build()

        val cts = CancellationTokenSource()
        val currentLoc: Location? = try {
            withTimeoutOrNull(DETECTION_TIMEOUT_MS + 1_000L) {
                suspendCancellableCoroutine { cont ->
                    cont.invokeOnCancellation { cts.cancel() }
                    fusedClient.getCurrentLocation(request, cts.token)
                        .addOnSuccessListener { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume(null)
                        }
                }
            }
        } catch (e: SecurityException) {
            return LocationDetectionResult.PermissionDenied
        } catch (e: Exception) {
            return LocationDetectionResult.Error("Location detection error: ${e.localizedMessage ?: "unknown"}")
        }

        if (currentLoc == null) {
            // Check lastLocation ONLY to distinguish stale cache vs empty provider (never silently adopt stale GPS!)
            val lastLoc: Location? = try {
                suspendCancellableCoroutine { cont ->
                    fusedClient.lastLocation
                        .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                }
            } catch (_: Exception) {
                null
            }
            if (lastLoc != null) {
                val ageMs = System.currentTimeMillis() - lastLoc.time
                val ageMins = (ageMs / 60_000L).coerceAtLeast(0L)
                return LocationDetectionResult.NullOrStaleLocation(
                    message = "Current GPS signal timed out and last cached fix is stale (${ageMins}m old). Move outdoors or enter location manually.",
                    cachedAgeMinutes = ageMins,
                )
            }
            return LocationDetectionResult.Timeout("Unable to acquire current location within 12s. Check GPS signal or enter location manually.")
        }

        val ageMs = System.currentTimeMillis() - currentLoc.time
        val isStale = ageMs > MAX_LOCATION_AGE_MS
        if (isStale) {
            return LocationDetectionResult.NullOrStaleLocation(
                message = "Location fix is stale (${ageMs / 60_000L}m old). Retry outdoors or select location manually.",
                cachedAgeMinutes = ageMs / 60_000L,
            )
        }

        val effectivePrecise = preciseGranted && (currentLoc.accuracy <= 100f)
        val source = if (effectivePrecise) "gps_precise" else "gps_approximate"
        val geo = reverseGeocoder.reverseGeocode(currentLoc.latitude, currentLoc.longitude)

        return LocationDetectionResult.Success(
            DetectedLocation(
                latitude = currentLoc.latitude,
                longitude = currentLoc.longitude,
                accuracyMeters = if (currentLoc.hasAccuracy()) currentLoc.accuracy else null,
                isPrecise = effectivePrecise,
                locationSource = source,
                timestampMs = currentLoc.time,
                isStale = false,
                village = geo.village,
                city = geo.city,
                district = geo.district,
                state = geo.state,
                country = geo.country,
                postalCode = geo.postalCode,
                geocoderFailed = geo.geocoderFailed,
            )
        )
    }
}
