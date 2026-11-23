package com.kissansevaai.feature.livestock

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.kissansevaai.R
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.model.LivestockResult
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.repository.LivestockRepository
import com.kissansevaai.ui.component.FeatureScaffold
import com.kissansevaai.ui.component.LoadingOverlay
import com.kissansevaai.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import com.kissansevaai.core.ai.FieldExtractor
import com.kissansevaai.core.ai.SpeechToText
import com.kissansevaai.core.ai.TextToSpeechEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class LivestockViewModel @Inject constructor(
    private val repository: LivestockRepository,
    private val dataStore: DataStoreManager,
    @ApplicationContext private val context: Context,
    val speechToText: SpeechToText,
    val fieldExtractor: FieldExtractor,
    val ttsEngine: TextToSpeechEngine,
) : ViewModel() {
    val language: StateFlow<String> = dataStore.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    private val _uiState = MutableStateFlow(LivestockUiState())
    val uiState: StateFlow<LivestockUiState> = _uiState

    fun createSampleAnimalUri(animal: String): Uri {
        val fileName = "sample_animal_${animal.lowercase().replace(" ", "_")}.jpg"
        val file = java.io.File(context.cacheDir, fileName)
        if (!file.exists()) {
            val bitmap = android.graphics.Bitmap.createBitmap(500, 500, android.graphics.Bitmap.Config.RGB_565)
            val canvas = android.graphics.Canvas(bitmap)
            val bgPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(180, 140, 100) }
            canvas.drawRect(0f, 0f, 500f, 500f, bgPaint)
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 32f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.drawText("Livestock: $animal", 250f, 250f, textPaint)
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
        }
        return Uri.fromFile(file)
    }

    fun analyze(imageUri: Uri, animalType: String) {
        viewModelScope.launch {
            _uiState.value = LivestockUiState(isLoading = true)
            try {
                val lang = dataStore.language.first()
                val bytes = com.kissansevaai.core.util.ImageCompressor.compressImage(context, imageUri)
                val part = MultipartBody.Part.createFormData("image", "animal.jpg", bytes.toRequestBody("image/jpeg".toMediaTypeOrNull()))
                repository.analyzeLivestock(
                    image = part,
                    animalType = animalType.toRequestBody("text/plain".toMediaTypeOrNull()),
                    language = lang.toRequestBody("text/plain".toMediaTypeOrNull())
                ).collect { res ->
                    when (res) {
                        is Resource.Loading -> _uiState.value = LivestockUiState(isLoading = true)
                        is Resource.Success -> _uiState.value = LivestockUiState(result = res.data)
                        is Resource.Error -> _uiState.value = LivestockUiState(error = res.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = LivestockUiState(error = e.localizedMessage ?: "Analysis failed")
            }
        }
    }
}
data class LivestockUiState(val isLoading: Boolean = false, val result: LivestockResult? = null, val error: String? = null)

@Composable
fun LivestockScreen(onBack: () -> Unit, viewModel: LivestockViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var animalType by remember { mutableStateOf("Cow (गाय)") }
    var showSourceChooser by remember { mutableStateOf(false) }
    var showCameraDialog by remember { mutableStateOf(false) }
    var permissionNotice by remember { mutableStateOf<String?>(null) }

    val commonAnimals = listOf("Cow (गाय)", "Buffalo (भैंस)", "Goat (बकरी)", "Sheep (भेड़)", "Poultry (मुर्गी)")

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
            title = { Text("Select Livestock Photo", fontWeight = FontWeight.Bold, color = TextCharcoal) },
            text = { Text("Take an animal health photo with Camera or choose from gallery.", color = TextMuted) },
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
        title = stringResource(R.string.livestock_title),
        subtitle = "पशुपालन एवं पशु स्वास्थ्य परामर्श",
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
                            contentDescription = "Animal",
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
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = AmberTint,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Pets,
                                        contentDescription = null,
                                        tint = HarvestAmber,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Text("Upload Animal Photo", fontWeight = FontWeight.Bold, color = TextCharcoal)
                            Text("Take photo of symptoms or injury", color = TextMuted, style = MaterialTheme.typography.bodySmall)
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
                    Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(18.dp))
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
                    Icon(Icons.Default.PhotoLibrary, null, tint = DeepEmerald, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Gallery", color = DeepEmerald, fontWeight = FontWeight.Bold)
                }
            }

            var selectedSymptoms by remember { mutableStateOf(setOf<String>()) }

            // Quick Test Sample Animals
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AmberTint.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, HarvestAmber.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Or Test with Sample Animal (त्वरित पशु नमूना):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = HarvestAmber
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val samples = listOf("Cow (FMD)", "Buffalo (Mastitis)", "Goat (Skin)")
                        samples.forEach { sample ->
                            Surface(
                                shape = RoundedCornerShape(9999.dp),
                                color = CardWhite,
                                border = BorderStroke(1.dp, BorderMedium),
                                modifier = Modifier.weight(1f).clickable {
                                    val cleanName = sample.substringBefore(" ")
                                    animalType = cleanName
                                    selectedUri = viewModel.createSampleAnimalUri(sample)
                                    permissionNotice = null
                                }
                            ) {
                                Text(
                                    text = sample,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextCharcoal,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // Animal category chip group
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Animal Category (पशु का प्रकार)", fontWeight = FontWeight.Bold, color = TextCharcoal)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        commonAnimals.take(3).forEach { animal ->
                            Surface(
                                shape = RoundedCornerShape(9999.dp),
                                color = if (animalType == animal) GreenTint else SurfaceContainerLow,
                                border = BorderStroke(1.dp, if (animalType == animal) DeepEmerald else BorderMedium),
                                modifier = Modifier.clickable { animalType = animal }
                            ) {
                                Text(
                                    text = animal,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (animalType == animal) DeepEmerald else TextCharcoal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = animalType,
                        onValueChange = { animalType = it },
                        label = { Text(stringResource(R.string.livestock_category)) },
                        leadingIcon = { Icon(Icons.Default.Pets, null, tint = DeepEmerald) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepEmerald,
                            unfocusedBorderColor = BorderMedium
                        )
                    )

                    // Symptoms checklist
                    Text("Observed Symptoms (देखे गए लक्षण):", fontWeight = FontWeight.Bold, color = TextCharcoal, style = MaterialTheme.typography.labelMedium)
                    val symptomOptions = listOf(
                        "Fever (तेज बुखार)",
                        "Mouth/Hoof Blisters (छाले)",
                        "Loss of Appetite (भूख न लगना)",
                        "Reduced Milk (दूध में कमी)",
                        "Lameness (लंगड़ाना)"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        symptomOptions.forEach { symptom ->
                            val isChecked = selectedSymptoms.contains(symptom)
                            Surface(
                                shape = RoundedCornerShape(9999.dp),
                                color = if (isChecked) AmberTint else SurfaceContainerLow,
                                border = BorderStroke(1.dp, if (isChecked) HarvestAmber else BorderMedium),
                                modifier = Modifier.clickable {
                                    val updated = selectedSymptoms.toMutableSet()
                                    if (isChecked) updated.remove(symptom) else updated.add(symptom)
                                    selectedSymptoms = updated
                                }
                            ) {
                                Text(
                                    text = symptom,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isChecked) SecondaryOrange else TextCharcoal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Analyze button
            Button(
                onClick = {
                    val uri = selectedUri ?: viewModel.createSampleAnimalUri(animalType)
                    val fullType = if (selectedSymptoms.isNotEmpty()) {
                        "$animalType showing symptoms: ${selectedSymptoms.joinToString(", ")}"
                    } else {
                        animalType
                    }
                    viewModel.analyze(uri, fullType)
                },
                enabled = !state.isLoading && animalType.isNotBlank(),
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
                        Icon(Icons.Default.MedicalServices, contentDescription = null)
                        Text(
                            text = "🐾 " + stringResource(R.string.livestock_run_diagnostics),
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
                    Text(text = it, color = OnErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }

            // Livestock Result Card
            state.result?.let { r ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = BorderStroke(1.5.dp, GreenTint),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = RoundedCornerShape(9999.dp), color = GreenTint) {
                                Text(
                                    text = "Diagnosed Result",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = DeepEmerald,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            val currentLang by viewModel.language.collectAsState()
                            com.kissansevaai.ui.component.TTSButton(
                                ttsEngine = viewModel.ttsEngine,
                                textToSpeak = "${r.conditionName ?: ""}. Symptoms: ${r.symptoms.joinToString(", ")}. Immediate action: ${r.immediateAction ?: ""}. Long term care: ${r.longTermCare ?: ""}",
                                lang = currentLang,
                                onFallbackExplanation = { fallbackMsg -> permissionNotice = fallbackMsg }
                            )
                        }

                        r.conditionName?.let {
                            Text(it, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = TextCharcoal)
                        }

                        if (r.symptoms.isNotEmpty()) {
                            Text("Observed Symptoms (लक्षण):", fontWeight = FontWeight.Bold, color = TextCharcoal)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                r.symptoms.forEach { symptom ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.FiberManualRecord, null, tint = HarvestAmber, modifier = Modifier.size(10.dp).padding(top = 4.dp))
                                        Text(symptom, color = TextCharcoal, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }

                        // Immediate Action
                        r.immediateAction?.let {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AmberTint.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, HarvestAmber.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "🚨 " + stringResource(R.string.livestock_immediate_action),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SecondaryOrange
                                    )
                                    Text(it, color = TextCharcoal, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        // Long term care
                        r.longTermCare?.let {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = GreenBg,
                                border = BorderStroke(1.dp, GreenTint),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "🌿 " + stringResource(R.string.livestock_long_term),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = DeepEmerald
                                    )
                                    Text(it, color = TextCharcoal, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        // Vet advice
                        r.vetConsultationAdvice?.let {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceContainerLow,
                                border = BorderStroke(1.dp, BorderMedium),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "👨‍⚕️ " + stringResource(R.string.livestock_vet_advice),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextCharcoal
                                    )
                                    Text(it, color = TextCharcoal, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            // Veterinary Emergency Hotline
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Government Veterinary Helpline", fontWeight = FontWeight.Bold, color = TextCharcoal, style = MaterialTheme.typography.titleSmall)
                        Text("Toll-free Kisan Call Centre • 24x7 Assistance", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, Uri.parse("tel:1962"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                        shape = RoundedCornerShape(9999.dp)
                    ) {
                        Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("1962", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
