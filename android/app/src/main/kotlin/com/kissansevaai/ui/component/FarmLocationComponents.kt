package com.kissansevaai.ui.component

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kissansevaai.R
import com.kissansevaai.core.ai.SpeechToText
import com.kissansevaai.core.location.DetectedLocation
import com.kissansevaai.core.location.LocationDetectionResult
import com.kissansevaai.core.location.SavedFarmLocation
import com.kissansevaai.ui.theme.*
import kotlinx.coroutines.launch

private val INDIAN_STATES_COMMON = listOf(
    "Maharashtra", "Punjab", "Gujarat", "Madhya Pradesh",
    "Uttar Pradesh", "Bihar", "Haryana", "Rajasthan", "Karnataka", "Telangana",
)

/**
 * Reusable controller & UI helper for single-shot GPS farm location detection,
 * explicit "Use this location for your farm?" confirmation dialog, manual location fallback,
 * and separation between Current Phone Location vs Saved Farm Location.
 */
@Composable
fun rememberFarmLocationPrompt(
    savedFarmLocation: SavedFarmLocation?,
    lang: String,
    speechToText: SpeechToText,
    onDetect: suspend () -> LocationDetectionResult,
    onConfirmSave: (SavedFarmLocation) -> Unit,
): FarmLocationPromptHandle {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isDetecting by remember { mutableStateOf(false) }
    var detectedCandidate by remember { mutableStateOf<DetectedLocation?>(null) }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var startInManualMode by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var showLocationServicesHint by remember { mutableStateOf(false) }

    fun executeDetect() {
        scope.launch {
            isDetecting = true
            statusBanner = null
            showLocationServicesHint = false
            when (val res = onDetect()) {
                is LocationDetectionResult.Success -> {
                    isDetecting = false
                    detectedCandidate = res.location
                    startInManualMode = false
                    showConfirmationDialog = true
                }
                LocationDetectionResult.PermissionDenied -> {
                    isDetecting = false
                    statusBanner = if (lang.startsWith("hi")) {
                        "स्थान की अनुमति अस्वीकार की गई। आप नीचे अपना जिला और राज्य स्वयं चुन सकते हैं।"
                    } else {
                        "Location permission denied. You can still enter your district and state manually."
                    }
                    startInManualMode = true
                    showConfirmationDialog = true
                }
                LocationDetectionResult.LocationServicesDisabled -> {
                    isDetecting = false
                    showLocationServicesHint = true
                    statusBanner = "Phone location services (GPS) are turned off. Enable GPS or enter district manually."
                }
                is LocationDetectionResult.PlayServicesUnavailable -> {
                    isDetecting = false
                    statusBanner = res.message
                    startInManualMode = true
                    showConfirmationDialog = true
                }
                is LocationDetectionResult.Timeout -> {
                    isDetecting = false
                    statusBanner = res.message
                }
                is LocationDetectionResult.NullOrStaleLocation -> {
                    isDetecting = false
                    statusBanner = res.message
                }
                is LocationDetectionResult.Error -> {
                    isDetecting = false
                    statusBanner = res.message
                }
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            executeDetect()
        } else {
            statusBanner = "Location permission denied. Enter your village, district and state manually."
            startInManualMode = true
            showConfirmationDialog = true
        }
    }

    val triggerDetection: () -> Unit = {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            executeDetect()
        } else {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            )
        }
    }

    val openManualDialog: () -> Unit = {
        startInManualMode = true
        showConfirmationDialog = true
    }

    if (showConfirmationDialog) {
        FarmLocationConfirmationDialog(
            detected = detectedCandidate,
            existingSaved = savedFarmLocation,
            initialManualMode = startInManualMode,
            lang = lang,
            speechToText = speechToText,
            onConfirm = { confirmed ->
                showConfirmationDialog = false
                onConfirmSave(confirmed)
            },
            onRetryDetection = {
                showConfirmationDialog = false
                triggerDetection()
            },
            onDismiss = { showConfirmationDialog = false },
        )
    }

    if (showLocationServicesHint) {
        AlertDialog(
            onDismissRequest = { showLocationServicesHint = false },
            containerColor = CardWhite,
            title = { Text("Location Services Off", fontWeight = FontWeight.Bold, color = TextCharcoal) },
            text = {
                Text("Enable phone GPS/location services to detect your farm automatically, or enter your district manually.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLocationServicesHint = false
                        runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                ) { Text("Open GPS Settings") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showLocationServicesHint = false
                        openManualDialog()
                    },
                ) { Text("Enter Manually", color = DeepEmerald) }
            },
        )
    }

    return FarmLocationPromptHandle(
        isDetecting = isDetecting,
        statusMessage = statusBanner,
        detectNow = triggerDetection,
        openManual = openManualDialog,
    )
}

data class FarmLocationPromptHandle(
    val isDetecting: Boolean,
    val statusMessage: String?,
    val detectNow: () -> Unit,
    val openManual: () -> Unit,
)

