package com.kissansevaai.feature.crop

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kissansevaai.core.ai.FieldExtractor
import com.kissansevaai.core.ai.SpeechToText
import com.kissansevaai.core.ai.TextToSpeechEngine
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.model.CropAnalysisResult
import com.kissansevaai.core.model.SecondOpinionResponse
import com.kissansevaai.core.repository.CropRepository
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
class CropViewModel @Inject constructor(
    private val repository: CropRepository,
    private val dataStore: DataStoreManager,
    @ApplicationContext private val context: Context,
    val speechToText: SpeechToText,
    val fieldExtractor: FieldExtractor,
    val ttsEngine: TextToSpeechEngine,
) : ViewModel() {

    val language: StateFlow<String> = dataStore.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    private val _uiState = MutableStateFlow(CropUiState())
    val uiState: StateFlow<CropUiState> = _uiState

    private val _history = MutableStateFlow<List<CropAnalysisResult>>(emptyList())
    val history: StateFlow<List<CropAnalysisResult>> = _history

    private var lastImageUri: Uri? = null
    private var lastLocation: String = "India"
    private var lastSoilType: String = "Loamy"

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

    fun createSampleLeafUri(crop: String, disease: String): Uri {
        val fileName = "sample_${crop.lowercase()}_${disease.lowercase().replace(" ", "_")}.jpg"
        val file = java.io.File(context.cacheDir, fileName)
        if (!file.exists()) {
            val bitmap = android.graphics.Bitmap.createBitmap(500, 500, android.graphics.Bitmap.Config.RGB_565)
            val canvas = android.graphics.Canvas(bitmap)
            val bgPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(34, 139, 34) }
            canvas.drawRect(0f, 0f, 500f, 500f, bgPaint)
            val spotPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(218, 165, 32) }
            canvas.drawCircle(180f, 150f, 45f, spotPaint)
            canvas.drawCircle(320f, 280f, 60f, spotPaint)
            canvas.drawCircle(220f, 380f, 35f, spotPaint)
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 30f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.drawText("$crop: $disease", 250f, 250f, textPaint)
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
        }
        return Uri.fromFile(file)
    }

    fun analyze(imageUri: Uri, location: String, soilType: String) {
        lastImageUri = imageUri
        lastLocation = location
        lastSoilType = soilType
        viewModelScope.launch {
            _uiState.value = CropUiState(isLoading = true)
            try {
                val lang = dataStore.language.first()
                val bytes = com.kissansevaai.core.util.ImageCompressor.compressImage(context, imageUri)
                val imagePart = MultipartBody.Part.createFormData(
                    "image", "leaf.jpg", bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                )

                repository.analyzeCrop(
                    image = imagePart,
                    location = location.toRequestBody("text/plain".toMediaTypeOrNull()),
                    soilType = soilType.toRequestBody("text/plain".toMediaTypeOrNull()),
                    language = lang.toRequestBody("text/plain".toMediaTypeOrNull()),
                ).collect { res ->
                    when (res) {
                        is Resource.Loading -> _uiState.value = CropUiState(isLoading = true)
                        is Resource.Success -> _uiState.value = CropUiState(result = res.data)
                        is Resource.Error -> _uiState.value = CropUiState(error = res.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = CropUiState(error = e.localizedMessage ?: "Failed to read image")
            }
        }
    }

    fun requestSecondOpinion(imageUri: Uri? = null, location: String? = null, soilType: String? = null) {
        val currentResult = _uiState.value.result ?: return
        val targetUri = imageUri ?: lastImageUri
        val targetLoc = location ?: lastLocation
        val targetSoil = soilType ?: lastSoilType

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSecondOpinionLoading = true, secondOpinionError = null)
            try {
                val lang = dataStore.language.first()
                val imagePart = targetUri?.let { uri ->
                    val bytes = com.kissansevaai.core.util.ImageCompressor.compressImage(context, uri)
                    MultipartBody.Part.createFormData(
                        "image", "leaf_second_opinion.jpg", bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                    )
                }

                repository.requestSecondOpinion(
                    image = imagePart,
                    analysisId = currentResult.id?.takeIf { !it.startsWith("temp_") }?.toRequestBody("text/plain".toMediaTypeOrNull()),
                    farmerConsented = "true".toRequestBody("text/plain".toMediaTypeOrNull()),
                    location = targetLoc.toRequestBody("text/plain".toMediaTypeOrNull()),
                    soilType = targetSoil.toRequestBody("text/plain".toMediaTypeOrNull()),
                    language = lang.toRequestBody("text/plain".toMediaTypeOrNull()),
                    primaryCrop = (currentResult.cropName ?: "").toRequestBody("text/plain".toMediaTypeOrNull()),
                    primaryDisease = currentResult.diseaseName.toRequestBody("text/plain".toMediaTypeOrNull()),
                ).collect { res ->
                    when (res) {
                        is Resource.Loading -> _uiState.value = _uiState.value.copy(isSecondOpinionLoading = true)
                        is Resource.Success -> {
                            val sec = res.data
                            val updatedPrimary = currentResult.copy(
                                secondOpinion = sec.secondOpinion,
                                reconciliationStatus = sec.reconciliationStatus,
                                reconciliationNote = sec.reconciliationNote,
                                treatmentWithheld = currentResult.treatmentWithheld || sec.treatmentWithheld,
                            )
                            _uiState.value = _uiState.value.copy(
                                isSecondOpinionLoading = false,
                                result = updatedPrimary,
                                secondOpinionResponse = sec,
                                secondOpinionError = if (!sec.ok) sec.error else null,
                            )
                        }
                        is Resource.Error -> {
                            _uiState.value = _uiState.value.copy(
                                isSecondOpinionLoading = false,
                                secondOpinionError = res.message,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSecondOpinionLoading = false,
                    secondOpinionError = e.localizedMessage ?: "Second opinion request failed",
                )
            }
        }
    }
}

data class CropUiState(
    val isLoading: Boolean = false,
    val result: CropAnalysisResult? = null,
    val error: String? = null,
    val isSecondOpinionLoading: Boolean = false,
    val secondOpinionResponse: SecondOpinionResponse? = null,
    val secondOpinionError: String? = null,
)
