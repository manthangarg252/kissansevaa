package com.kissansevaai.feature.crop

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.model.CropAnalysisResult
import com.kissansevaai.core.repository.CropRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.kissansevaai.core.ai.FieldExtractor
import com.kissansevaai.core.ai.SpeechToText
import com.kissansevaai.core.ai.TextToSpeechEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
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
}

data class CropUiState(
    val isLoading: Boolean = false,
    val result: CropAnalysisResult? = null,
    val error: String? = null,
)
