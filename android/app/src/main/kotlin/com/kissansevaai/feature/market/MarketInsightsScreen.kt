package com.kissansevaai.feature.market

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kissansevaai.R
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.model.MarketRecommendationRequest
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.repository.PricesRepository
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
class MarketViewModel @Inject constructor(
    private val repository: PricesRepository,
    private val ds: DataStoreManager,
) : ViewModel() {
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
    var location by remember { mutableStateOf("") }
    var soilType by remember { mutableStateOf("") }
    var farmSize by remember { mutableStateOf("") }
    var currentCrop by remember { mutableStateOf("") }
    var irrigation by remember { mutableStateOf("") }

    FeatureScaffold(title = stringResource(R.string.market_title), subtitle = stringResource(R.string.market_subtitle), onBack = onBack) { _ ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Slate800), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.market_farm_profile), style = MaterialTheme.typography.titleMedium, color = Emerald400)
                    OutlinedTextField(location, { location = it }, label = { Text(stringResource(R.string.common_location)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(soilType, { soilType = it }, label = { Text(stringResource(R.string.common_soil_type)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(farmSize, { farmSize = it }, label = { Text("Farm Size (acres)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(currentCrop, { currentCrop = it }, label = { Text(stringResource(R.string.market_current_crop)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(irrigation, { irrigation = it }, label = { Text("Irrigation Type") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            Button(
                onClick = { viewModel.recommend(location, soilType, farmSize, currentCrop, irrigation) },
                enabled = !state.isLoading && location.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
            ) { Text(stringResource(R.string.market_get_recommendations)) }
            LoadingOverlay(state.isLoading)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.response?.let { AiResponseCard(stringResource(R.string.market_strategy_title), it) }
        }
    }
}
