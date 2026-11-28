package com.kissansevaai.feature.crop

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.kissansevaai.R
import com.kissansevaai.ui.component.FeatureScaffold
import com.kissansevaai.ui.component.FillByVoiceButton
import com.kissansevaai.ui.component.LoadingOverlay
import com.kissansevaai.ui.component.VoiceInputField
import com.kissansevaai.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CropDiseaseScreen(onBack: () -> Unit, viewModel: CropViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    val savedFarm by viewModel.savedFarmLocation.collectAsState()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var cropNameInput by remember { mutableStateOf("") }
    var symptomDescriptionInput by remember { mutableStateOf("") }
    var location by remember(savedFarm) { mutableStateOf(savedFarm?.formattedSummary.orEmpty()) }
    var soilType by remember(savedFarm) { mutableStateOf(savedFarm?.soilType.orEmpty()) }
    var treatmentTab by remember { mutableStateOf(0) } // 0 = Organic, 1 = Chemical

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
            title = { Text("Select Crop Photo", fontWeight = FontWeight.Bold, color = TextCharcoal) },
            text = { Text("Take a live leaf photo with Camera or select an existing picture from your gallery.", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        showSourceChooser = false
                        onChooseCamera()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                    shape = RoundedCornerShape(9999.dp)
                ) { Text("Camera", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showSourceChooser = false
                        permissionNotice = null
                        galleryLauncher.launch("image/*")
                    },
                    shape = RoundedCornerShape(9999.dp)
                ) { Text("Gallery", color = DeepEmerald) }
            },
            containerColor = CardWhite
        )
    }

    FeatureScaffold(
        title = stringResource(R.string.crop_title),
        subtitle = "AI-powered leaf disease diagnosis & care",
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            permissionNotice?.let { notice ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AmberTint),
                    border = BorderStroke(1.dp, HarvestAmber.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = notice,
                        color = SecondaryOrange,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Image Upload Zone
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.5.dp, if (selectedUri != null) DeepEmerald else BorderMedium),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clickable { showSourceChooser = true },
            ) {
                if (selectedUri != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = selectedUri,
                            contentDescription = "Selected leaf",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(R.drawable.ic_image_placeholder),
                            error = painterResource(R.drawable.ic_broken_image),
                        )
                        Surface(
                            shape = CircleShape,
                            color = CardWhite,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .clickable { selectedUri = null }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = ErrorRed,
                                modifier = Modifier.padding(6.dp).size(18.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = GreenTintLight,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = DeepEmerald,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.crop_upload_photo),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextCharcoal
                            )
                            Text(
                                text = stringResource(R.string.crop_take_photo_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            // Quick source action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onChooseCamera,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(9999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Camera", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        permissionNotice = null
                        galleryLauncher.launch("image/*")
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(9999.dp),
                    border = BorderStroke(1.5.dp, DeepEmerald)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = DeepEmerald, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Gallery", color = DeepEmerald, fontWeight = FontWeight.Bold)
                }
            }

            // Quick Test Sample Leaves
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = GreenBg),
                border = BorderStroke(1.dp, GreenTint),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.crop_sample_test_label),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = DeepEmerald
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val samples = listOf(
                            Triple("Wheat", "Yellow Rust", "Alluvial Loam"),
                            Triple("Tomato", "Early Blight", "Red Loam"),
                            Triple("Cotton", "Leaf Curl", "Black Cotton")
                        )
                        samples.forEach { (crop, disease, soil) ->
                            Surface(
                                shape = RoundedCornerShape(9999.dp),
                                color = CardWhite,
                                border = BorderStroke(1.dp, BorderMedium),
                                modifier = Modifier.weight(1f).clickable {
                                    val uri = viewModel.createSampleLeafUri(crop, disease)
                                    selectedUri = uri
                                    cropNameInput = crop
                                    symptomDescriptionInput = "$crop $disease sample check"
                                    location = savedFarm?.formattedSummary.orEmpty()
                                    soilType = savedFarm?.soilType?.ifBlank { soil } ?: soil
                                    permissionNotice = null
                                }
                            ) {
                                Text(
                                    text = "$crop $disease",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextCharcoal,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // Form inputs
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val currentLang by viewModel.language.collectAsState()

                    FillByVoiceButton(
                        fields = listOf("location", "soilType"),
                        fieldLabels = mapOf(
                            "location" to "Farm Location",
                            "soilType" to "Soil Type",
                        ),
                        lang = currentLang,
                        speechToText = viewModel.speechToText,
                        fieldExtractor = viewModel.fieldExtractor,
                        onFill = { data ->
                            data["location"]?.let { location = it }
                            data["soilType"]?.let { soilType = it }
                        },
                        onMessage = { msg -> permissionNotice = msg },
                        modifier = Modifier.fillMaxWidth()
                    )

                    VoiceInputField(
                        value = location,
                        onValueChange = { location = it },
                        label = stringResource(R.string.common_location),
                        fieldKey = "location",
                        lang = currentLang,
                        speechToText = viewModel.speechToText,
                        placeholder = "e.g. Saved farm village / district",
                        leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = DeepEmerald) },
                        onMessage = { permissionNotice = it },
                    )

                    VoiceInputField(
                        value = soilType,
                        onValueChange = { soilType = it },
                        label = stringResource(R.string.common_soil_type),
                        fieldKey = "soilType",
                        lang = currentLang,
                        speechToText = viewModel.speechToText,
                        placeholder = "e.g. Alluvial / Black Cotton",
                        leadingIcon = { Icon(Icons.Default.Grass, null, tint = DeepEmerald) },
                        onMessage = { permissionNotice = it },
                    )
                }
            }

            // Analyze Crop CTA Button
            Button(
                onClick = {
                    selectedUri?.let {
                        viewModel.analyze(
                            imageUri = it,
                            location = location,
                            soilType = soilType,
                            cropHint = "",
                            symptomDescription = "",
                        )
                    }
                },
                enabled = selectedUri != null && !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(9999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = null)
                        Text(
                            text = "🔬 " + stringResource(R.string.crop_detect_btn),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            LoadingOverlay(state.isLoading)

            state.error?.let {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = it,
                        color = OnErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Diagnosis Result Card (Minimalist: Best Answer + Treatment Tips)
            state.result?.let { r ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = BorderStroke(1.dp, GreenTint),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Clean Header: Best Answer label + Read Aloud TTS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(9999.dp),
                                color = GreenTint
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = DeepEmerald,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.crop_analysis_result),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DeepEmerald
                                    )
                                }
                            }

                            val currentLang by viewModel.language.collectAsState()
                            val textToSpeak = remember(r, currentLang) {
                                val cropPrefix = when (currentLang.lowercase().trim()) {
                                    "hi" -> "फसल"
                                    "mr" -> "पीक"
                                    "pa" -> "ਫ਼ਸਲ"
                                    "bho" -> "फसल"
                                    "sa" -> "सस्यम्"
                                    else -> "Crop"
                                }
                                val diseasePrefix = when (currentLang.lowercase().trim()) {
                                    "hi" -> "रोग"
                                    "mr" -> "रोग"
                                    "pa" -> "ਬਿਮਾਰੀ"
                                    "bho" -> "रोग"
                                    "sa" -> "रोगः"
                                    else -> "Disease"
                                }
                                val delimiter = if (currentLang.lowercase().trim() in listOf("hi", "pa", "bho", "sa")) "।" else "."
                                val cropPart = if (!r.cropName.isNullOrBlank()) "$cropPrefix: ${r.cropName}$delimiter " else ""
                                val summaryPart = r.summary ?: ""
                                "$diseasePrefix: ${r.diseaseName}$delimiter $cropPart$summaryPart".trim()
                            }
                            com.kissansevaai.ui.component.TTSButton(
                                ttsEngine = viewModel.ttsEngine,
                                textToSpeak = textToSpeak,
                                lang = currentLang,
                                onFallbackExplanation = { fallbackMsg -> permissionNotice = fallbackMsg }
                            )
                        }

                        // Best Answer: Disease & Crop
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = r.diseaseName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Medium,
                                color = TextCharcoal
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                r.cropName?.let {
                                    Text("${stringResource(R.string.crop_label_prefix)} $it", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                                }
                                r.severity?.let {
                                    Surface(
                                        shape = RoundedCornerShape(9999.dp),
                                        color = AmberTint
                                    ) {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SecondaryOrange,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        r.summary?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextCharcoal,
                                lineHeight = 22.sp
                            )
                        }

                        // Treatment Tips (Organic & Chemical)
                        if (r.treatment.organic.isNotEmpty() || r.treatment.chemical.isNotEmpty()) {
                            HorizontalDivider(color = BorderLight)
                            Text(
                                text = "Treatment Tips",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextCharcoal
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (treatmentTab == 0) DeepEmerald else Color.Transparent,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { treatmentTab = 0 }
                                    ) {
                                        Text(
                                            text = stringResource(R.string.crop_organic_treatment),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (treatmentTab == 0) Color.White else TextMuted,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (treatmentTab == 1) DeepEmerald else Color.Transparent,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { treatmentTab = 1 }
                                    ) {
                                        Text(
                                            text = stringResource(R.string.crop_chemical_treatment),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (treatmentTab == 1) Color.White else TextMuted,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            // Treatment Checklist
                            if (treatmentTab == 0) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    r.treatment.organic.forEach { item ->
                                        Row(
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, null, tint = DeepEmerald, modifier = Modifier.size(18.dp))
                                            Text(item, style = MaterialTheme.typography.bodyMedium, color = TextCharcoal)
                                        }
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    r.treatment.chemical.forEach { item ->
                                        Row(
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.Science, null, tint = HarvestAmber, modifier = Modifier.size(18.dp))
                                            Text(item, style = MaterialTheme.typography.bodyMedium, color = TextCharcoal)
                                        }
                                    }
                                }
                            }
                        }

                        // Prevention Tips
                        if (r.prevention.isNotEmpty()) {
                            HorizontalDivider(color = BorderLight)
                            Text(
                                text = stringResource(R.string.crop_prevention_strategies),
                                style = MaterialTheme.typography.titleSmall,
                                color = TextCharcoal
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                r.prevention.forEach { item ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Shield, null, tint = Blue500, modifier = Modifier.size(16.dp))
                                        Text(item, style = MaterialTheme.typography.bodySmall, color = TextCharcoal)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Past diagnoses history
            if (history.isNotEmpty()) {
                Text(
                    text = "Past Diagnoses (${history.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextCharcoal
                )
                history.forEach { item ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        border = BorderStroke(1.dp, BorderLight),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.diseaseName, fontWeight = FontWeight.Bold, color = DeepEmerald, modifier = Modifier.weight(1f))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (item.needs_review) {
                                        Surface(shape = RoundedCornerShape(9999.dp), color = AmberTint) {
                                            Text(
                                                text = stringResource(R.string.crop_status_needs_review),
                                                color = SecondaryOrange,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                fontSize = 10.sp
                                            )
                                        }
                                    } else {
                                        item.severity?.let {
                                            Surface(shape = RoundedCornerShape(9999.dp), color = AmberTint) {
                                                Text(
                                                    it,
                                                    color = SecondaryOrange,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                item.cropName?.let { Text("${stringResource(R.string.crop_label_prefix)} $it", color = TextMuted, style = MaterialTheme.typography.bodySmall) }
                                item.top_cosine_similarity?.let { sim ->
                                    Text("Sim: ${"%.3f".format(sim)}", color = DeepEmerald, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            item.summary?.let { Text(it, color = TextCharcoal, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}
