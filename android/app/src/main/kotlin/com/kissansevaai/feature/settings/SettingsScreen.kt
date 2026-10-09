package com.kissansevaai.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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

private val LANGUAGES = listOf("en" to "English", "hi" to "हिन्दी", "mr" to "मराठी", "pa" to "ਪੰਜਾਬੀ", "sa" to "संस्कृतम्")
private val ENGINE_MODES = listOf("auto" to R.string.settings_engine_auto, "remote" to R.string.settings_engine_remote, "ondevice" to R.string.settings_engine_ondevice)

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val lang by viewModel.language.collectAsState()
    val engine by viewModel.engineMode.collectAsState()
    val url by viewModel.baseUrl.collectAsState()
    var urlField by remember(url) { mutableStateOf(url ?: "") }

    FeatureScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { _ ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Language
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Slate800), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium, color = Emerald400)
                    Spacer(Modifier.height(8.dp))
                    LANGUAGES.forEach { (code, label) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = lang == code, onClick = { viewModel.setLanguage(code) }, colors = RadioButtonDefaults.colors(selectedColor = Emerald500))
                            Text(label, color = Slate200, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }

            // Engine mode
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Slate800), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_engine_mode), style = MaterialTheme.typography.titleMedium, color = Emerald400)
                    Spacer(Modifier.height(8.dp))
                    ENGINE_MODES.forEach { (mode, labelRes) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = engine == mode, onClick = { viewModel.setEngineMode(mode) }, colors = RadioButtonDefaults.colors(selectedColor = Emerald500))
                            Text(stringResource(labelRes), color = Slate200, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }

            // Base URL
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Slate800), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_base_url), style = MaterialTheme.typography.titleMedium, color = Emerald400)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        urlField, { urlField = it },
                        placeholder = { Text("http://10.0.2.2:5000") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.setBaseUrl(urlField.trim()) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                    ) { Text(stringResource(R.string.common_save)) }
                }
            }
        }
    }
}
