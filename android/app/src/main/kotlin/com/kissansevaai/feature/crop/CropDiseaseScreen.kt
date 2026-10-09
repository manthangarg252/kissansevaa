package com.kissansevaai.feature.crop

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.kissansevaai.R
import com.kissansevaai.ui.component.AiResponseCard
import com.kissansevaai.ui.component.FeatureScaffold
import com.kissansevaai.ui.component.LoadingOverlay
import com.kissansevaai.ui.theme.*

@Composable
fun CropDiseaseScreen(onBack: () -> Unit, viewModel: CropViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var location by remember { mutableStateOf("") }
    var soilType by remember { mutableStateOf("") }

    var showSourceChooser by remember { mutableStateOf(false) }
    var showCameraDialog by remember { mutableStateOf(false) }
    var permissionNotice by remember { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            permissionNotice = null
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            permissionNotice = null
            showCameraDialog = true
        } else {
            // Camera denied -> fall back to gallery with explanation
            permissionNotice = "Camera permission denied. Opening photo gallery instead."
            galleryLauncher.launch("image/*")
        }
    }

    val onChooseCamera = {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (granted) {
            permissionNotice = null
            showCameraDialog = true
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    if (showCameraDialog) {
        com.kissansevaai.ui.component.CameraCaptureDialog(
            onDismissRequest = { showCameraDialog = false },
            onImageCaptured = { uri ->
                showCameraDialog = false
                selectedUri = uri
                permissionNotice = null
            },
            onError = { err ->
                showCameraDialog = false
                permissionNotice = "Camera error: $err. Please select from gallery."
                galleryLauncher.launch("image/*")
            }
        )
    }

    if (showSourceChooser) {
        AlertDialog(
            onDismissRequest = { showSourceChooser = false },
            title = { Text("Select Crop Photo") },
            text = { Text("Take a live leaf photo with CameraX or select an existing picture from your gallery.") },
            confirmButton = {
                TextButton(onClick = {
                    showSourceChooser = false
                    onChooseCamera()
                }) { Text("Camera", color = Emerald400) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSourceChooser = false
                    permissionNotice = null
                    galleryLauncher.launch("image/*")
                }) { Text("Gallery", color = Slate300) }
            },
            containerColor = Slate800,
            textContentColor = Slate200,
            titleContentColor = Emerald400
        )
    }

    FeatureScaffold(title = stringResource(R.string.crop_title), onBack = onBack) { _ ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            permissionNotice?.let { notice ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Amber500.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = notice,
                        color = Amber500,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Image picker card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth().height(200.dp).clickable { showSourceChooser = true },
            ) {
                if (selectedUri != null) {
                    AsyncImage(
                        model = selectedUri,
                        contentDescription = "Selected leaf",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().border(2.dp, Slate700, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CloudUpload, null, tint = Slate500, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.crop_upload_hint), color = Slate500)
                            Spacer(Modifier.height(4.dp))
                            Text("Tap to choose Camera or Gallery", color = Emerald400, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Quick source action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onChooseCamera,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Take Photo")
                }
                OutlinedButton(
                    onClick = {
                        permissionNotice = null
                        galleryLauncher.launch("image/*")
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("From Gallery")
                }
            }

            OutlinedTextField(
                value = location, onValueChange = { location = it },
                label = { Text(stringResource(R.string.common_location)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = soilType, onValueChange = { soilType = it },
                label = { Text(stringResource(R.string.common_soil_type)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            val currentLang by viewModel.language.collectAsState()
            com.kissansevaai.ui.component.VoiceFormFillButton(
                fields = listOf("location", "soilType"),
                lang = currentLang,
                speechToText = viewModel.speechToText,
                fieldExtractor = viewModel.fieldExtractor,
                onFill = { data ->
                    data["location"]?.let { location = it }
                    data["soilType"]?.let { soilType = it }
                },
                onMessage = { msg ->
                    permissionNotice = msg
                },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { selectedUri?.let { viewModel.analyze(it, location, soilType) } },
                enabled = selectedUri != null && !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
            ) { Text(stringResource(R.string.crop_detect_btn)) }

            LoadingOverlay(state.isLoading)

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            state.result?.let { r ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.crop_analysis_result), style = MaterialTheme.typography.titleMedium, color = Emerald400)
                            com.kissansevaai.ui.component.TTSButton(
                                ttsEngine = viewModel.ttsEngine,
                                textToSpeak = "${r.diseaseName}. Crop: ${r.cropName ?: ""}. Severity: ${r.severity ?: ""}. ${r.summary ?: ""}",
                                lang = currentLang,
                                onFallbackExplanation = { fallbackMsg -> permissionNotice = fallbackMsg }
                            )
                        }
                        Text("Disease: ${r.diseaseName}", color = Slate200)
                        r.cropName?.let { Text("Crop: $it", color = Slate300) }
                        r.severity?.let { Text("${stringResource(R.string.common_severity)}: $it", color = Slate300) }
                        r.confidence?.let { Text("Confidence: ${"%.1f".format(it * 100)}%", color = Slate300) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${stringResource(R.string.common_source)}: ${r.source ?: "unknown"}", color = Slate500, style = MaterialTheme.typography.bodySmall)
                            if (r.source == "mock") {
                                Spacer(Modifier.width(8.dp))
                                Badge(containerColor = Amber500) { Text("Demo data", color = Color.Black) }
                            }
                        }
                        r.summary?.let { Spacer(Modifier.height(4.dp)); Text(it, color = Slate200) }

                        if (r.treatment.organic.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.crop_organic), style = MaterialTheme.typography.titleSmall, color = Emerald300)
                            r.treatment.organic.forEach { Text("• $it", color = Slate300, style = MaterialTheme.typography.bodySmall) }
                        }
                        if (r.treatment.chemical.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.crop_chemical), style = MaterialTheme.typography.titleSmall, color = Amber500)
                            r.treatment.chemical.forEach { Text("• $it", color = Slate300, style = MaterialTheme.typography.bodySmall) }
                        }
                        if (r.prevention.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.crop_prevention), style = MaterialTheme.typography.titleSmall, color = Blue500)
                            r.prevention.forEach { Text("• $it", color = Slate300, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Past Diagnoses (${history.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = Slate200,
                    fontWeight = FontWeight.SemiBold
                )
                history.forEach { item ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate800),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.diseaseName, fontWeight = FontWeight.Bold, color = Emerald400)
                                item.severity?.let {
                                    Text(it, color = Slate400, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            item.cropName?.let { Text("Crop: $it", color = Slate300, style = MaterialTheme.typography.bodySmall) }
                            item.summary?.let { Text(it, color = Slate400, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}
