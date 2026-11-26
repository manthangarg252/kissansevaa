package com.kissansevaai.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kissansevaai.R
import com.kissansevaai.core.ai.FieldExtractor
import com.kissansevaai.core.ai.SpeechToText
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.location.DetectedLocation
import com.kissansevaai.core.location.LocationDetectionResult
import com.kissansevaai.core.location.SavedFarmLocation
import com.kissansevaai.core.repository.FarmLocationRepository
import com.kissansevaai.ui.component.FeatureScaffold
import com.kissansevaai.ui.component.FillByVoiceButton
import com.kissansevaai.ui.component.VoiceInputField
import com.kissansevaai.ui.component.rememberFarmLocationPrompt
import com.kissansevaai.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val ds: DataStoreManager,
    private val farmLocationRepository: FarmLocationRepository,
    val speechToText: SpeechToText,
    val fieldExtractor: FieldExtractor,
) : ViewModel() {
    val language = ds.language.stateIn(viewModelScope, SharingStarted.Eagerly, "en")
    val engineMode = ds.engineMode.stateIn(viewModelScope, SharingStarted.Eagerly, "auto")
    val baseUrl = ds.baseUrl.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val savedFarmLocation: StateFlow<SavedFarmLocation?> = farmLocationRepository.observeSavedFarmLocation()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentPhoneLocation: StateFlow<DetectedLocation?> = farmLocationRepository.currentPhoneLocation

    private val _profileStatus = MutableStateFlow<String?>(null)
    val profileStatus: StateFlow<String?> = _profileStatus

    init {
        viewModelScope.launch {
            farmLocationRepository.refreshFromBackend()
        }
    }

    fun setLanguage(lang: String) { viewModelScope.launch { ds.setLanguage(lang) } }
    fun setEngineMode(mode: String) { viewModelScope.launch { ds.setEngineMode(mode) } }
    fun setBaseUrl(url: String) { viewModelScope.launch { ds.setBaseUrl(url) } }

    suspend fun detectPhoneLocation(): LocationDetectionResult = farmLocationRepository.detectCurrentPhoneLocation()

    fun saveFarmProfile(updated: SavedFarmLocation) {
        viewModelScope.launch {
            when (val res = farmLocationRepository.confirmAndSaveFarmProfile(updated)) {
                is Resource.Success -> {
                    _profileStatus.value = if (res.data.pendingSync) {
                        "Farm profile saved in Room offline — queued for MongoDB sync."
                    } else {
                        "Farm profile & location saved to Room and MongoDB."
                    }
                }
                is Resource.Error -> _profileStatus.value = "Save failed: ${res.message}"
                is Resource.Loading -> Unit
            }
        }
    }
}

