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
import com.kissansevaai.core.ai.FieldExtractor
import com.kissansevaai.core.ai.SpeechToText
import com.kissansevaai.core.ai.TextToSpeechEngine
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.model.LivestockIntakeFields
import com.kissansevaai.core.model.LivestockResult
import com.kissansevaai.core.repository.LivestockRepository
import com.kissansevaai.ui.component.CameraCaptureDialog
import com.kissansevaai.ui.component.FeatureScaffold
import com.kissansevaai.ui.component.LoadingOverlay
import com.kissansevaai.ui.component.MicButton
import com.kissansevaai.ui.component.TTSButton
import com.kissansevaai.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

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

    private val _history = MutableStateFlow<List<LivestockResult>>(emptyList())
    val history: StateFlow<List<LivestockResult>> = _history

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            repository.getHistory().collect { res ->
                if (res is Resource.Success) {
                    _history.value = res.data
                }
            }
        }
    }

    fun analyzeDetailed(imageUri: Uri?, intake: LivestockIntakeFields) {
        viewModelScope.launch {
            _uiState.value = LivestockUiState(isLoading = true)
            try {
                val lang = dataStore.language.first()
                val imagePart = imageUri?.let { uri ->
                    val bytes = com.kissansevaai.core.util.ImageCompressor.compressImage(context, uri)
                    MultipartBody.Part.createFormData(
                        "image", "animal.jpg", bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                    )
                }
                val textMime = "text/plain".toMediaTypeOrNull()

                repository.analyzeLivestockDetailed(
                    image = imagePart,
                    animalType = intake.animalType.toRequestBody(textMime),
                    animalAge = intake.animalAge.toRequestBody(textMime),
                    symptomDuration = intake.symptomDuration.toRequestBody(textMime),
                    feverStatus = intake.feverStatus.toRequestBody(textMime),
                    appetiteStatus = intake.appetiteStatus.toRequestBody(textMime),
                    milkYieldChange = intake.milkYieldChange.toRequestBody(textMime),
                    lesionsOrUlcers = intake.lesionsOrUlcers.toString().toRequestBody(textMime),
                    isolatedFromHerd = intake.isolatedFromHerd.toString().toRequestBody(textMime),
                    symptomsNotes = intake.symptomsNotes.toRequestBody(textMime),
                    language = lang.toRequestBody(textMime),
                ).collect { res ->
                    when (res) {
                        is Resource.Loading -> _uiState.value = LivestockUiState(isLoading = true)
                        is Resource.Success -> {
                            _uiState.value = LivestockUiState(result = res.data)
                            loadHistory()
                        }
                        is Resource.Error -> _uiState.value = LivestockUiState(error = res.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = LivestockUiState(error = e.localizedMessage ?: "Analysis failed")
            }
        }
    }
}

data class LivestockUiState(
    val isLoading: Boolean = false,
    val result: LivestockResult? = null,
    val error: String? = null,
)

@Composable
fun LivestockScreen(onBack: () -> Unit, viewModel: LivestockViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    val currentLang by viewModel.language.collectAsState()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var animalType by remember { mutableStateOf("Cattle") }
    var animalAge by remember { mutableStateOf("Adult") }
    var symptomDuration by remember { mutableStateOf("1-3 days") }
    var feverStatus by remember { mutableStateOf("unknown") }
    var appetiteStatus by remember { mutableStateOf("normal") }
    var milkYieldChange by remember { mutableStateOf("normal") }
    var lesionsOrUlcers by remember { mutableStateOf(false) }
    var isolatedFromHerd by remember { mutableStateOf(false) }
    var symptomsNotes by remember { mutableStateOf("") }

    var showSourceChooser by remember { mutableStateOf(false) }
    var showCameraDialog by remember { mutableStateOf(false) }
    var permissionNotice by remember { mutableStateOf<String?>(null) }

    val commonAnimals = listOf("Cattle", "Buffalo", "Goat", "Sheep", "Poultry")
    val durations = listOf("< 24h", "1-3 days", "3+ days")
    val yesNoOpts = listOf("yes" to "Yes", "no" to "No", "unknown" to "Unsure")
    val appetiteOpts = listOf("normal" to "Normal", "reduced" to "Reduced", "none" to "None")
    val milkOpts = listOf("normal" to "Normal", "dropped" to "Dropped", "stopped" to "Stopped", "na" to "N/A")

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
        CameraCaptureDialog(
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
                    onClick = { showSourceChooser = false; onChooseCamera() },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                    shape = RoundedCornerShape(9999.dp)
                ) { Text("Camera", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSourceChooser = false; permissionNotice = null; galleryLauncher.launch("image/*") },
                    shape = RoundedCornerShape(9999.dp)
                ) { Text("Gallery", color = DeepEmerald) }
            },
            containerColor = CardWhite
        )
    }

    FeatureScaffold(
        title = stringResource(R.string.livestock_title),
        subtitle = "पशुपालन एवं पशु स्वास्थ्य सुरक्षा जांच",
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            permissionNotice?.let { notice ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AmberTint),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(notice, color = SecondaryOrange, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }

            // Optional Photo Upload Zone (never auto-substitutes synthetic fake photos!)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.5.dp, if (selectedUri != null) DeepEmerald else BorderMedium),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
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
                            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clickable { selectedUri = null }
                        ) {
                            Icon(Icons.Default.Close, "Clear", tint = ErrorRed, modifier = Modifier.padding(6.dp).size(18.dp))
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Pets, null, tint = HarvestAmber, modifier = Modifier.size(34.dp))
                            Text(stringResource(R.string.livestock_upload_photo), fontWeight = FontWeight.Bold, color = TextCharcoal)
                            Text("Optional — attach lesion/animal photo OR submit symptom intake", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onChooseCamera,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(9999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald)
                ) {
                    Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Camera", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(9999.dp),
                    border = BorderStroke(1.5.dp, DeepEmerald)
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, tint = DeepEmerald, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gallery", color = DeepEmerald, fontWeight = FontWeight.Bold)
                }
            }

            // Structured Clinical Intake Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.livestock_category_label), fontWeight = FontWeight.Bold, color = TextCharcoal)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        commonAnimals.forEach { sp ->
                            FilterChip(selected = animalType == sp, onClick = { animalType = sp }, label = { Text(sp) })
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = animalAge,
                            onValueChange = { animalAge = it },
                            label = { Text("Age / Group") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        OutlinedTextField(
                            value = symptomDuration,
                            onValueChange = { symptomDuration = it },
                            label = { Text("Duration") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }

                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        durations.forEach { d ->
                            FilterChip(selected = symptomDuration == d, onClick = { symptomDuration = d }, label = { Text(d) })
                        }
                    }

                    Text("Fever?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextCharcoal)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        yesNoOpts.forEach { (k, lbl) ->
                            FilterChip(selected = feverStatus == k, onClick = { feverStatus = k }, label = { Text(lbl) })
                        }
                    }

                    Text("Appetite & Feed Intake", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextCharcoal)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        appetiteOpts.forEach { (k, lbl) ->
                            FilterChip(selected = appetiteStatus == k, onClick = { appetiteStatus = k }, label = { Text(lbl) })
                        }
                    }

                    Text("Milk Yield Change", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextCharcoal)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        milkOpts.forEach { (k, lbl) ->
                            FilterChip(selected = milkYieldChange == k, onClick = { milkYieldChange = k }, label = { Text(lbl) })
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Visible lesions / blisters / ulcers", style = MaterialTheme.typography.bodySmall, color = TextCharcoal)
                        Switch(checked = lesionsOrUlcers, onCheckedChange = { lesionsOrUlcers = it })
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Already isolated from healthy herd", style = MaterialTheme.typography.bodySmall, color = TextCharcoal)
                        Switch(checked = isolatedFromHerd, onCheckedChange = { isolatedFromHerd = it })
                    }

                    OutlinedTextField(
                        value = symptomsNotes,
                        onValueChange = { symptomsNotes = it },
                        label = { Text("Symptom notes (cough, lameness, swelling...)") },
                        trailingIcon = {
                            MicButton(
                                speechToText = viewModel.speechToText,
                                lang = currentLang,
                                onResult = { symptomsNotes = if (symptomsNotes.isBlank()) it else "$symptomsNotes, $it" },
                                onExplanation = { permissionNotice = it },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }

            // Submit Button
            val canAnalyze = selectedUri != null || symptomsNotes.isNotBlank() || lesionsOrUlcers || feverStatus == "yes"
            Button(
                onClick = {
                    viewModel.analyzeDetailed(
                        selectedUri,
                        LivestockIntakeFields(
                            animalType = animalType,
                            animalAge = animalAge,
                            symptomDuration = symptomDuration,
                            feverStatus = feverStatus,
                            appetiteStatus = appetiteStatus,
                            milkYieldChange = milkYieldChange,
                            lesionsOrUlcers = lesionsOrUlcers,
                            isolatedFromHerd = isolatedFromHerd,
                            symptomsNotes = symptomsNotes,
                        )
                    )
                },
                enabled = !state.isLoading && canAnalyze,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(9999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                } else {
                    Text("🐾 " + stringResource(R.string.livestock_run_diagnostics), fontWeight = FontWeight.Bold)
                }
            }

            LoadingOverlay(state.isLoading)

            state.error?.let {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("AI Veterinary Triage Unavailable", fontWeight = FontWeight.Bold, color = ErrorRed)
                        Text(text = it, color = OnErrorContainer, style = MaterialTheme.typography.bodySmall)
                        Text("For urgent symptoms (fever + ulcers, bleeding, collapse), call 1962 immediately.", style = MaterialTheme.typography.labelSmall, color = OnErrorContainer)
                    }
                }
            }

            // Result Card
            state.result?.let { r ->
                if (r.urgentVetEscalation) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = ErrorContainer),
                        border = BorderStroke(1.5.dp, ErrorRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("URGENT VETERINARY ESCALATION RECOMMENDED", fontWeight = FontWeight.ExtraBold, color = ErrorRed)
                            r.escalationReason?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = OnErrorContainer)
                            }
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = BorderStroke(1.5.dp, GreenTint),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(9999.dp), color = GreenTint) {
                                Text(
                                    text = "${stringResource(R.string.livestock_diagnosed_result)} • ${r.severity ?: "Screened"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = DeepEmerald,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            TTSButton(
                                ttsEngine = viewModel.ttsEngine,
                                textToSpeak = listOfNotNull(r.conditionName, r.immediateAction, r.vetConsultationAdvice).joinToString(". "),
                                lang = currentLang,
                                onFallbackExplanation = { permissionNotice = it },
                            )
                        }

                        r.conditionName?.let {
                            Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = TextCharcoal)
                        } ?: run {
                            if (!r.aiAvailable) {
                                Surface(shape = RoundedCornerShape(10.dp), color = AmberTint, border = BorderStroke(1.dp, SecondaryOrange)) {
                                    Text(
                                        text = "AI Veterinary Vision Unavailable (${r.serviceStatus ?: "service_unavailable"}): ${r.error ?: "Showing deterministic emergency rule triage only."}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SecondaryOrange,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }

                        r.safetyNotice?.let { safety ->
                            Surface(shape = RoundedCornerShape(10.dp), color = AmberTint, border = BorderStroke(1.dp, SecondaryOrange)) {
                                Text(safety, style = MaterialTheme.typography.labelSmall, color = SecondaryOrange, modifier = Modifier.padding(10.dp))
                            }
                        }

                        r.immediateAction?.let {
                            Text("Immediate Care: $it", style = MaterialTheme.typography.bodyMedium, color = TextCharcoal)
                        }
                        r.longTermCare?.let {
                            Text("Supportive Care: $it", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                        r.vetConsultationAdvice?.let {
                            Text("Vet Advice: $it", style = MaterialTheme.typography.bodySmall, color = DeepEmerald, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                Text("Recent Livestock Triage (${history.size})", fontWeight = FontWeight.Bold, color = TextCharcoal)
                history.take(5).forEach { h ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${h.animalType ?: "Livestock"}: ${h.conditionName ?: "Screened"}", fontWeight = FontWeight.Bold, color = DeepEmerald)
                                if (h.urgentVetEscalation) {
                                    Text("URGENT", style = MaterialTheme.typography.labelSmall, color = ErrorRed, fontWeight = FontWeight.Bold)
                                }
                            }
                            h.immediateAction?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted) }
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
                        Text(stringResource(R.string.livestock_call_centre), color = TextMuted, style = MaterialTheme.typography.bodySmall)
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