@Composable
fun FarmLocationConfirmationDialog(
    detected: DetectedLocation?,
    existingSaved: SavedFarmLocation?,
    initialManualMode: Boolean,
    lang: String,
    speechToText: SpeechToText,
    onConfirm: (SavedFarmLocation) -> Unit,
    onRetryDetection: () -> Unit,
    onDismiss: () -> Unit,
) {
    var manualMode by remember { mutableStateOf(initialManualMode || detected == null) }

    var farmName by remember(existingSaved) { mutableStateOf(existingSaved?.farmName.orEmpty()) }
    var village by remember(detected, existingSaved) {
        mutableStateOf(detected?.village?.ifBlank { existingSaved?.village.orEmpty() } ?: existingSaved?.village.orEmpty())
    }
    var city by remember(detected, existingSaved) {
        mutableStateOf(detected?.city ?: existingSaved?.city.orEmpty())
    }
    var district by remember(detected, existingSaved) {
        mutableStateOf(detected?.district?.ifBlank { "" } ?: existingSaved?.district.orEmpty())
    }
    var state by remember(detected, existingSaved) {
        mutableStateOf(detected?.state?.ifBlank { "" } ?: existingSaved?.state.orEmpty())
    }
    val country = detected?.country?.ifBlank { existingSaved?.country.orEmpty() } ?: existingSaved?.country.orEmpty().ifBlank { "India" }
    val postalCode = detected?.postalCode ?: existingSaved?.postalCode.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.MyLocation, null, tint = DeepEmerald)
                Text(
                    text = stringResource(R.string.location_confirm_title),
                    fontWeight = FontWeight.Bold,
                    color = DeepEmerald,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (detected != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GreenBg,
                        border = BorderStroke(1.dp, GreenTint),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Current Phone Location", fontWeight = FontWeight.Bold, color = DeepEmerald, style = MaterialTheme.typography.labelMedium)
                            Text("Locality: ${detected.displayLocality}", color = TextCharcoal, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "District: ${detected.district.ifBlank { "Unavailable — select manually" }} • State: ${detected.state.ifBlank { "Unavailable" }}",
                                color = if (detected.district.isBlank()) SecondaryOrange else TextCharcoal,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = "Coordinates: %.4f, %.4f".format(detected.latitude, detected.longitude),
                                color = TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                text = "Source: ${detected.sourceBadgeLabel}",
                                color = DeepEmerald,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            if (detected.geocoderFailed || detected.district.isBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Warning, null, tint = SecondaryOrange, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = if (detected.geocoderFailed) "Reverse geocoding unavailable; please enter district & state." else "District not returned by geocoder; verify or edit below.",
                                        color = SecondaryOrange,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }

                if (existingSaved != null && existingSaved.hasConfirmedLocation) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AmberTint,
                        border = BorderStroke(1.dp, SecondaryOrange),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Saved Farm Location (Current)", fontWeight = FontWeight.Bold, color = SecondaryOrange, style = MaterialTheme.typography.labelSmall)
                            Text(existingSaved.formattedSummary, style = MaterialTheme.typography.bodySmall, color = TextCharcoal)
                            if (existingSaved.locationUpdatedAt.isNotBlank()) {
                                Text("Last updated: ${existingSaved.locationUpdatedAt}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                        }
                    }
                }

                if (manualMode || detected?.district.isNullOrBlank() || detected?.state.isNullOrBlank()) {
                    VoiceInputField(
                        value = farmName,
                        onValueChange = { farmName = it },
                        label = "Farm Name",
                        fieldKey = "farmName",
                        lang = lang,
                        speechToText = speechToText,
                        placeholder = "e.g. Riverbank Plot",
                    )
                    VoiceInputField(
                        value = village,
                        onValueChange = { village = it },
                        label = "Village / Locality",
                        fieldKey = "village",
                        lang = lang,
                        speechToText = speechToText,
                    )
                    VoiceInputField(
                        value = district,
                        onValueChange = { district = it },
                        label = "District",
                        fieldKey = "district",
                        lang = lang,
                        speechToText = speechToText,
                        supportingWarning = if (detected != null && district.isBlank()) "District not auto-detected — please select or speak" else null,
                    )
                    VoiceInputField(
                        value = state,
                        onValueChange = { state = it },
                        label = "State",
                        fieldKey = "state",
                        lang = lang,
                        speechToText = speechToText,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        INDIAN_STATES_COMMON.forEach { st ->
                            FilterChip(
                                selected = state.equals(st, ignoreCase = true),
                                onClick = { state = st },
                                label = { Text(st) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val base = existingSaved ?: SavedFarmLocation()
                    val useManualCoords = manualMode && detected == null
                    val finalLat = if (useManualCoords) base.latitude else (detected?.latitude ?: base.latitude)
                    val finalLon = if (useManualCoords) base.longitude else (detected?.longitude ?: base.longitude)
                    val src = when {
                        detected != null && !manualMode -> detected.locationSource
                        detected != null && manualMode -> detected.locationSource
                        else -> "manual"
                    }
                    onConfirm(
                        base.copy(
                            farmName = farmName.trim(),
                            village = village.trim(),
                            city = city.trim(),
                            district = district.trim(),
                            state = state.trim(),
                            country = country.trim(),
                            postalCode = postalCode.trim(),
                            latitude = finalLat,
                            longitude = finalLon,
                            locationSource = src,
                        )
                    )
                },
                enabled = district.isNotBlank() || state.isNotBlank() || detected != null,
                colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                shape = RoundedCornerShape(9999.dp),
            ) {
                Text(stringResource(R.string.location_confirm_btn), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { manualMode = !manualMode },
                    shape = RoundedCornerShape(9999.dp),
                ) {
                    Text(stringResource(R.string.location_change_btn), color = DeepEmerald)
                }
                TextButton(onClick = onRetryDetection) {
                    Text(stringResource(R.string.location_retry_btn), color = TextCharcoal)
                }
            }
        },
    )
}