private val LANGUAGES = listOf(
    "en" to "English",
    "hi" to "हिन्दी (Hindi)",
    "bho" to "भोजपुरी (Bhojpuri)",
    "mr" to "मराठी (Marathi)",
    "pa" to "ਪੰਜਾਬੀ (Punjabi)",
    "sa" to "संस्कृतम् (Sanskrit)"
)
private val ENGINE_MODES = listOf("auto" to R.string.settings_engine_auto, "remote" to R.string.settings_engine_remote, "ondevice" to R.string.settings_engine_ondevice)

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val lang by viewModel.language.collectAsState()
    val engine by viewModel.engineMode.collectAsState()
    val url by viewModel.baseUrl.collectAsState()
    val profileStatus by viewModel.profileStatus.collectAsState()
    val savedProfile by viewModel.savedFarmLocation.collectAsState()
    val currentPhoneLoc by viewModel.currentPhoneLocation.collectAsState()

    var urlField by remember(url) { mutableStateOf(url ?: "") }
    var farmerNameInput by remember(savedProfile) { mutableStateOf(savedProfile?.farmerName.orEmpty()) }
    var farmNameInput by remember(savedProfile) { mutableStateOf(savedProfile?.farmName.orEmpty()) }
    var villageInput by remember(savedProfile) { mutableStateOf(savedProfile?.village.orEmpty()) }
    var districtInput by remember(savedProfile) { mutableStateOf(savedProfile?.district.orEmpty()) }
    var stateInput by remember(savedProfile) { mutableStateOf(savedProfile?.state.orEmpty()) }
    var soilInput by remember(savedProfile) { mutableStateOf(savedProfile?.soilType.orEmpty()) }
    var irrigationInput by remember(savedProfile) { mutableStateOf(savedProfile?.irrigationType.orEmpty()) }
    var acresInput by remember(savedProfile) { mutableStateOf(savedProfile?.farmSizeAcres?.toString().orEmpty()) }
    var primaryCropsInput by remember(savedProfile) { mutableStateOf(savedProfile?.primaryCrops?.joinToString(", ").orEmpty()) }
    var voiceNotice by remember { mutableStateOf<String?>(null) }

    val locationPrompt = rememberFarmLocationPrompt(
        savedFarmLocation = savedProfile,
        lang = lang,
        speechToText = viewModel.speechToText,
        onDetect = { viewModel.detectPhoneLocation() },
        onConfirmSave = { confirmed -> viewModel.saveFarmProfile(confirmed) },
    )

    FeatureScaffold(
        title = stringResource(R.string.settings_title),
        subtitle = stringResource(R.string.settings_subtitle),
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Automatic Farm Location Card (Saved Farm vs Current Phone Location)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (lang.startsWith("hi")) "खेत का स्थान (स्वचालित पहचान और मैनुअल)" else "Farm Location (Auto GPS & Manual)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DeepEmerald
                    )

                    val savedSummary = savedProfile?.formattedSummary.orEmpty()
                    Surface(shape = RoundedCornerShape(12.dp), color = GreenBg, border = BorderStroke(1.dp, GreenTint), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Saved Farm Location", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = DeepEmerald)
                            Text(
                                text = savedSummary.ifBlank { "Not configured yet — detect GPS or enter district & state below." },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextCharcoal
                            )
                            savedProfile?.let { s ->
                                if (s.latitude != null && s.longitude != null) {
                                    Text("Coords: %.4f, %.4f (${s.locationSource.ifBlank { "manual" }})".format(s.latitude, s.longitude), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                }
                                if (s.locationUpdatedAt.isNotBlank()) {
                                    Text("Last updated: ${s.locationUpdatedAt}${if (s.pendingSync) " (Pending Sync)" else ""}", style = MaterialTheme.typography.labelSmall, color = if (s.pendingSync) SecondaryOrange else TextMuted)
                                }
                            }
                        }
                    }

                    currentPhoneLoc?.let { phone ->
                        Surface(shape = RoundedCornerShape(10.dp), color = AmberTint, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Current Phone Location: ${phone.displayLocality} (${phone.sourceBadgeLabel}) — kept separate until you Confirm.",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryOrange,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { locationPrompt.detectNow() },
                            enabled = !locationPrompt.isDetecting,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(9999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                        ) {
                            if (locationPrompt.isDetecting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Detecting...")
                            } else {
                                Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.location_detect_btn), fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedButton(
                            onClick = { locationPrompt.openManual() },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(9999.dp),
                        ) {
                            Icon(Icons.Default.EditLocationAlt, null, tint = DeepEmerald, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.location_change_btn), color = DeepEmerald)
                        }
                    }

                    locationPrompt.statusMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = SecondaryOrange)
                    }
                }
            }

            // Farm Profile Card (Voice-enabled fields + persistence)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (lang.startsWith("hi")) "किसान एवं खेत प्रोफ़ाइल" else "Farmer Profile (Weather, Mandi & Schemes Context)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DeepEmerald
                    )
                    FillByVoiceButton(
                        fields = listOf("name", "farmName", "village", "district", "state", "farmSize", "soilType", "irrigation", "primaryCrops"),
                        fieldLabels = mapOf(
                            "name" to "Farmer Name",
                            "farmName" to "Farm Name",
                            "village" to "Village",
                            "district" to "District",
                            "state" to "State",
                            "farmSize" to "Field Area (Acres)",
                            "soilType" to "Soil Type",
                            "irrigation" to "Irrigation",
                            "primaryCrops" to "Primary Crops",
                        ),
                        lang = lang,
                        speechToText = viewModel.speechToText,
                        fieldExtractor = viewModel.fieldExtractor,
                        onFill = { map ->
                            map["name"]?.let { farmerNameInput = it }
                            map["farmName"]?.let { farmNameInput = it }
                            map["village"]?.let { villageInput = it }
                            map["district"]?.let { districtInput = it }
                            map["state"]?.let { stateInput = it }
                            map["soilType"]?.let { soilInput = it }
                            map["irrigation"]?.let { irrigationInput = it }
                            map["farmSize"]?.let { acresInput = it }
                            map["primaryCrops"]?.let { primaryCropsInput = it }
                        },
                        onMessage = { voiceNotice = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    VoiceInputField(
                        value = farmerNameInput,
                        onValueChange = { farmerNameInput = it },
                        label = "Farmer Name",
                        fieldKey = "farmerName",
                        lang = lang,
                        speechToText = viewModel.speechToText,
                        onMessage = { voiceNotice = it },
                    )
                    VoiceInputField(
                        value = farmNameInput,
                        onValueChange = { farmNameInput = it },
                        label = "Farm Name",
                        fieldKey = "farmName",
                        lang = lang,
                        speechToText = viewModel.speechToText,
                        onMessage = { voiceNotice = it },
                    )
                    VoiceInputField(
                        value = villageInput,
                        onValueChange = { villageInput = it },
                        label = "Village / Locality",
                        fieldKey = "village",
                        lang = lang,
                        speechToText = viewModel.speechToText,
                        onMessage = { voiceNotice = it },
                    )
                    VoiceInputField(
                        value = districtInput,
                        onValueChange = { districtInput = it },
                        label = "District",
                        fieldKey = "district",
                        lang = lang,
                        speechToText = viewModel.speechToText,
                        onMessage = { voiceNotice = it },
                    )
                    VoiceInputField(
                        value = stateInput,
                        onValueChange = { stateInput = it },
                        label = "State",
                        fieldKey = "state",
                        lang = lang,
                        speechToText = viewModel.speechToText,
                        onMessage = { voiceNotice = it },
                    )
                    VoiceInputField(
                        value = acresInput,
                        onValueChange = { acresInput = it },
                        label = "Field Area (Acres)",
                        fieldKey = "farmSize",
                        lang = lang,
                        speechToText = viewModel.speechToText,
                        onMessage = { voiceNotice = it },
                    )
                    VoiceInputField(
                        value = soilInput,
                        onValueChange = { soilInput = it },
                        label = "Soil Type",
                        fieldKey = "soilType",
                        lang = lang,
                        speechToText = viewModel.speechToText,
                        onMessage = { voiceNotice = it },
                    )
                    VoiceInputField(
                        value = irrigationInput,
                        onValueChange = { irrigationInput = it },
                        label = "Irrigation Method",
                        fieldKey = "irrigationType",
                        lang = lang,
                        speechToText = viewModel.speechToText,
                        onMessage = { voiceNotice = it },
                    )
                    VoiceInputField(
                        value = primaryCropsInput,
                        onValueChange = { primaryCropsInput = it },
                        label = "Primary Crops (comma separated)",
                        fieldKey = "primaryCrops",
                        lang = lang,
                        speechToText = viewModel.speechToText,
                        onMessage = { voiceNotice = it },
                    )

                    Button(
                        onClick = {
                            val base = savedProfile ?: SavedFarmLocation()
                            val cropsList = primaryCropsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            viewModel.saveFarmProfile(
                                base.copy(
                                    farmerName = farmerNameInput.trim(),
                                    farmName = farmNameInput.trim(),
                                    village = villageInput.trim(),
                                    district = districtInput.trim(),
                                    state = stateInput.trim(),
                                    soilType = soilInput.trim(),
                                    irrigationType = irrigationInput.trim(),
                                    farmSizeAcres = acresInput.toDoubleOrNull(),
                                    primaryCrops = cropsList,
                                    locationSource = base.locationSource.ifBlank { "manual" },
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(9999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                    ) {
                        Text(stringResource(R.string.common_save), fontWeight = FontWeight.Bold)
                    }
                    (voiceNotice ?: profileStatus)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = DeepEmerald)
                    }
                }
            }

            // Language
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_language),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DeepEmerald
                    )
                    Spacer(Modifier.height(8.dp))
                    LANGUAGES.forEach { (code, label) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = lang == code,
                                onClick = { viewModel.setLanguage(code) },
                                colors = RadioButtonDefaults.colors(selectedColor = DeepEmerald)
                            )
                            Text(label, color = TextCharcoal, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }

            // Engine mode
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_engine_mode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DeepEmerald
                    )
                    Spacer(Modifier.height(8.dp))
                    ENGINE_MODES.forEach { (mode, labelRes) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = engine == mode,
                                onClick = { viewModel.setEngineMode(mode) },
                                colors = RadioButtonDefaults.colors(selectedColor = DeepEmerald)
                            )
                            Text(stringResource(labelRes), color = TextCharcoal, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }

            // Base URL
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_base_url),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DeepEmerald
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = urlField,
                        onValueChange = { urlField = it },
                        placeholder = { Text("http://10.0.2.2:5050") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepEmerald,
                            unfocusedBorderColor = BorderMedium
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.setBaseUrl(urlField.trim()) },
                        shape = RoundedCornerShape(9999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                    ) {
                        Text(stringResource(R.string.common_save), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
