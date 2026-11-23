package com.kissansevaai.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kissansevaai.core.ai.TextToSpeechEngine
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.model.MarketPrice
import com.kissansevaai.core.model.SensorData
import com.kissansevaai.core.repository.IoTRepository
import com.kissansevaai.core.repository.PricesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dataStore: DataStoreManager,
    val ttsEngine: TextToSpeechEngine,
    private val iotRepository: IoTRepository,
    private val pricesRepository: PricesRepository,
) : ViewModel() {

    val language: StateFlow<String> = dataStore.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    private val _sensorData = MutableStateFlow(
        SensorData(temperature = 27.8, humidity = 64.0, soilMoisture = 45.0, phLevel = 6.7)
    )
    val sensorData: StateFlow<SensorData> = _sensorData

    private val _mandiPrices = MutableStateFlow<List<MarketPrice>>(emptyList())
    val mandiPrices: StateFlow<List<MarketPrice>> = _mandiPrices

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            iotRepository.getLiveSensorData().collect { res ->
                if (res is Resource.Success && res.data != null) {
                    _sensorData.value = res.data
                }
            }
        }
        viewModelScope.launch {
            pricesRepository.getMarketPrices().collect { res ->
                if (res is Resource.Success && res.data.isNotEmpty()) {
                    _mandiPrices.value = res.data.take(4)
                }
            }
        }
    }

    fun toggleLanguage() {
        viewModelScope.launch {
            val current = dataStore.language.first()
            val next = when (current) {
                "en" -> "hi"
                "hi" -> "bho"
                "bho" -> "mr"
                "mr" -> "pa"
                else -> "en"
            }
            dataStore.setLanguage(next)
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            dataStore.setLanguage(lang)
        }
    }

    fun speakWeatherAdvisory(text: String) {
        viewModelScope.launch {
            val lang = dataStore.language.first()
            _isSpeaking.value = true
            ttsEngine.speak(
                text = text,
                langCode = lang,
                onStart = { _isSpeaking.value = true },
                onDone = { _isSpeaking.value = false },
                onError = { _isSpeaking.value = false }
            )
        }
    }
}

