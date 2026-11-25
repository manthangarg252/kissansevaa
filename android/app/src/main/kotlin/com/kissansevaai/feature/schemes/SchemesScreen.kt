package com.kissansevaai.feature.schemes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.model.Scheme
import com.kissansevaai.core.model.SchemeRecommendationRequest
import com.kissansevaai.core.repository.SchemesRepository
import com.kissansevaai.core.ai.FieldExtractor
import com.kissansevaai.core.ai.SpeechToText
import com.kissansevaai.ui.component.AiResponseCard
import com.kissansevaai.ui.component.FeatureScaffold
import com.kissansevaai.ui.component.FieldMicButton
import com.kissansevaai.ui.component.LoadingOverlay
import com.kissansevaai.ui.component.VoiceFormFillButton
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
    private val ds: DataStoreManager,
    val speechToText: SpeechToText,
    val fieldExtractor: FieldExtractor,
) : ViewModel() {

    val language: StateFlow<String> = ds.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

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
    var selectedCategory by remember { mutableStateOf("All") }

    var stateField by remember { mutableStateOf("Gujarat") }
    var farmSize by remember { mutableStateOf("5") }
    var crop by remember { mutableStateOf("Cotton & Groundnut") }
    var soilType by remember { mutableStateOf("Black Cotton") }
    var landType by remember { mutableStateOf("Irrigated") }
    var formNotice by remember { mutableStateOf<String?>(null) }
    val currentLang by viewModel.language.collectAsState()

    val uriHandler = LocalUriHandler.current

    FeatureScaffold(
        title = stringResource(R.string.schemes_title),
        subtitle = "सरकारी योजनाएं, अनुदान एवं पात्रता",
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Modern Tab Row
            Surface(
                color = CardWhite,
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = if (selectedTab == 0) DeepEmerald else SurfaceContainerLow,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 0 }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = if (selectedTab == 0) Color.White else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Schemes (योजनाएं)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 0) Color.White else TextCharcoal
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = if (selectedTab == 1) DeepEmerald else SurfaceContainerLow,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 1 }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (selectedTab == 1) Color.White else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "AI Advisor (सलाहकार)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 1) Color.White else TextCharcoal
                            )
                        }
                    }
                }
            }

            if (selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.schemes_search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = DeepEmerald) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, null, tint = TextMuted)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepEmerald,
                            unfocusedBorderColor = BorderMedium
                        )
                    )

                    // Filter chips row
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(listOf("All", "Subsidies", "Financial", "Insurance", "Irrigation", "Organic", "Technology")) { cat ->
                            Surface(
                                shape = RoundedCornerShape(9999.dp),
                                color = if (selectedCategory == cat) GreenTint else CardWhite,
                                border = BorderStroke(1.dp, if (selectedCategory == cat) DeepEmerald else BorderMedium),
                                modifier = Modifier.clickable { selectedCategory = cat }
                            ) {
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedCategory == cat) DeepEmerald else TextCharcoal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    LoadingOverlay(isSchemesLoading && schemes.isEmpty())

                    val filteredSchemes = remember(schemes, searchQuery, selectedCategory) {
                        var list = schemes
                        if (selectedCategory != "All") {
                            list = list.filter { scheme ->
                                when (selectedCategory) {
                                    "Subsidies" -> scheme.description.contains("subsidy", ignoreCase = true) ||
                                            scheme.description.contains("grant", ignoreCase = true) ||
                                            scheme.description.contains("support", ignoreCase = true) ||
                                            scheme.category.contains("Income", ignoreCase = true) ||
                                            scheme.category.contains("Organic", ignoreCase = true)
                                    "Financial" -> scheme.category.contains("Financial", ignoreCase = true) ||
                                            scheme.category.contains("Income", ignoreCase = true) ||
                                            scheme.name.contains("KCC", ignoreCase = true)
                                    else -> scheme.category.contains(selectedCategory, ignoreCase = true) ||
                                            scheme.description.contains(selectedCategory, ignoreCase = true)
                                }
                            }
                        }
                        if (searchQuery.isNotBlank()) {
                            list = list.filter {
                                it.name.contains(searchQuery, ignoreCase = true) ||
                                it.category.contains(searchQuery, ignoreCase = true) ||
                                it.description.contains(searchQuery, ignoreCase = true)
                            }
                        }
                        list
                    }

                    if (filteredSchemes.isEmpty() && !isSchemesLoading) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CardWhite),
                            border = BorderStroke(1.dp, BorderLight),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.SearchOff, null, tint = TextMuted, modifier = Modifier.size(40.dp))
                                Text(stringResource(R.string.schemes_no_schemes_found), fontWeight = FontWeight.Bold, color = TextCharcoal)
                                Text(stringResource(R.string.schemes_no_schemes_desc), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                Button(
                                    onClick = {
                                        searchQuery = ""
                                        selectedCategory = "All"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                                    shape = RoundedCornerShape(9999.dp)
                                ) {
                                    Text(stringResource(R.string.schemes_reset_filters), fontWeight = FontWeight.Bold)
                                }
                            }
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
                                colors = CardDefaults.cardColors(containerColor = CardWhite),
                                border = BorderStroke(1.dp, BorderLight),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = scheme.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextCharcoal,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(9999.dp),
                                            color = GreenTint
                                        ) {
                                            Text(
                                                text = scheme.category,
                                                color = DeepEmerald,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = scheme.description,
                                        color = TextCharcoal,
                                        style = MaterialTheme.typography.bodyMedium,
                                        lineHeight = 20.sp
                                    )

                                    if (scheme.benefits.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = GreenBg,
                                            border = BorderStroke(1.dp, GreenTint),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.MonetizationOn, null, tint = DeepEmerald, modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = "लाभ (Benefit): ${scheme.benefits}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DeepEmerald
                                                )
                                            }
                                        }
                                    }

                                    if (scheme.eligibility.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(
                                                text = "पात्रता (Eligibility):",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = TextMuted
                                            )
                                            scheme.eligibility.forEach { item ->
                                                Row(
                                                    verticalAlignment = Alignment.Top,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(Icons.Default.Check, null, tint = DeepEmerald, modifier = Modifier.size(14.dp))
                                                    Text(item, color = TextCharcoal, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }

                                    scheme.link?.takeIf { it.isNotBlank() }?.let { schemeUrl ->
                                        Button(
                                            onClick = {
                                                try { uriHandler.openUri(schemeUrl) } catch (_: Exception) {}
                                            },
                                            modifier = Modifier.fillMaxWidth().height(42.dp),
                                            shape = RoundedCornerShape(9999.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(stringResource(R.string.schemes_apply_btn), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab 1: AI Advisor
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.schemes_modal_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextCharcoal
                            )
                            Text(
                                text = stringResource(R.string.schemes_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )

                            formNotice?.let { notice ->
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = GreenBg),
                                    border = BorderStroke(1.dp, GreenTint),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = notice,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DeepEmerald,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = stateField,
                                onValueChange = { stateField = it },
                                label = { Text(stringResource(R.string.schemes_state_label)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    FieldMicButton(
                                        speechToText = viewModel.speechToText,
                                        lang = currentLang,
                                        onResult = { stateField = it },
                                        onError = { formNotice = it }
                                    )
                                }
                            )
                            OutlinedTextField(
                                value = farmSize,
                                onValueChange = { farmSize = it },
                                label = { Text(stringResource(R.string.schemes_farm_size_label)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    FieldMicButton(
                                        speechToText = viewModel.speechToText,
                                        lang = currentLang,
                                        onResult = { farmSize = it.filter { c -> c.isDigit() || c == '.' }.ifBlank { it } },
                                        onError = { formNotice = it }
                                    )
                                }
                            )
                            OutlinedTextField(
                                value = crop,
                                onValueChange = { crop = it },
                                label = { Text(stringResource(R.string.schemes_primary_crop_label)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    FieldMicButton(
                                        speechToText = viewModel.speechToText,
                                        lang = currentLang,
                                        onResult = { crop = it },
                                        onError = { formNotice = it }
                                    )
                                }
                            )
                            OutlinedTextField(
                                value = soilType,
                                onValueChange = { soilType = it },
                                label = { Text(stringResource(R.string.schemes_soil_type_label)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    FieldMicButton(
                                        speechToText = viewModel.speechToText,
                                        lang = currentLang,
                                        onResult = { soilType = it },
                                        onError = { formNotice = it }
                                    )
                                }
                            )
                            OutlinedTextField(
                                value = landType,
                                onValueChange = { landType = it },
                                label = { Text(stringResource(R.string.schemes_irrigation_land_type)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    FieldMicButton(
                                        speechToText = viewModel.speechToText,
                                        lang = currentLang,
                                        onResult = { landType = it },
                                        onError = { formNotice = it }
                                    )
                                }
                            )

                            VoiceFormFillButton(
                                fields = listOf("state", "farmSize", "crop", "soilType", "landType"),
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
                                onMessage = { msg -> formNotice = msg },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = { viewModel.recommend(stateField, farmSize, crop, soilType, landType) },
                                enabled = !state.isLoading,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(9999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald)
                            ) {
                                if (state.isLoading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, null)
                                        Text(stringResource(R.string.schemes_get_subsidies_btn), fontWeight = FontWeight.Bold)
                                    }
                                }
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
                        AiResponseCard("AI Recommended Government Schemes", it)
                    }
                }
            }
        }
    }
}
