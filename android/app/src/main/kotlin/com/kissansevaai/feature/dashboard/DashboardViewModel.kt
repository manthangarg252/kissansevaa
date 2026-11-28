package com.kissansevaai.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kissansevaai.core.ai.SpeechToText
import com.kissansevaai.core.ai.TextToSpeechEngine
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.location.DetectedLocation
import com.kissansevaai.core.location.LocationDetectionResult
import com.kissansevaai.core.location.SavedFarmLocation
import com.kissansevaai.core.model.MarketPrice
import com.kissansevaai.core.model.PlannerTaskDto
import com.kissansevaai.core.model.SensorData
import com.kissansevaai.core.model.WeatherForecast
import com.kissansevaai.core.repository.FarmLocationRepository
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
    val speechToText: SpeechToText,
    private val iotRepository: IoTRepository,
    private val pricesRepository: PricesRepository,
    private val weatherRepository: WeatherRepository,
    private val plannerRepository: PlannerRepository,
    private val offlineSyncManager: OfflineSyncManager,
    private val farmLocationRepository: FarmLocationRepository,
) : ViewModel() {

    val language: StateFlow<String> = dataStore.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    val pendingSyncCount: StateFlow<Int> = offlineSyncManager.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val savedFarmLocation: StateFlow<SavedFarmLocation?> = farmLocationRepository.observeSavedFarmLocation()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentPhoneLocation: StateFlow<DetectedLocation?> = farmLocationRepository.currentPhoneLocation

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

    private val _locationSaveNotice = MutableStateFlow<String?>(null)
    val locationSaveNotice: StateFlow<String?> = _locationSaveNotice

    init {
        viewModelScope.launch {
            farmLocationRepository.refreshFromBackend()
            loadData()
        }
    }

    suspend fun detectPhoneLocation(): LocationDetectionResult = farmLocationRepository.detectCurrentPhoneLocation()

    fun confirmFarmLocation(location: SavedFarmLocation) {
        viewModelScope.launch {
            when (val res = farmLocationRepository.confirmAndSaveFarmProfile(location)) {
                is Resource.Success -> {
                    _locationSaveNotice.value = if (res.data.pendingSync) {
                        "Farm location saved offline — will sync when online."
                    } else {
                        "Farm location saved: ${res.data.formattedSummary}"
                    }
                    refreshWeatherForFarm(res.data)
                }
                is Resource.Error -> _locationSaveNotice.value = res.message
                is Resource.Loading -> Unit
            }
        }
    }

    fun clearLocationNotice() {
        _locationSaveNotice.value = null
    }

    fun refreshWeatherForFarm(saved: SavedFarmLocation? = savedFarmLocation.value) {
        viewModelScope.launch {
            val lang = dataStore.language.first()
            weatherRepository.getForecast(
                lat = saved?.latitude,
                lon = saved?.longitude,
                location = saved?.formattedSummary?.takeIf { it.isNotBlank() },
                language = lang,
                forceRefresh = true,
                includeAi = false,
            ).collect { res ->
                if (res is Resource.Success) {
                    _weather.value = res.data
                }
            }
        }
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
            val saved = farmLocationRepository.getSavedFarmLocationOnce()
            val farmState = saved?.state?.trim().orEmpty()
            val primaryCropsLower = saved?.primaryCrops.orEmpty().map { it.trim().lowercase() }.toSet()
            pricesRepository.getMarketPrices().collect { res ->
                if (res is Resource.Success && res.data.isNotEmpty()) {
                    val sorted = res.data.sortedWith(
                        compareByDescending<MarketPrice> {
                            farmState.isNotBlank() && it.state.equals(farmState, ignoreCase = true)
                        }.thenByDescending {
                            primaryCropsLower.any { crop -> it.crop.lowercase().contains(crop) }
                        }
                    )
                    _mandiPrices.value = sorted
                        .distinctBy { it.crop.trim().lowercase() }
                        .take(4)
                }
            }
        }
        viewModelScope.launch {
            val lang = dataStore.language.first()
            val saved = farmLocationRepository.getSavedFarmLocationOnce()
            weatherRepository.getForecast(
                lat = saved?.latitude,
                lon = saved?.longitude,
                location = saved?.formattedSummary?.takeIf { it.isNotBlank() },
                language = lang,
                includeAi = false,
            ).collect { res ->
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
