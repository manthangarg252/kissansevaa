package com.kissansevaai.feature.carbon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
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
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.model.CarbonEstimateRequest
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.repository.CarbonRepository
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
class CarbonViewModel @Inject constructor(
    private val repository: CarbonRepository,
    private val ds: DataStoreManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CarbonUiState())
    val uiState: StateFlow<CarbonUiState> = _uiState

    fun estimate(farmSize: String, cropType: String, method: String, irrigation: String) {
        viewModelScope.launch {
            val lang = ds.language.first()
            val req = CarbonEstimateRequest(farmSize, cropType, method, irrigation, lang)
            repository.estimateCarbon(req).collect { res ->
                when (res) {
                    is Resource.Loading -> _uiState.value = CarbonUiState(isLoading = true)
                    is Resource.Success -> _uiState.value = CarbonUiState(response = res.data)
                    is Resource.Error -> _uiState.value = CarbonUiState(error = res.message)
                }
            }
        }
    }
}
data class CarbonUiState(val isLoading: Boolean = false, val response: String? = null, val error: String? = null)

@Composable
fun CarbonCreditsScreen(onBack: () -> Unit, viewModel: CarbonViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var farmSize by remember { mutableStateOf("5") }
    var cropType by remember { mutableStateOf("Cotton & Wheat") }
    var method by remember { mutableStateOf("Organic (जैविक)") }
    var irrigation by remember { mutableStateOf("Drip (ड्रिप)") }

    val methods = listOf("Organic (जैविक)", "Zero-Till", "Agroforestry", "Conventional")
    val irrigations = listOf("Drip (ड्रिप)", "Sprinkler", "Canal/Flood", "Rainfed")

    FeatureScaffold(
        title = stringResource(R.string.carbon_title),
        subtitle = "कार्बन क्रेडिट अनुमान एवं हरित कमाई",
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
            // Hero Carbon Potential Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DeepEmerald),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(Icons.Default.Eco, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Green Agriculture Rewards",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Text(
                        text = "Your Carbon Potential (कार्बन कमाई)",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = "Earn up to ₹15,000 – ₹40,000 / year by adopting regenerative farming and carbon sequestration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnPrimaryContainer
                    )
                }
            }

            // Practice Calculator Inputs
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
                    Text(
                        text = "Farm Practices & Parameters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextCharcoal
                    )

                    OutlinedTextField(
                        value = farmSize,
                        onValueChange = { farmSize = it },
                        label = { Text(stringResource(R.string.carbon_farm_size)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepEmerald,
                            unfocusedBorderColor = BorderMedium
                        )
                    )

                    OutlinedTextField(
                        value = cropType,
                        onValueChange = { cropType = it },
                        label = { Text(stringResource(R.string.carbon_crop_type)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepEmerald,
                            unfocusedBorderColor = BorderMedium
                        )
                    )

                    // Method Chips
                    Text("Farming Method (खेती का तरीका):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextCharcoal)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        methods.forEach { m ->
                            Surface(
                                shape = RoundedCornerShape(9999.dp),
                                color = if (method == m) GreenTint else SurfaceContainerLow,
                                border = BorderStroke(1.dp, if (method == m) DeepEmerald else BorderMedium),
                                modifier = Modifier.clickable { method = m }
                            ) {
                                Text(
                                    text = m,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (method == m) DeepEmerald else TextCharcoal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // Irrigation Chips
                    Text("Irrigation Type (सिंचाई प्रणाली):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextCharcoal)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        irrigations.forEach { irr ->
                            Surface(
                                shape = RoundedCornerShape(9999.dp),
                                color = if (irrigation == irr) GreenTint else SurfaceContainerLow,
                                border = BorderStroke(1.dp, if (irrigation == irr) DeepEmerald else BorderMedium),
                                modifier = Modifier.clickable { irrigation = irr }
                            ) {
                                Text(
                                    text = irr,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (irrigation == irr) DeepEmerald else TextCharcoal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // Estimate Button
            Button(
                onClick = { viewModel.estimate(farmSize, cropType, method, irrigation) },
                enabled = !state.isLoading && farmSize.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(9999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Eco, null)
                        Text(
                            text = "🌿 " + stringResource(R.string.carbon_estimate_btn),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            LoadingOverlay(state.isLoading)

            state.error?.let {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = it, color = OnErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }

            state.response?.let {
                AiResponseCard("Carbon Credits Breakdown (कार्बन रिपोर्ट)", it)
            }
        }
    }
}
