package com.kissansevaai.feature.iot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kissansevaai.R
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.model.IoTInsightsRequest
import com.kissansevaai.core.model.SensorData
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.repository.IoTRepository
import com.kissansevaai.ui.component.AiResponseCard
import com.kissansevaai.ui.component.FeatureScaffold
import com.kissansevaai.ui.component.LoadingOverlay
import com.kissansevaai.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IoTViewModel @Inject constructor(
    private val repository: IoTRepository,
    private val ds: DataStoreManager,
) : ViewModel() {
    private val _sensor = MutableStateFlow<SensorData?>(null)
    val sensor: StateFlow<SensorData?> = _sensor
    private val _insights = MutableStateFlow<String?>(null)
    val insights: StateFlow<String?> = _insights
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init { fetchLive() }

    fun fetchLive() {
        viewModelScope.launch {
            repository.getLiveSensorData().collect { res ->
                when (res) {
                    is Resource.Loading -> {}
                    is Resource.Success -> {
                        _sensor.value = res.data
                        _error.value = null
                    }
                    is Resource.Error -> {
                        _error.value = res.message
                    }
                }
            }
        }
    }

    fun getInsights() {
        val s = _sensor.value ?: return
        viewModelScope.launch {
            val lang = ds.language.first()
            repository.getInsights(IoTInsightsRequest(s, lang)).collect { res ->
                when (res) {
                    is Resource.Loading -> {
                        _isLoading.value = true
                        _error.value = null
                    }
                    is Resource.Success -> {
                        _isLoading.value = false
                        _insights.value = res.data
                    }
                    is Resource.Error -> {
                        _isLoading.value = false
                        _error.value = res.message
                    }
                }
            }
        }
    }
}

@Composable
fun IoTMonitoringScreen(onBack: () -> Unit, viewModel: IoTViewModel = hiltViewModel()) {
    val sensor by viewModel.sensor.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    FeatureScaffold(title = stringResource(R.string.iot_title), subtitle = stringResource(R.string.iot_subtitle), onBack = onBack) { _ ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SensorCard(stringResource(R.string.iot_temp), "${sensor?.temperature ?: "--"}°C", Icons.Default.Thermostat, Red400, Modifier.weight(1f))
                SensorCard(stringResource(R.string.iot_humidity), "${sensor?.humidity ?: "--"}%", Icons.Default.WaterDrop, Blue500, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SensorCard(stringResource(R.string.iot_moisture), "${sensor?.soilMoisture ?: "--"}%", Icons.Default.Grass, Emerald500, Modifier.weight(1f))
                SensorCard(stringResource(R.string.iot_ph), "${sensor?.phLevel ?: "--"}", Icons.Default.Science, Purple500, Modifier.weight(1f))
            }
            Button(onClick = { viewModel.getInsights() }, enabled = sensor != null && !isLoading, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Emerald600)) { Text("Get AI Insights") }
            LoadingOverlay(isLoading)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            insights?.let { AiResponseCard("AI Insights", it) }
        }
    }
}

@Composable
private fun SensorCard(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Slate800), modifier = modifier) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, color = Slate100)
            Text(label, style = MaterialTheme.typography.bodySmall, color = Slate400)
        }
    }
}
