package com.kissansevaai.feature.iot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        val s = _sensor.value ?: SensorData(temperature = 27.8, humidity = 64.0, soilMoisture = 45.0, phLevel = 6.7)
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
    var selectedRange by remember { mutableStateOf("24H") }

    FeatureScaffold(
        title = stringResource(R.string.iot_title),
        subtitle = "स्मार्ट फार्म सेंसर टेलीमेट्री व निगरानी",
        onBack = onBack,
        actions = {
            IconButton(onClick = { viewModel.fetchLive() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = DeepEmerald)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Live Status Banner
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, BorderLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(SproutMint)
                        )
                        Text(
                            text = "Plot 1 Sensors: Live",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextCharcoal
                        )
                    }
                    Text(
                        text = "Real-time Telemetry",
                        style = MaterialTheme.typography.labelSmall,
                        color = DeepEmerald,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 4 Telemetry Cards (2x2 Grid)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IoTSensorCard(
                    label = stringResource(R.string.iot_temp),
                    value = "${sensor?.temperature ?: "27.8"}°C",
                    status = "Normal Range",
                    icon = Icons.Default.DeviceThermostat,
                    accentColor = HarvestAmber,
                    iconBg = AmberTint,
                    modifier = Modifier.weight(1f)
                )
                IoTSensorCard(
                    label = stringResource(R.string.iot_humidity),
                    value = "${sensor?.humidity ?: "64.0"}%",
                    status = "Favorable",
                    icon = Icons.Default.Air,
                    accentColor = Blue500,
                    iconBg = SurfaceContainerHigh,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IoTSensorCard(
                    label = stringResource(R.string.iot_moisture),
                    value = "${sensor?.soilMoisture ?: "45.0"}%",
                    status = "Optimal Moisture",
                    icon = Icons.Default.WaterDrop,
                    accentColor = DeepEmerald,
                    iconBg = GreenTintLight,
                    modifier = Modifier.weight(1f)
                )
                IoTSensorCard(
                    label = stringResource(R.string.iot_ph),
                    value = "${sensor?.phLevel ?: "6.7"} pH",
                    status = "Slightly Acidic",
                    icon = Icons.Default.Science,
                    accentColor = TertiaryGreen,
                    iconBg = GreenTintLight,
                    modifier = Modifier.weight(1f)
                )
            }

            // Historical Trend Time Range Selector
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Telemetry Trends (रुझान)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextCharcoal
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("24H", "7D", "30D").forEach { range ->
                                Surface(
                                    shape = RoundedCornerShape(9999.dp),
                                    color = if (selectedRange == range) DeepEmerald else SurfaceContainerLow,
                                    modifier = Modifier.clickable { selectedRange = range }
                                ) {
                                    Text(
                                        text = range,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedRange == range) Color.White else TextCharcoal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Simulated Clean Sensor Wave Graph
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceBg,
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Avg Moisture: 45.2% • Stable Irrigation Cycle",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                listOf(35, 42, 48, 45, 52, 44, 46, 49, 45).forEach { h ->
                                    Box(
                                        modifier = Modifier
                                            .width(22.dp)
                                            .height((h * 1.2).dp)
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(DeepEmerald.copy(alpha = if (h == 45) 1f else 0.45f))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // AI Insights CTA Button
            Button(
                onClick = { viewModel.getInsights() },
                enabled = sensor != null && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(9999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Text("🤖 Get AI Telemetry Insights", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            LoadingOverlay(isLoading)

            error?.let {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = it, color = OnErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }

            insights?.let {
                AiResponseCard("AI Sensor Advisory (सेंसर विश्लेषण)", it)
            }
        }
    }
}

@Composable
private fun IoTSensorCard(
    label: String,
    value: String,
    status: String,
    icon: ImageVector,
    accentColor: Color,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
                Surface(
                    shape = CircleShape,
                    color = iconBg,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = TextCharcoal
            )

            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
        }
    }
}
