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
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.ui.component.FeatureScaffold
import com.kissansevaai.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val ds: DataStoreManager) : ViewModel() {
    val language = ds.language.stateIn(viewModelScope, SharingStarted.Eagerly, "en")
    val engineMode = ds.engineMode.stateIn(viewModelScope, SharingStarted.Eagerly, "auto")
    val baseUrl = ds.baseUrl.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setLanguage(lang: String) { viewModelScope.launch { ds.setLanguage(lang) } }
    fun setEngineMode(mode: String) { viewModelScope.launch { ds.setEngineMode(mode) } }
    fun setBaseUrl(url: String) { viewModelScope.launch { ds.setBaseUrl(url) } }
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
    var urlField by remember(url) { mutableStateOf(url ?: "") }

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
                        placeholder = { Text("http://10.201.214.248:5050") },
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
