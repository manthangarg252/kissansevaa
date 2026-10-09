package com.kissansevaai.feature.carbon

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
    var farmSize by remember { mutableStateOf("") }
    var cropType by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("") }
    var irrigation by remember { mutableStateOf("") }

    FeatureScaffold(title = stringResource(R.string.carbon_title), subtitle = stringResource(R.string.carbon_subtitle), onBack = onBack) { _ ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Slate800), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(farmSize, { farmSize = it }, label = { Text(stringResource(R.string.carbon_farm_size)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(cropType, { cropType = it }, label = { Text(stringResource(R.string.carbon_crop_type)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(method, { method = it }, label = { Text(stringResource(R.string.carbon_method)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(irrigation, { irrigation = it }, label = { Text(stringResource(R.string.carbon_irrigation)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            Button(onClick = { viewModel.estimate(farmSize, cropType, method, irrigation) }, enabled = !state.isLoading && farmSize.isNotBlank(), modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Emerald600)) { Text(stringResource(R.string.carbon_estimate_btn)) }
            LoadingOverlay(state.isLoading)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.response?.let { AiResponseCard(stringResource(R.string.carbon_title), it) }
        }
    }
}
