package com.kissansevaai.feature.livestock

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var animalType by remember { mutableStateOf("") }

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
            title = { Text("Select Livestock Photo") },
            text = { Text("Take a live animal photo with CameraX or select an existing picture from your gallery.") },
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

    FeatureScaffold(title = stringResource(R.string.livestock_title), onBack = onBack) { _ ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

            Card(
                shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth().height(200.dp).clickable { showSourceChooser = true },
            ) {
                if (selectedUri != null) AsyncImage(selectedUri, "Animal", Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CloudUpload, null, tint = Slate500, modifier = Modifier.size(48.dp))
                        Text("Upload animal photo", color = Slate500)
                        Spacer(Modifier.height(4.dp))
                        Text("Tap to choose Camera or Gallery", color = Emerald400, style = MaterialTheme.typography.bodySmall)
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

            OutlinedTextField(animalType, { animalType = it }, label = { Text(stringResource(R.string.livestock_category)) }, singleLine = true, modifier = Modifier.fillMaxWidth())

            val currentLang by viewModel.language.collectAsState()
            com.kissansevaai.ui.component.VoiceFormFillButton(
                fields = listOf("animalType"),
                lang = currentLang,
                speechToText = viewModel.speechToText,
                fieldExtractor = viewModel.fieldExtractor,
                onFill = { data ->
                    data["animalType"]?.let { animalType = it }
                },
                onMessage = { msg ->
                    permissionNotice = msg
                },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { selectedUri?.let { viewModel.analyze(it, animalType) } }, enabled = selectedUri != null && !state.isLoading, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Emerald600)) { Text(stringResource(R.string.livestock_run_diagnostics)) }
            LoadingOverlay(state.isLoading)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.result?.let { r ->
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Slate800), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.livestock_diag_result), style = MaterialTheme.typography.titleMedium, color = Emerald400)
                            com.kissansevaai.ui.component.TTSButton(
                                ttsEngine = viewModel.ttsEngine,
                                textToSpeak = "${r.conditionName ?: ""}. Symptoms: ${r.symptoms.joinToString(", ")}. Immediate action: ${r.immediateAction ?: ""}. Long term care: ${r.longTermCare ?: ""}",
                                lang = currentLang,
                                onFallbackExplanation = { fallbackMsg -> permissionNotice = fallbackMsg }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            r.conditionName?.let { Text("Condition: $it", color = Slate200) }
                            if (r.source == "mock") {
                                Spacer(Modifier.width(8.dp))
                                Badge(containerColor = Amber500) { Text("Demo data", color = Color.Black) }
                            }
                        }
                        r.confidence?.let { Text("Confidence: $it", color = Slate300) }
                        if (r.symptoms.isNotEmpty()) { Text("Symptoms:", color = Slate300); r.symptoms.forEach { Text("• $it", color = Slate400, style = MaterialTheme.typography.bodySmall) } }
                        r.immediateAction?.let { Spacer(Modifier.height(4.dp)); Text(stringResource(R.string.livestock_immediate_action), style = MaterialTheme.typography.titleSmall, color = Red400); Text(it, color = Slate200) }
                        r.longTermCare?.let { Spacer(Modifier.height(4.dp)); Text(stringResource(R.string.livestock_long_term), style = MaterialTheme.typography.titleSmall, color = Blue500); Text(it, color = Slate200) }
                        r.vetConsultationAdvice?.let { Spacer(Modifier.height(4.dp)); Text(stringResource(R.string.livestock_vet_advice), style = MaterialTheme.typography.titleSmall, color = Amber500); Text(it, color = Slate200) }
                    }
                }
            }
        }
    }
}
