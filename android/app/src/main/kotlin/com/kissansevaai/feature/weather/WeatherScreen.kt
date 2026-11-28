package com.kissansevaai.feature.weather

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kissansevaai.R
import com.kissansevaai.core.ai.SpeechToText
import com.kissansevaai.core.ai.TextToSpeechEngine
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.location.LocationDetectionResult
import com.kissansevaai.core.location.SavedFarmLocation
import com.kissansevaai.core.model.WeatherAlertRequest
import com.kissansevaai.core.model.WeatherForecast
import com.kissansevaai.core.repository.FarmLocationRepository
import com.kissansevaai.core.repository.WeatherRepository
import com.kissansevaai.ui.component.FeatureScaffold
import com.kissansevaai.ui.component.LoadingOverlay
import com.kissansevaai.ui.component.TTSButton
import com.kissansevaai.ui.component.VoiceInputField
import com.kissansevaai.ui.component.rememberFarmLocationPrompt
import com.kissansevaai.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WeatherUiState(
    val isLoading: Boolean = false,
    val forecast: WeatherForecast? = null,
    val error: String? = null,
)

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val repository: WeatherRepository,
    private val farmLocationRepository: FarmLocationRepository,
    private val dataStore: DataStoreManager,
    val speechToText: SpeechToText,
    val ttsEngine: TextToSpeechEngine,
) : ViewModel() {

    val language: StateFlow<String> = dataStore.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    val savedFarmLocation: StateFlow<SavedFarmLocation?> = farmLocationRepository.observeSavedFarmLocation()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _uiState = MutableStateFlow(WeatherUiState(isLoading = true))
    val uiState: StateFlow<WeatherUiState> = _uiState

    init {
        loadForecast()
    }

    suspend fun detectPhoneLocation(): LocationDetectionResult = farmLocationRepository.detectCurrentPhoneLocation()

    fun confirmAndSaveFarmLocation(location: SavedFarmLocation) {
        viewModelScope.launch {
            val saved = farmLocationRepository.confirmAndSaveFarmProfile(location)
            if (saved is Resource.Success) {
                loadForecastForSavedFarm(saved.data, forceRefresh = true)
            }
        }
    }

    fun loadForecastForSavedFarm(saved: SavedFarmLocation?, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val lang = dataStore.language.first()
            repository.getForecast(
                lat = saved?.latitude,
                lon = saved?.longitude,
                location = saved?.formattedSummary?.takeIf { it.isNotBlank() },
                language = lang,
                forceRefresh = forceRefresh,
                includeAi = false,
            ).collect { res ->
                when (res) {
                    is Resource.Loading -> _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    is Resource.Success -> _uiState.value = WeatherUiState(isLoading = false, forecast = res.data)
                    is Resource.Error -> _uiState.value = _uiState.value.copy(isLoading = false, error = res.message)
                }
            }
        }
    }

    fun loadForecast(location: String? = null, forceRefresh: Boolean = false, includeAi: Boolean = false) {
        viewModelScope.launch {
            val lang = dataStore.language.first()
            val saved = if (location.isNullOrBlank()) (savedFarmLocation.value ?: farmLocationRepository.getSavedFarmLocationOnce()) else null
            repository.getForecast(
                lat = saved?.latitude,
                lon = saved?.longitude,
                location = location?.takeIf { it.isNotBlank() } ?: saved?.formattedSummary?.takeIf { it.isNotBlank() },
                language = lang,
                forceRefresh = forceRefresh,
                includeAi = includeAi,
            ).collect { res ->
                when (res) {
                    is Resource.Loading -> _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    is Resource.Success -> _uiState.value = WeatherUiState(isLoading = false, forecast = res.data)
                    is Resource.Error -> _uiState.value = _uiState.value.copy(isLoading = false, error = res.message)
                }
            }
        }
    }

    fun evaluateFarmAlerts(location: String, crop: String, soilType: String) {
        viewModelScope.launch {
            val lang = dataStore.language.first()
            val saved = savedFarmLocation.value ?: farmLocationRepository.getSavedFarmLocationOnce()
            val useCoords = location.isBlank() || location.equals(saved?.formattedSummary, ignoreCase = true)
            repository.evaluateFarmAlerts(
                WeatherAlertRequest(
                    location = location.ifBlank { saved?.formattedSummary.orEmpty() },
                    lat = if (useCoords) saved?.latitude else null,
                    lon = if (useCoords) saved?.longitude else null,
                    crop = crop,
                    soilType = soilType,
                    language = lang,
                    includeAi = true,
                )
            ).collect { res ->
                when (res) {
                    is Resource.Loading -> _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    is Resource.Success -> _uiState.value = WeatherUiState(isLoading = false, forecast = res.data)
                    is Resource.Error -> _uiState.value = _uiState.value.copy(isLoading = false, error = res.message)
                }
            }
        }
    }
}

