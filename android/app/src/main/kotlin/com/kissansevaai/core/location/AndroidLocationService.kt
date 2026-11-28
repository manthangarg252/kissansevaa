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
        const val MAX_FALLBACK_AGE_MS = 60 * 60 * 1000L
        const val DETECTION_TIMEOUT_MS = 6_000L
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
    private suspend fun fetchSingleFix(priority: Int, timeoutMs: Long): Location? {
        val request = CurrentLocationRequest.Builder()
            .setPriority(priority)
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .setMaxUpdateAgeMillis(120_000L)
            .setDurationMillis(timeoutMs)
            .build()
        val cts = CancellationTokenSource()
        return withTimeoutOrNull(timeoutMs + 1_000L) {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { cts.cancel() }
                fusedClient.getCurrentLocation(request, cts.token)
                    .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchBestLastKnownLocation(): Location? {
        val fusedLast = try {
            suspendCancellableCoroutine<Location?> { cont ->
                fusedClient.lastLocation
                    .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }
        } catch (_: Exception) {
            null
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val gpsLast = runCatching { lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
        val netLast = runCatching { lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
        return listOfNotNull(fusedLast, gpsLast, netLast).maxByOrNull { it.time }
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

        val preciseGranted = hasFineLocationPermission()
        var currentLoc: Location? = null

        if (gmsStatus == ConnectionResult.SUCCESS) {
            currentLoc = try {
                // Try high accuracy first when fine permission is granted, then fall back to balanced (Wi-Fi/cell) for indoor speed
                val primary = fetchSingleFix(
                    priority = if (preciseGranted) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    timeoutMs = DETECTION_TIMEOUT_MS,
                )
                primary ?: if (preciseGranted) {
                    fetchSingleFix(
                        priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        timeoutMs = 5_000L,
                    )
                } else {
                    null
                }
            } catch (_: SecurityException) {
                return LocationDetectionResult.PermissionDenied
            } catch (e: Exception) {
                null
            }
        }

        if (currentLoc == null) {
            val lastLoc = fetchBestLastKnownLocation()
            if (lastLoc != null) {
                val ageMs = (System.currentTimeMillis() - lastLoc.time).coerceAtLeast(0L)
                if (ageMs <= MAX_FALLBACK_AGE_MS) {
                    currentLoc = lastLoc
                } else {
                    val ageMins = ageMs / 60_000L
                    return LocationDetectionResult.NullOrStaleLocation(
                        message = "Current GPS signal timed out and last cached fix is stale (${ageMins}m old). Move outdoors or enter location manually.",
                        cachedAgeMinutes = ageMins,
                    )
                }
            } else if (gmsStatus != ConnectionResult.SUCCESS) {
                return LocationDetectionResult.PlayServicesUnavailable(
                    "Google Play Services is unavailable on this device (code $gmsStatus). Enter district & state manually."
                )
            } else {
                return LocationDetectionResult.Timeout("Unable to acquire current location. Check GPS signal or enter location manually.")
            }
        }

        val ageMs = (System.currentTimeMillis() - currentLoc.time).coerceAtLeast(0L)
        val isStale = ageMs > MAX_LOCATION_AGE_MS
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
                isStale = isStale,
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
