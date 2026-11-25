package com.kissansevaai.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kissansevaai.core.ai.TextToSpeechEngine
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.model.MarketPrice
import com.kissansevaai.core.model.PlannerTaskDto
import com.kissansevaai.core.model.SensorData
import com.kissansevaai.core.model.WeatherForecast
import com.kissansevaai.core.repository.IoTRepository
import com.kissansevaai.core.repository.PlannerRepository
import com.kissansevaai.core.repository.PricesRepository
import com.kissansevaai.core.repository.WeatherRepository
import com.kissansevaai.core.sync.OfflineSyncManager
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
    private val weatherRepository: WeatherRepository,
    private val plannerRepository: PlannerRepository,
    private val offlineSyncManager: OfflineSyncManager,
) : ViewModel() {

    val language: StateFlow<String> = dataStore.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    val pendingSyncCount: StateFlow<Int> = offlineSyncManager.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _sensorData = MutableStateFlow(
        SensorData(temperature = 27.8, humidity = 64.0, soilMoisture = 45.0, phLevel = 6.7)
    )
    val sensorData: StateFlow<SensorData> = _sensorData

    private val _mandiPrices = MutableStateFlow<List<MarketPrice>>(emptyList())
    val mandiPrices: StateFlow<List<MarketPrice>> = _mandiPrices

    private val _weather = MutableStateFlow<WeatherForecast?>(null)
    val weather: StateFlow<WeatherForecast?> = _weather

    private val _upcomingTasks = MutableStateFlow<List<PlannerTaskDto>>(emptyList())
    val upcomingTasks: StateFlow<List<PlannerTaskDto>> = _upcomingTasks

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
        viewModelScope.launch {
            val lang = dataStore.language.first()
            weatherRepository.getForecast(language = lang, includeAi = false).collect { res ->
                if (res is Resource.Success) {
                    _weather.value = res.data
                }
            }
        }
        viewModelScope.launch {
            plannerRepository.getPlansAndTasks().collect { res ->
                if (res is Resource.Success) {
                    _upcomingTasks.value = res.data.tasks.filter { it.status == "pending" }.take(3)
                }
            }
        }
    }

    fun flushOfflineQueue() {
        viewModelScope.launch {
            offlineSyncManager.flushPendingOperations()
            loadData()
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
