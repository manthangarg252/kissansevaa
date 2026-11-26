package com.kissansevaai.feature.schemes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kissansevaai.R
import com.kissansevaai.core.ai.FieldExtractor
import com.kissansevaai.core.ai.SpeechToText
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.location.SavedFarmLocation
import com.kissansevaai.core.model.Scheme
import com.kissansevaai.core.model.SchemeEligibilityResponse
import com.kissansevaai.core.model.SchemeExplainResponse
import com.kissansevaai.core.model.SchemeRecommendationRequest
import com.kissansevaai.core.repository.FarmLocationRepository
import com.kissansevaai.core.repository.SchemesRepository
import com.kissansevaai.ui.component.AiResponseCard
import com.kissansevaai.ui.component.FeatureScaffold
import com.kissansevaai.ui.component.FillByVoiceButton
import com.kissansevaai.ui.component.LoadingOverlay
import com.kissansevaai.ui.component.VoiceInputField
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
class SchemesViewModel @Inject constructor(
    private val repository: SchemesRepository,
    private val farmLocationRepository: FarmLocationRepository,
    private val ds: DataStoreManager,
    val speechToText: SpeechToText,
    val fieldExtractor: FieldExtractor,
) : ViewModel() {

    val language: StateFlow<String> = ds.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    val savedFarmLocation: StateFlow<SavedFarmLocation?> = farmLocationRepository.observeSavedFarmLocation()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _schemes = MutableStateFlow<List<Scheme>>(emptyList())
    val schemes: StateFlow<List<Scheme>> = _schemes

    private val _savedIds = MutableStateFlow<Set<String>>(emptySet())
    val savedIds: StateFlow<Set<String>> = _savedIds

    private val _isSchemesLoading = MutableStateFlow(true)
    val isSchemesLoading: StateFlow<Boolean> = _isSchemesLoading

    private val _uiState = MutableStateFlow(SchemesUiState())
    val uiState: StateFlow<SchemesUiState> = _uiState

    private val _explainState = MutableStateFlow<SchemeExplainResponse?>(null)
    val explainState: StateFlow<SchemeExplainResponse?> = _explainState

    init {
        loadSchemes()
        loadSaved()
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

    private fun loadSaved() {
        viewModelScope.launch {
            repository.getSavedSchemeIds().collect { _savedIds.value = it }
        }
    }

    fun toggleBookmark(schemeId: String) {
        val current = _savedIds.value.contains(schemeId)
        _savedIds.value = if (current) _savedIds.value - schemeId else _savedIds.value + schemeId
        viewModelScope.launch { repository.toggleSaveScheme(schemeId, current) }
    }

    fun explainScheme(scheme: Scheme) {
        viewModelScope.launch {
            val lang = ds.language.first()
            repository.explainScheme(scheme.id, lang).collect { res ->
                when (res) {
                    is Resource.Success -> _explainState.value = res.data
                    is Resource.Error -> _explainState.value = SchemeExplainResponse(
                        scheme = scheme,
                        explanation = "Live AI explainer unavailable (${res.message}). Official portal: ${scheme.officialPortal ?: scheme.link}",
                        aiAvailable = false,
                        serviceStatus = "unavailable",
                    )
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    fun dismissExplanation() {
        _explainState.value = null
    }

    fun evaluateEligibility(state: String, farmSize: String, crop: String, soilType: String, landType: String) {
        viewModelScope.launch {
            val lang = ds.language.first()
            val req = SchemeRecommendationRequest(state, farmSize, crop, soilType, landType, false, lang)
            repository.checkEligibility(req).collect { res ->
                when (res) {
                    is Resource.Loading -> _uiState.value = SchemesUiState(isLoading = true)
                    is Resource.Success -> _uiState.value = SchemesUiState(eligibilityResult = res.data, response = res.data.aiSummary)
                    is Resource.Error -> _uiState.value = SchemesUiState(error = res.message)
                }
            }
        }
    }
}

data class SchemesUiState(
    val isLoading: Boolean = false,
    val eligibilityResult: SchemeEligibilityResponse? = null,
    val response: String? = null,
    val error: String? = null,
)

@Composable
fun SchemesScreen(onBack: () -> Unit, viewModel: SchemesViewModel = hiltViewModel()) {
    val schemes by viewModel.schemes.collectAsState()
    val savedIds by viewModel.savedIds.collectAsState()
    val isSchemesLoading by viewModel.isSchemesLoading.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val explainState by viewModel.explainState.collectAsState()
    val savedFarm by viewModel.savedFarmLocation.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var savedOnly by remember { mutableStateOf(false) }
    var selectedStateFilter by remember(savedFarm) { mutableStateOf(savedFarm?.state.orEmpty()) }

    var stateField by remember(savedFarm) { mutableStateOf(savedFarm?.state.orEmpty()) }
    var farmSize by remember(savedFarm) { mutableStateOf(savedFarm?.farmSizeAcres?.toString() ?: "") }
    var crop by remember(savedFarm) { mutableStateOf(savedFarm?.primaryCrops?.joinToString(", ").orEmpty()) }
    var soilType by remember(savedFarm) { mutableStateOf(savedFarm?.soilType.orEmpty()) }
    var landType by remember(savedFarm) { mutableStateOf(savedFarm?.irrigationType.orEmpty()) }
    var formNotice by remember { mutableStateOf<String?>(null) }
    val currentLang by viewModel.language.collectAsState()
    val uriHandler = LocalUriHandler.current

    explainState?.let { exp ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissExplanation() },
            title = { Text(exp.scheme.name, fontWeight = FontWeight.Bold, color = DeepEmerald) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(exp.explanation ?: exp.scheme.description, color = TextCharcoal, style = MaterialTheme.typography.bodySmall)
                    if (exp.scheme.documents.isNotEmpty()) {
                        Text("Required Documents: ${exp.scheme.documents.joinToString(", ")}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                    exp.scheme.phone?.let { Text("Helpline: $it", color = DeepEmerald, style = MaterialTheme.typography.labelSmall) }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissExplanation() }, colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald)) {
                    Text("Close")
                }
            },
            containerColor = CardWhite,
        )
    }

    FeatureScaffold(
        title = stringResource(R.string.schemes_title),
        subtitle = "सरकारी योजनाएं, अनुदान एवं पात्रता",
        onBack = onBack
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Surface(color = CardWhite, border = BorderStroke(1.dp, BorderLight), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = if (selectedTab == 0) DeepEmerald else SurfaceContainerLow,
                        modifier = Modifier.weight(1f).clickable { selectedTab = 0 }
                    ) {
                        Row(modifier = Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalance, null, tint = if (selectedTab == 0) Color.White else TextMuted, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Verified Schemes", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (selectedTab == 0) Color.White else TextCharcoal)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = if (selectedTab == 1) DeepEmerald else SurfaceContainerLow,
                        modifier = Modifier.weight(1f).clickable { selectedTab = 1 }
                    ) {
                        Row(modifier = Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = if (selectedTab == 1) Color.White else TextMuted, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Eligibility Check", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (selectedTab == 1) Color.White else TextCharcoal)
                        }
                    }
                }
            }

            if (selectedTab == 0) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    VoiceInputField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = stringResource(R.string.schemes_search_placeholder),
                        fieldKey = "searchQuery",
                        lang = currentLang,
                        speechToText = viewModel.speechToText,
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = DeepEmerald) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        savedFarm?.state?.takeIf { it.isNotBlank() }?.let { farmState ->
                            item {
                                FilterChip(
                                    selected = selectedStateFilter.equals(farmState, ignoreCase = true),
                                    onClick = {
                                        selectedStateFilter = if (selectedStateFilter.equals(farmState, ignoreCase = true)) "" else farmState
                                    },
                                    leadingIcon = { Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(15.dp)) },
                                    label = { Text("$farmState Schemes") },
                                )
                            }
                        }
                        item {
                            FilterChip(
                                selected = selectedStateFilter.isBlank(),
                                onClick = { selectedStateFilter = "" },
                                label = { Text("All States") },
                            )
                        }
                        item {
                            FilterChip(
                                selected = savedOnly,
                                onClick = { savedOnly = !savedOnly },
                                leadingIcon = { Icon(Icons.Default.Bookmark, null, modifier = Modifier.size(15.dp)) },
                                label = { Text("Saved (${savedIds.size})") },
                            )
                        }
                        items(listOf("All", "Income", "Insurance", "Credit", "Irrigation", "Soil", "Market", "Organic", "Livestock")) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat) },
                            )
                        }
                    }

                    LoadingOverlay(isSchemesLoading && schemes.isEmpty())

                    val filteredSchemes = remember(schemes, searchQuery, selectedCategory, savedOnly, savedIds, selectedStateFilter) {
                        schemes.filter { scheme ->
                            val matchSaved = !savedOnly || savedIds.contains(scheme.id)
                            val matchCat = selectedCategory == "All" || scheme.category.contains(selectedCategory, ignoreCase = true)
                            val matchState = selectedStateFilter.isBlank() ||
                                scheme.states.isEmpty() ||
                                scheme.states.any { st ->
                                    st.equals("All", ignoreCase = true) ||
                                        st.equals("All India", ignoreCase = true) ||
                                        st.equals(selectedStateFilter, ignoreCase = true)
                                }
                            val matchQ = searchQuery.isBlank() ||
                                scheme.name.contains(searchQuery, ignoreCase = true) ||
                                scheme.description.contains(searchQuery, ignoreCase = true)
                            matchSaved && matchCat && matchState && matchQ
                        }
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
                        items(filteredSchemes, key = { it.id.ifBlank { it.name } }) { scheme ->
                            val isSaved = savedIds.contains(scheme.id)
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CardWhite),
                                border = BorderStroke(1.dp, if (isSaved) DeepEmerald else BorderLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(scheme.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextCharcoal)
                                            scheme.lastVerifiedAt?.let {
                                                Text("Verified $it • ${scheme.category}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                            }
                                        }
                                        IconButton(onClick = { viewModel.toggleBookmark(scheme.id) }) {
                                            Icon(
                                                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                contentDescription = "Save Scheme",
                                                tint = if (isSaved) DeepEmerald else TextMuted,
                                            )
                                        }
                                    }

                                    Text(scheme.description, color = TextCharcoal, style = MaterialTheme.typography.bodySmall)

                                    if (scheme.benefits.isNotBlank()) {
                                        Surface(shape = RoundedCornerShape(8.dp), color = GreenBg, border = BorderStroke(1.dp, GreenTint), modifier = Modifier.fillMaxWidth()) {
                                            Text("Benefit: ${scheme.benefits}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = DeepEmerald, modifier = Modifier.padding(8.dp))
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        OutlinedButton(
                                            onClick = { viewModel.explainScheme(scheme) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(9999.dp),
                                        ) {
                                            Text("Explain", color = DeepEmerald, fontSize = 12.sp)
                                        }
                                        (scheme.officialPortal ?: scheme.link)?.takeIf { it.isNotBlank() }?.let { url ->
                                            Button(
                                                onClick = { runCatching { uriHandler.openUri(url) } },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(9999.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                                            ) {
                                                Text("Portal", fontSize = 12.sp)
                                                Spacer(Modifier.width(4.dp))
                                                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab 1: Eligibility Questionnaire & Deterministic + Optional AI Match Results
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardWhite), border = BorderStroke(1.dp, BorderLight), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.schemes_modal_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextCharcoal)
                            formNotice?.let { Text(it, color = DeepEmerald, style = MaterialTheme.typography.bodySmall) }

                            VoiceInputField(
                                value = stateField, onValueChange = { stateField = it },
                                label = stringResource(R.string.schemes_state_label), fieldKey = "state", lang = currentLang,
                                speechToText = viewModel.speechToText, modifier = Modifier.fillMaxWidth(), onMessage = { formNotice = it },
                            )
                            VoiceInputField(
                                value = farmSize, onValueChange = { farmSize = it },
                                label = stringResource(R.string.schemes_farm_size_label), fieldKey = "farmSize", lang = currentLang,
                                speechToText = viewModel.speechToText, modifier = Modifier.fillMaxWidth(), onMessage = { formNotice = it },
                            )
                            VoiceInputField(
                                value = crop, onValueChange = { crop = it },
                                label = stringResource(R.string.schemes_primary_crop_label), fieldKey = "cropName", lang = currentLang,
                                speechToText = viewModel.speechToText, modifier = Modifier.fillMaxWidth(), onMessage = { formNotice = it },
                            )
                            VoiceInputField(
                                value = soilType, onValueChange = { soilType = it },
                                label = stringResource(R.string.schemes_soil_type_label), fieldKey = "soilType", lang = currentLang,
                                speechToText = viewModel.speechToText, modifier = Modifier.fillMaxWidth(), onMessage = { formNotice = it },
                            )
                            VoiceInputField(
                                value = landType, onValueChange = { landType = it },
                                label = stringResource(R.string.schemes_irrigation_land_type), fieldKey = "landType", lang = currentLang,
                                speechToText = viewModel.speechToText, modifier = Modifier.fillMaxWidth(), onMessage = { formNotice = it },
                            )

                            FillByVoiceButton(
                                fields = listOf("state", "farmSize", "crop", "soilType", "landType"),
                                fieldLabels = mapOf(
                                    "state" to stringResource(R.string.schemes_state_label),
                                    "farmSize" to stringResource(R.string.schemes_farm_size_label),
                                    "crop" to stringResource(R.string.schemes_primary_crop_label),
                                    "soilType" to stringResource(R.string.schemes_soil_type_label),
                                    "landType" to stringResource(R.string.schemes_irrigation_land_type),
                                ),
                                lang = currentLang,
                                speechToText = viewModel.speechToText,
                                fieldExtractor = viewModel.fieldExtractor,
                                onFill = { data ->
                                    data["state"]?.let { stateField = it }
                                    data["farmSize"]?.let { farmSize = it }
                                    data["crop"]?.let { crop = it }
                                    data["soilType"]?.let { soilType = it }
                                    data["landType"]?.let { landType = it }
                                },
                                onMessage = { formNotice = it },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = { viewModel.evaluateEligibility(stateField, farmSize, crop, soilType, landType) },
                                enabled = !state.isLoading,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(9999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald)
                            ) {
                                Text(stringResource(R.string.schemes_get_subsidies_btn), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    LoadingOverlay(state.isLoading)
                    state.error?.let {
                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = ErrorContainer), modifier = Modifier.fillMaxWidth()) {
                            Text(it, color = OnErrorContainer, modifier = Modifier.padding(12.dp))
                        }
                    }

                    state.eligibilityResult?.let { res ->
                        res.aiSummary?.let { aiSum -> AiResponseCard("AI Counselor Priority Plan", aiSum) }
                        Text("Eligibility Matches (Verified ${res.lastVerifiedAt})", fontWeight = FontWeight.Bold, color = TextCharcoal)
                        res.matches.forEach { m ->
                            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardWhite), border = BorderStroke(1.dp, BorderLight), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(m.scheme.name, fontWeight = FontWeight.Bold, color = TextCharcoal, modifier = Modifier.weight(1f))
                                        Surface(shape = RoundedCornerShape(9999.dp), color = if (m.eligibilityStatus.contains("Eligible")) GreenTint else AmberTint) {
                                            Text(m.eligibilityStatus, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (m.eligibilityStatus.contains("Eligible")) DeepEmerald else SecondaryOrange, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                        }
                                    }
                                    m.matchedReasons.forEach { r -> Text("✓ $r", style = MaterialTheme.typography.bodySmall, color = DeepEmerald) }
                                    m.cautions.forEach { c -> Text("⚠ $c", style = MaterialTheme.typography.bodySmall, color = SecondaryOrange) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
