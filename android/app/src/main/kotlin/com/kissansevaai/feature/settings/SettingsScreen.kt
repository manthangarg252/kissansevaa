package com.kissansevaai.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kissansevaai.R
import com.kissansevaai.core.ai.FieldExtractor
import com.kissansevaai.core.ai.SpeechToText
import com.kissansevaai.core.common.toUserFriendlyMessage
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.model.FarmProfileUpdateRequest
import com.kissansevaai.core.network.KissanApi
import com.kissansevaai.ui.component.FeatureScaffold
import com.kissansevaai.ui.component.VoiceFormFillButton
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
    private val api: KissanApi,
    val speechToText: SpeechToText,
    val fieldExtractor: FieldExtractor,
) : ViewModel() {
    val language = ds.language.stateIn(viewModelScope, SharingStarted.Eagerly, "en")
    val engineMode = ds.engineMode.stateIn(viewModelScope, SharingStarted.Eagerly, "auto")
    val baseUrl = ds.baseUrl.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _profileStatus = MutableStateFlow<String?>(null)
    val profileStatus: StateFlow<String?> = _profileStatus

    private val _stateField = MutableStateFlow("")
    val stateField: StateFlow<String> = _stateField
    private val _districtField = MutableStateFlow("")
    val districtField: StateFlow<String> = _districtField
    private val _soilField = MutableStateFlow("")
    val soilField: StateFlow<String> = _soilField
    private val _irrigationField = MutableStateFlow("")
    val irrigationField: StateFlow<String> = _irrigationField
    private val _farmSizeField = MutableStateFlow("")
    val farmSizeField: StateFlow<String> = _farmSizeField

    init {
        viewModelScope.launch {
            runCatching { api.getMe() }.onSuccess { u ->
                _stateField.value = u.state
                _districtField.value = u.district
                _soilField.value = u.soilType
                _irrigationField.value = u.irrigationType
                _farmSizeField.value = u.farmSizeAcres?.toString() ?: ""
            }
        }
    }

    fun setLanguage(lang: String) { viewModelScope.launch { ds.setLanguage(lang) } }
    fun setEngineMode(mode: String) { viewModelScope.launch { ds.setEngineMode(mode) } }
    fun setBaseUrl(url: String) { viewModelScope.launch { ds.setBaseUrl(url) } }

    fun saveFarmProfile(state: String, district: String, soilType: String, irrigation: String, acresText: String) {
        viewModelScope.launch {
            try {
                val updated = api.updateProfile(
                    FarmProfileUpdateRequest(
                        state = state.trim(),
                        district = district.trim(),
                        soilType = soilType.trim(),
                        irrigationType = irrigation.trim(),
                        farmSizeAcres = acresText.toDoubleOrNull(),
                    )
                )
                _stateField.value = updated.state
                _districtField.value = updated.district
                _soilField.value = updated.soilType
                _irrigationField.value = updated.irrigationType
                _farmSizeField.value = updated.farmSizeAcres?.toString() ?: ""
                _profileStatus.value = "Farm profile saved to backend."
            } catch (e: Exception) {
                _profileStatus.value = "Save deferred/failed: ${e.toUserFriendlyMessage()}"
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
    val loadedState by viewModel.stateField.collectAsState()
    val loadedDistrict by viewModel.districtField.collectAsState()
    val loadedSoil by viewModel.soilField.collectAsState()
    val loadedIrrig by viewModel.irrigationField.collectAsState()
    val loadedAcres by viewModel.farmSizeField.collectAsState()

    var urlField by remember(url) { mutableStateOf(url ?: "") }
    var stateInput by remember(loadedState) { mutableStateOf(loadedState) }
    var districtInput by remember(loadedDistrict) { mutableStateOf(loadedDistrict) }
    var soilInput by remember(loadedSoil) { mutableStateOf(loadedSoil) }
    var irrigationInput by remember(loadedIrrig) { mutableStateOf(loadedIrrig) }
    var acresInput by remember(loadedAcres) { mutableStateOf(loadedAcres) }
    var voiceNotice by remember { mutableStateOf<String?>(null) }

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
            // Farm Profile Card (Used to personalize Weather, Planner, Schemes, Chatbot)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Farmer Profile (Weather & Planner Context)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DeepEmerald
                    )
                    VoiceFormFillButton(
                        fields = listOf("state", "district", "soilType", "irrigation", "farmSize"),
                        lang = lang,
                        speechToText = viewModel.speechToText,
                        fieldExtractor = viewModel.fieldExtractor,
                        onFill = { map ->
                            map["state"]?.let { stateInput = it }
                            map["district"]?.let { districtInput = it }
                            map["soilType"]?.let { soilInput = it }
                            map["irrigation"]?.let { irrigationInput = it }
                            map["farmSize"]?.let { acresInput = it }
                        },
                        onMessage = { voiceNotice = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = stateInput,
                            onValueChange = { stateInput = it },
                            label = { Text("State") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        OutlinedTextField(
                            value = districtInput,
                            onValueChange = { districtInput = it },
                            label = { Text("District") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = soilInput,
                            onValueChange = { soilInput = it },
                            label = { Text("Soil Type") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        OutlinedTextField(
                            value = acresInput,
                            onValueChange = { acresInput = it },
                            label = { Text("Acres") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                    OutlinedTextField(
                        value = irrigationInput,
                        onValueChange = { irrigationInput = it },
                        label = { Text("Irrigation") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Button(
                        onClick = { viewModel.saveFarmProfile(stateInput, districtInput, soilInput, irrigationInput, acresInput) },
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
