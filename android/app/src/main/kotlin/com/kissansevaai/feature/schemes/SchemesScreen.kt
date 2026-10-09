package com.kissansevaai.feature.schemes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kissansevaai.R
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.model.Scheme
import com.kissansevaai.core.model.SchemeRecommendationRequest
import com.kissansevaai.core.repository.SchemesRepository
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
class SchemesViewModel @Inject constructor(
    private val repository: SchemesRepository,
    private val ds: DataStoreManager,
) : ViewModel() {

    private val _schemes = MutableStateFlow<List<Scheme>>(emptyList())
    val schemes: StateFlow<List<Scheme>> = _schemes

    private val _isSchemesLoading = MutableStateFlow(true)
    val isSchemesLoading: StateFlow<Boolean> = _isSchemesLoading

    private val _uiState = MutableStateFlow(SchemesUiState())
    val uiState: StateFlow<SchemesUiState> = _uiState

    init {
        loadSchemes()
    }

    fun loadSchemes() {
        viewModelScope.launch {
            repository.getSchemes().collect { res ->
                when (res) {
                    is Resource.Loading -> _isSchemesLoading.value = true
                    is Resource.Success -> {
                        _isSchemesLoading.value = false
                        _schemes.value = res.data
                    }
                    is Resource.Error -> _isSchemesLoading.value = false
                }
            }
        }
    }

    fun recommend(state: String, farmSize: String, crop: String, soilType: String, landType: String) {
        viewModelScope.launch {
            val lang = ds.language.first()
            val req = SchemeRecommendationRequest(state, farmSize, crop, soilType, landType, lang)
            repository.getRecommendations(req).collect { res ->
                when (res) {
                    is Resource.Loading -> _uiState.value = SchemesUiState(isLoading = true)
                    is Resource.Success -> _uiState.value = SchemesUiState(response = res.data)
                    is Resource.Error -> _uiState.value = SchemesUiState(error = res.message)
                }
            }
        }
    }
}

data class SchemesUiState(val isLoading: Boolean = false, val response: String? = null, val error: String? = null)

@Composable
fun SchemesScreen(onBack: () -> Unit, viewModel: SchemesViewModel = hiltViewModel()) {
    val schemes by viewModel.schemes.collectAsState()
    val isSchemesLoading by viewModel.isSchemesLoading.collectAsState()
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    var stateField by remember { mutableStateOf("") }
    var farmSize by remember { mutableStateOf("") }
    var crop by remember { mutableStateOf("") }
    var soilType by remember { mutableStateOf("") }
    var landType by remember { mutableStateOf("") }

    val uriHandler = LocalUriHandler.current

    FeatureScaffold(
        title = stringResource(R.string.schemes_title),
        subtitle = stringResource(R.string.schemes_subtitle),
        onBack = onBack
    ) { _ ->
        Column(Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Slate900,
                contentColor = Emerald400,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Government Schemes", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.ListAlt, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("AI Advisor", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
                )
            }

            if (selectedTab == 0) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.common_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    LoadingOverlay(isSchemesLoading && schemes.isEmpty())

                    val filteredSchemes = remember(schemes, searchQuery) {
                        if (searchQuery.isBlank()) schemes
                        else schemes.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                            it.category.contains(searchQuery, ignoreCase = true) ||
                            it.description.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredSchemes, key = { it.id.ifBlank { it.name } }) { scheme ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Slate800),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = scheme.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Slate100,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Emerald600.copy(alpha = 0.2f),
                                            border = null
                                        ) {
                                            Text(
                                                text = scheme.category,
                                                color = Emerald400,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = scheme.description,
                                        color = Slate300,
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    if (scheme.eligibility.isNotEmpty()) {
                                        Text(
                                            text = "Eligibility:",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Slate400,
                                            fontWeight = FontWeight.Bold
                                        )
                                        scheme.eligibility.forEach { item ->
                                            Text(
                                                text = "• $item",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Slate400
                                            )
                                        }
                                    }

                                    scheme.link?.takeIf { it.isNotBlank() }?.let { linkUrl ->
                                        OutlinedButton(
                                            onClick = {
                                                try { uriHandler.openUri(linkUrl) } catch (_: Exception) {}
                                            },
                                            modifier = Modifier.align(Alignment.End),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Emerald400)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Visit Portal", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate800),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(stateField, { stateField = it }, label = { Text("State") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(farmSize, { farmSize = it }, label = { Text("Farm Size (acres)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(crop, { crop = it }, label = { Text("Crop") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(soilType, { soilType = it }, label = { Text(stringResource(R.string.common_soil_type)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(landType, { landType = it }, label = { Text("Land Type") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Button(
                        onClick = { viewModel.recommend(stateField, farmSize, crop, soilType, landType) },
                        enabled = !state.isLoading && stateField.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                    ) {
                        Text(stringResource(R.string.schemes_ai_btn))
                    }
                    LoadingOverlay(state.isLoading)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    state.response?.let { AiResponseCard(stringResource(R.string.schemes_modal_title), it) }
                }
            }
        }
    }
}
