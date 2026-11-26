package com.kissansevaai.feature.market

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
import com.kissansevaai.core.location.SavedFarmLocation
import com.kissansevaai.core.model.MarketRecommendationRequest
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.repository.FarmLocationRepository
import com.kissansevaai.core.repository.PricesRepository
import com.kissansevaai.ui.component.AiResponseCard
import com.kissansevaai.ui.component.FeatureScaffold
import com.kissansevaai.ui.component.LoadingOverlay
import com.kissansevaai.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val repository: PricesRepository,
    private val farmLocationRepository: FarmLocationRepository,
    private val ds: DataStoreManager,
) : ViewModel() {
    val savedFarmLocation: StateFlow<SavedFarmLocation?> = farmLocationRepository.observeSavedFarmLocation()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _uiState = MutableStateFlow(MarketUiState())
    val uiState: StateFlow<MarketUiState> = _uiState

    fun recommend(location: String, soilType: String, farmSize: String, currentCrop: String, irrigation: String) {
        viewModelScope.launch {
            val lang = ds.language.first()
            val req = MarketRecommendationRequest(location, soilType, farmSize, currentCrop, irrigation, lang)
            repository.getMarketRecommendations(req).collect { res ->
                when (res) {
                    is Resource.Loading -> _uiState.value = MarketUiState(isLoading = true)
                    is Resource.Success -> _uiState.value = MarketUiState(response = res.data)
                    is Resource.Error -> _uiState.value = MarketUiState(error = res.message)
                }
            }
        }
    }
}
data class MarketUiState(val isLoading: Boolean = false, val response: String? = null, val error: String? = null)

@Composable
fun MarketInsightsScreen(onBack: () -> Unit, viewModel: MarketViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val savedFarm by viewModel.savedFarmLocation.collectAsState()
    var location by remember(savedFarm) { mutableStateOf(savedFarm?.formattedSummary.orEmpty()) }
    var soilType by remember(savedFarm) { mutableStateOf(savedFarm?.soilType.orEmpty()) }
    var farmSize by remember(savedFarm) { mutableStateOf(savedFarm?.farmSizeAcres?.toString().orEmpty()) }
    var currentCrop by remember(savedFarm) { mutableStateOf(savedFarm?.primaryCrops?.firstOrNull().orEmpty()) }
    var irrigation by remember(savedFarm) { mutableStateOf(savedFarm?.irrigationType.orEmpty()) }

    FeatureScaffold(
        title = stringResource(R.string.market_title),
        subtitle = "स्मार्ट बाजार रणनीति एवं बिक्री परामर्श",
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
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, BorderLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Farm Profile & Market Parameters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextCharcoal
                    )

                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text(stringResource(R.string.common_location)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepEmerald,
                            unfocusedBorderColor = BorderMedium
                        )
                    )

                    OutlinedTextField(
                        value = soilType,
                        onValueChange = { soilType = it },
                        label = { Text(stringResource(R.string.common_soil_type)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepEmerald,
                            unfocusedBorderColor = BorderMedium
                        )
                    )

                    OutlinedTextField(
                        value = farmSize,
                        onValueChange = { farmSize = it },
                        label = { Text("Farm Size (in acres)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepEmerald,
                            unfocusedBorderColor = BorderMedium
                        )
                    )

                    OutlinedTextField(
                        value = currentCrop,
                        onValueChange = { currentCrop = it },
                        label = { Text(stringResource(R.string.market_current_crop)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepEmerald,
                            unfocusedBorderColor = BorderMedium
                        )
                    )

                    OutlinedTextField(
                        value = irrigation,
                        onValueChange = { irrigation = it },
                        label = { Text("Irrigation Type") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepEmerald,
                            unfocusedBorderColor = BorderMedium
                        )
                    )
                }
            }

            Button(
                onClick = { viewModel.recommend(location, soilType, farmSize, currentCrop, irrigation) },
                enabled = !state.isLoading && location.isNotBlank(),
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
                        Icon(Icons.Default.AutoAwesome, null)
                        Text(
                            text = "📊 " + stringResource(R.string.market_get_recommendations),
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
                AiResponseCard(stringResource(R.string.market_strategy_title), it)
            }
        }
    }
}