@Composable
fun WeatherScreen(onBack: () -> Unit, viewModel: WeatherViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val lang by viewModel.language.collectAsState()
    val savedFarm by viewModel.savedFarmLocation.collectAsState()
    var locationInput by remember(savedFarm) { mutableStateOf(savedFarm?.formattedSummary.orEmpty()) }
    var cropInput by remember { mutableStateOf("Wheat") }
    var soilInput by remember(savedFarm) { mutableStateOf(savedFarm?.soilType?.ifBlank { "Loamy" } ?: "Loamy") }
    var voiceNotice by remember { mutableStateOf<String?>(null) }

    val locationPrompt = rememberFarmLocationPrompt(
        savedFarmLocation = savedFarm,
        lang = lang,
        speechToText = viewModel.speechToText,
        onDetect = { viewModel.detectPhoneLocation() },
        onConfirmSave = { confirmed -> viewModel.confirmAndSaveFarmLocation(confirmed) },
    )

    val quickHubs = listOf("Ludhiana", "Indore", "Patna", "Pune", "Karnal", "New Delhi")

    FeatureScaffold(
        title = stringResource(R.string.weather_title),
        subtitle = "Open-Meteo Live Forecast & Spray Window",
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            (voiceNotice ?: locationPrompt.statusMessage)?.let { note ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AmberTint),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(note, color = SecondaryOrange, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }

            // Search / Location Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoiceInputField(
                            value = locationInput,
                            onValueChange = { locationInput = it },
                            label = stringResource(R.string.weather_location_label),
                            fieldKey = "district",
                            lang = lang,
                            speechToText = viewModel.speechToText,
                            placeholder = "Speak or type district / city",
                            onMessage = { voiceNotice = it },
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { viewModel.loadForecast(locationInput.ifBlank { null }, forceRefresh = true, includeAi = false) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AssistChip(
                            onClick = { locationPrompt.detectNow() },
                            leadingIcon = { Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(14.dp), tint = DeepEmerald) },
                            label = { Text(if (locationPrompt.isDetecting) "Detecting..." else "Detect Farm GPS", style = MaterialTheme.typography.labelSmall) },
                        )
                        savedFarm?.takeIf { it.hasConfirmedLocation }?.let { farm ->
                            FilterChip(
                                selected = locationInput.equals(farm.formattedSummary, ignoreCase = true),
                                onClick = {
                                    locationInput = farm.formattedSummary
                                    viewModel.loadForecastForSavedFarm(farm, forceRefresh = true)
                                },
                                label = { Text("My Farm (${farm.district.ifBlank { farm.state }})", style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                        quickHubs.forEach { hub ->
                            FilterChip(
                                selected = locationInput.equals(hub, ignoreCase = true),
                                onClick = {
                                    locationInput = hub
                                    viewModel.loadForecast(hub, forceRefresh = false, includeAi = false)
                                },
                                label = { Text(hub, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cropInput,
                            onValueChange = { cropInput = it },
                            label = { Text("Crop") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        OutlinedTextField(
                            value = soilInput,
                            onValueChange = { soilInput = it },
                            label = { Text("Soil") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }

                    Button(
                        onClick = { viewModel.evaluateFarmAlerts(locationInput, cropInput, soilInput) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(9999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                    ) {
                        Icon(Icons.Default.NotificationsActive, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.weather_check_alerts_btn), fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (state.isLoading && state.forecast == null) {
                LoadingOverlay(true)
            }

            state.error?.let { err ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(err, color = ErrorRed, modifier = Modifier.padding(14.dp), fontWeight = FontWeight.SemiBold)
                }
            }

            state.forecast?.let { w ->
                // Current Weather Hero Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DeepEmerald),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    w.locationName.ifBlank { "Farm Location" },
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "${w.current.conditionLabel} • ${w.provider.uppercase()} (${w.source})",
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(9999.dp),
                                color = if (w.isStale) AmberTint else Color.White.copy(alpha = 0.18f),
                            ) {
                                Text(
                                    text = if (w.isStale) "CACHED / STALE" else "LIVE DATA",
                                    color = if (w.isStale) SecondaryOrange else Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${w.current.temperatureC.toInt()}°C",
                                color = Color.White,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Humidity: ${w.current.humidityPct.toInt()}%", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                Text("Wind: ${w.current.windSpeedKmh} km/h", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                Text("Rain now: ${w.current.precipitationMm} mm", color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        w.staleReason?.let { stale ->
                            Surface(shape = RoundedCornerShape(8.dp), color = AmberTint) {
                                Text(stale, color = SecondaryOrange, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
                            }
                        }
                    }
                }

                // Spraying Window Summary Banner + TTS
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (w.sprayWindowSuitable) GreenTint else AmberTint,
                    border = BorderStroke(1.dp, if (w.sprayWindowSuitable) DeepEmerald else SecondaryOrange),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (w.sprayWindowSuitable) {
                                    stringResource(R.string.weather_spray_suitable)
                                } else {
                                    stringResource(R.string.weather_spray_hold)
                                },
                                fontWeight = FontWeight.Bold,
                                color = if (w.sprayWindowSuitable) DeepEmerald else SecondaryOrange,
                            )
                            val topMsg = w.advisories.firstOrNull()?.message.orEmpty()
                            if (topMsg.isNotBlank()) {
                                Text(topMsg, style = MaterialTheme.typography.bodySmall, color = TextCharcoal)
                            }
                        }
                        TTSButton(
                            ttsEngine = viewModel.ttsEngine,
                            textToSpeak = w.advisories.joinToString(". ") { "${it.title}: ${it.message}" }.ifBlank { w.current.conditionLabel },
                            lang = lang,
                            onFallbackExplanation = { voiceNotice = it },
                        )
                    }
                }

                // Rule-based Agri Advisories
                if (w.advisories.isNotEmpty()) {
                    Text(
                        stringResource(R.string.weather_advisories_heading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextCharcoal,
                    )
                    w.advisories.forEach { adv ->
                        val cardColor = when (adv.severity) {
                            "warning" -> ErrorContainer.copy(alpha = 0.45f)
                            "caution" -> AmberTint
                            else -> CardWhite
                        }
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = cardColor),
                            border = BorderStroke(1.dp, BorderLight),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(adv.title, fontWeight = FontWeight.Bold, color = TextCharcoal)
                                Text(adv.message, style = MaterialTheme.typography.bodySmall, color = TextCharcoal)
                            }
                        }
                    }
                }

                // AI Advisory Summary (honest if AI disabled/quota exhausted)
                if (!w.aiSummary.isNullOrBlank()) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = GreenBg),
                        border = BorderStroke(1.dp, GreenTint),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Gemini Farm Action Note", fontWeight = FontWeight.Bold, color = DeepEmerald)
                            Text(w.aiSummary, style = MaterialTheme.typography.bodySmall, color = TextCharcoal)
                        }
                    }
                } else if (!w.aiServiceStatus.isNullOrBlank() && w.aiServiceStatus != "not_requested" && w.aiServiceStatus != "pending") {
                    Text(
                        "Note: Live Gemini text summary unavailable (${w.aiServiceStatus}); deterministic agronomic weather rules remain active.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                }

                // 7-Day Daily Forecast
                if (w.daily.isNotEmpty()) {
                    Text(
                        stringResource(R.string.weather_7day_heading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextCharcoal,
                    )
                    w.daily.forEach { day ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CardWhite),
                            border = BorderStroke(1.dp, BorderLight),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(day.date, fontWeight = FontWeight.Bold, color = TextCharcoal)
                                    Text(day.conditionLabel, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "${day.tempMinC.toInt()}° / ${day.tempMaxC.toInt()}°C",
                                        fontWeight = FontWeight.Bold,
                                        color = DeepEmerald,
                                    )
                                    Text(
                                        "Rain ${day.precipitationProbPct.toInt()}% (${day.precipitationMm}mm) • Wind ${day.windMaxKmh.toInt()} km/h",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
