package com.kissansevaai.feature.planner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kissansevaai.R
import com.kissansevaai.core.ai.FieldExtractor
import com.kissansevaai.core.ai.SpeechToText
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.location.SavedFarmLocation
import com.kissansevaai.core.model.CreatePlanRequest
import com.kissansevaai.core.model.CropPlanDto
import com.kissansevaai.core.model.PlannerTaskDto
import com.kissansevaai.core.repository.FarmLocationRepository
import com.kissansevaai.core.repository.PlannerRepository
import com.kissansevaai.core.sync.OfflineSyncManager
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

data class PlannerUiState(
    val isLoading: Boolean = false,
    val plans: List<CropPlanDto> = emptyList(),
    val tasks: List<PlannerTaskDto> = emptyList(),
    val selectedPlanId: String? = null,
    val aiStatusMessage: String? = null,
    val error: String? = null,
)

@HiltViewModel
class CropPlannerViewModel @Inject constructor(
    private val repository: PlannerRepository,
    private val farmLocationRepository: FarmLocationRepository,
    private val offlineSyncManager: OfflineSyncManager,
    private val dataStore: DataStoreManager,
    val speechToText: SpeechToText,
    val fieldExtractor: FieldExtractor,
) : ViewModel() {

    val language: StateFlow<String> = dataStore.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    val pendingSyncCount: StateFlow<Int> = offlineSyncManager.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val savedFarmLocation: StateFlow<SavedFarmLocation?> = farmLocationRepository.observeSavedFarmLocation()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _uiState = MutableStateFlow(PlannerUiState(isLoading = true))
    val uiState: StateFlow<PlannerUiState> = _uiState

    init {
        loadPlans()
    }

    fun loadPlans() {
        viewModelScope.launch {
            repository.getPlansAndTasks().collect { res ->
                when (res) {
                    is Resource.Loading -> _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    is Resource.Success -> {
                        val activePlanId = _uiState.value.selectedPlanId
                            ?.takeIf { id -> res.data.plans.any { it.id == id } }
                            ?: res.data.plans.firstOrNull()?.id
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            plans = res.data.plans,
                            tasks = res.data.tasks,
                            selectedPlanId = activePlanId,
                            error = null,
                        )
                    }
                    is Resource.Error -> _uiState.value = _uiState.value.copy(isLoading = false, error = res.message)
                }
            }
        }
    }

    fun selectPlan(planId: String) {
        _uiState.value = _uiState.value.copy(selectedPlanId = planId)
    }

    fun createPlan(
        cropName: String,
        variety: String,
        stage: String,
        soilType: String,
        irrigation: String,
        farmSizeAcres: Double,
        location: String,
        sowingDate: String = "",
        customTask: String = "",
    ) {
        viewModelScope.launch {
            val lang = dataStore.language.first()
            val farm = savedFarmLocation.value ?: farmLocationRepository.getSavedFarmLocationOnce()
            repository.createPlan(
                CreatePlanRequest(
                    cropName = cropName,
                    variety = variety,
                    stage = stage,
                    soilType = soilType,
                    irrigationType = irrigation,
                    farmSizeAcres = farmSizeAcres,
                    locationName = location.ifBlank { farm?.formattedSummary.orEmpty() },
                    latitude = farm?.latitude,
                    longitude = farm?.longitude,
                    sowingDate = sowingDate,
                    customTask = customTask,
                    language = lang,
                )
            ).collect { res ->
                when (res) {
                    is Resource.Loading -> _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    is Resource.Success -> {
                        val created = res.data.plan
                        val updatedPlans = listOf(created) + _uiState.value.plans.filterNot { it.id == created.id }
                        val updatedTasks = res.data.tasks + _uiState.value.tasks.filterNot { it.planId == created.id }
                        val msg = when {
                            res.data.weatherSource == "offline_queued" -> "Saved offline in Room; full weather plan will sync when online."
                            !res.data.aiAvailable && !res.data.aiServiceStatus.isNullOrBlank() -> "7-day weather plan created via deterministic rules (AI note: ${res.data.aiServiceStatus})."
                            else -> "7-day weather-aware crop plan generated."
                        }
                        _uiState.value = PlannerUiState(
                            isLoading = false,
                            plans = updatedPlans,
                            tasks = updatedTasks,
                            selectedPlanId = created.id,
                            aiStatusMessage = msg,
                        )
                    }
                    is Resource.Error -> _uiState.value = _uiState.value.copy(isLoading = false, error = res.message)
                }
            }
        }
    }

    fun toggleTask(task: PlannerTaskDto) {
        val id = task.id ?: return
        val nextStatus = if (task.status == "completed") "pending" else "completed"
        viewModelScope.launch {
            repository.toggleTaskStatus(id, nextStatus)
            val updated = _uiState.value.tasks.map {
                if (it.id == id) it.copy(status = nextStatus) else it
            }
            _uiState.value = _uiState.value.copy(tasks = updated)
        }
    }

    fun regenerateActivePlan() {
        val planId = _uiState.value.selectedPlanId ?: return
        viewModelScope.launch {
            repository.regenerateTasks(planId).collect { res ->
                when (res) {
                    is Resource.Loading -> _uiState.value = _uiState.value.copy(isLoading = true)
                    is Resource.Success -> {
                        val rest = _uiState.value.tasks.filterNot { it.planId == planId }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            tasks = res.data.tasks + rest,
                            aiStatusMessage = "Tasks regenerated against latest weather forecast.",
                        )
                    }
                    is Resource.Error -> _uiState.value = _uiState.value.copy(isLoading = false, error = res.message)
                }
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            offlineSyncManager.flushPendingOperations()
            loadPlans()
        }
    }
}

@Composable
fun CropPlannerScreen(onBack: () -> Unit, viewModel: CropPlannerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val lang by viewModel.language.collectAsState()
    val pendingSync by viewModel.pendingSyncCount.collectAsState()
    val savedFarm by viewModel.savedFarmLocation.collectAsState()

    var showCreateForm by remember { mutableStateOf(false) }
    var cropName by remember { mutableStateOf("Wheat") }
    var variety by remember { mutableStateOf("") }
    var sowingDate by remember { mutableStateOf("") }
    var customTask by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf("vegetative") }
    var soilType by remember(savedFarm) { mutableStateOf(savedFarm?.soilType?.ifBlank { "Loamy" } ?: "Loamy") }
    var irrigationType by remember(savedFarm) { mutableStateOf(savedFarm?.irrigationType?.ifBlank { "Drip / Canal" } ?: "Drip / Canal") }
    var farmSize by remember(savedFarm) { mutableStateOf(savedFarm?.farmSizeAcres?.toString() ?: "2.5") }
    var location by remember(savedFarm) { mutableStateOf(savedFarm?.formattedSummary.orEmpty()) }
    var ambiguousWarnings by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var notice by remember { mutableStateOf<String?>(null) }

    val stages = listOf("pre_sowing", "sowing", "vegetative", "flowering", "fruiting", "maturity", "harvest")

    FeatureScaffold(
        title = stringResource(R.string.planner_title),
        subtitle = "Weather-Aware 7-Day Farming Calendar",
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (pendingSync > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AmberTint,
                    border = BorderStroke(1.dp, SecondaryOrange),
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.syncNow() },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "$pendingSync offline change(s) queued — tap to sync now",
                            color = SecondaryOrange,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Icon(Icons.Default.Sync, null, tint = SecondaryOrange)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { showCreateForm = !showCreateForm },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(9999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                ) {
                    Icon(if (showCreateForm) Icons.Default.Close else Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (showCreateForm) "Hide Plan Form" else stringResource(R.string.planner_new_plan),
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (state.selectedPlanId != null) {
                    OutlinedButton(
                        onClick = { viewModel.regenerateActivePlan() },
                        shape = RoundedCornerShape(9999.dp),
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = DeepEmerald, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Weather Sync", color = DeepEmerald)
                    }
                }
            }

            if (showCreateForm) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Create 7-Day Crop Schedule", fontWeight = FontWeight.Bold, color = DeepEmerald)
                        FillByVoiceButton(
                            fields = listOf("cropName", "sowingDate", "farmSize", "soilType", "irrigationType", "customTask", "location"),
                            fieldLabels = mapOf(
                                "cropName" to "Crop Name",
                                "sowingDate" to "Sowing Date (YYYY-MM-DD)",
                                "farmSize" to "Field Size (Acres)",
                                "soilType" to "Soil Type",
                                "irrigationType" to "Irrigation Method",
                                "customTask" to "Custom Task",
                                "location" to "Farm Location",
                            ),
                            lang = lang,
                            speechToText = viewModel.speechToText,
                            fieldExtractor = viewModel.fieldExtractor,
                            onFill = { map ->
                                map["cropName"]?.takeIf { it.isNotBlank() }?.let { cropName = it }
                                map["sowingDate"]?.takeIf { it.isNotBlank() }?.let { sowingDate = it }
                                map["farmSize"]?.takeIf { it.isNotBlank() }?.let { farmSize = it }
                                map["soilType"]?.takeIf { it.isNotBlank() }?.let { soilType = it }
                                map["irrigationType"]?.takeIf { it.isNotBlank() }?.let { irrigationType = it }
                                map["customTask"]?.takeIf { it.isNotBlank() }?.let { customTask = it }
                                map["location"]?.takeIf { it.isNotBlank() }?.let { location = it }
                            },
                            onAmbiguitiesDetected = { ambiguousWarnings = it },
                            onMessage = { notice = it },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        VoiceInputField(
                            value = cropName,
                            onValueChange = { cropName = it },
                            label = "Crop Name*",
                            fieldKey = "cropName",
                            lang = lang,
                            speechToText = viewModel.speechToText,
                            onMessage = { notice = it },
                        )

                        VoiceInputField(
                            value = sowingDate,
                            onValueChange = { sowingDate = it },
                            label = "Sowing Date (YYYY-MM-DD)",
                            fieldKey = "sowingDate",
                            lang = lang,
                            speechToText = viewModel.speechToText,
                            placeholder = "e.g. 2026-11-10",
                            supportingWarning = ambiguousWarnings["sowingDate"],
                            onMessage = { notice = it },
                        )

                        OutlinedTextField(
                            value = variety,
                            onValueChange = { variety = it },
                            label = { Text("Variety (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        )

                        Text("Growth Stage", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextCharcoal)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            stages.forEach { st ->
                                FilterChip(
                                    selected = stage == st,
                                    onClick = { stage = st },
                                    label = { Text(st.replace('_', ' ').replaceFirstChar { it.uppercase() }) },
                                )
                            }
                        }

                        VoiceInputField(
                            value = soilType,
                            onValueChange = { soilType = it },
                            label = "Soil Type",
                            fieldKey = "soilType",
                            lang = lang,
                            speechToText = viewModel.speechToText,
                            onMessage = { notice = it },
                        )
                        VoiceInputField(
                            value = irrigationType,
                            onValueChange = { irrigationType = it },
                            label = "Irrigation Method",
                            fieldKey = "irrigationType",
                            lang = lang,
                            speechToText = viewModel.speechToText,
                            onMessage = { notice = it },
                        )
                        VoiceInputField(
                            value = farmSize,
                            onValueChange = { farmSize = it },
                            label = "Field Size (Acres)",
                            fieldKey = "farmSize",
                            lang = lang,
                            speechToText = viewModel.speechToText,
                            supportingWarning = ambiguousWarnings["farmSize"],
                            onMessage = { notice = it },
                        )
                        VoiceInputField(
                            value = customTask,
                            onValueChange = { customTask = it },
                            label = "Custom Task (Optional)",
                            fieldKey = "customTask",
                            lang = lang,
                            speechToText = viewModel.speechToText,
                            placeholder = "e.g. Apply zinc sulfate spray",
                            onMessage = { notice = it },
                        )
                        VoiceInputField(
                            value = location,
                            onValueChange = { location = it },
                            label = "District / Location (uses saved farm by default)",
                            fieldKey = "location",
                            lang = lang,
                            speechToText = viewModel.speechToText,
                            onMessage = { notice = it },
                        )

                        Button(
                            onClick = {
                                if (cropName.isNotBlank()) {
                                    viewModel.createPlan(
                                        cropName = cropName.trim(),
                                        variety = variety.trim(),
                                        stage = stage,
                                        soilType = soilType.trim(),
                                        irrigation = irrigationType.trim(),
                                        farmSizeAcres = farmSize.toDoubleOrNull() ?: 1.0,
                                        location = location.trim(),
                                        sowingDate = sowingDate.trim(),
                                        customTask = customTask.trim(),
                                    )
                                    showCreateForm = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(9999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                        ) {
                            Text("Generate 7-Day Crop Plan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            (notice ?: state.aiStatusMessage)?.let { msg ->
                Surface(shape = RoundedCornerShape(12.dp), color = GreenTint, modifier = Modifier.fillMaxWidth()) {
                    Text(msg, color = DeepEmerald, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }

            state.error?.let { err ->
                Surface(shape = RoundedCornerShape(12.dp), color = ErrorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(err, color = ErrorRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }

            if (state.isLoading && state.plans.isEmpty()) {
                LoadingOverlay(true)
            }

            // Plan Switcher
            if (state.plans.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.plans.forEach { plan ->
                        val pId = plan.id ?: return@forEach
                        FilterChip(
                            selected = state.selectedPlanId == pId,
                            onClick = { viewModel.selectPlan(pId) },
                            label = { Text("${plan.cropName} (${plan.stage.replace('_', ' ')})") },
                        )
                    }
                }
            }

            val selectedPlan = state.plans.find { it.id == state.selectedPlanId }
            val activeTasks = state.tasks.filter { it.planId == state.selectedPlanId }

            selectedPlan?.let { plan ->
                if (!plan.aiAdvisoryNote.isNullOrBlank()) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = GreenBg),
                        border = BorderStroke(1.dp, GreenTint),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Agronomist Stage Note (${plan.generationSource})", fontWeight = FontWeight.Bold, color = DeepEmerald)
                            Text(plan.aiAdvisoryNote, style = MaterialTheme.typography.bodySmall, color = TextCharcoal)
                        }
                    }
                }

                Text(
                    "Scheduled Tasks (${activeTasks.count { it.status == "completed" }}/${activeTasks.size} done)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextCharcoal,
                )

                activeTasks.forEach { task ->
                    val done = task.status == "completed"
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = if (done) SurfaceContainerLow else CardWhite),
                        border = BorderStroke(1.dp, if (task.weatherAdjusted) SecondaryOrange else BorderLight),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleTask(task) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Checkbox(
                                checked = done,
                                onCheckedChange = { viewModel.toggleTask(task) },
                                colors = CheckboxDefaults.colors(checkedColor = DeepEmerald),
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        task.title,
                                        fontWeight = FontWeight.Bold,
                                        color = if (done) TextMuted else TextCharcoal,
                                        textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Surface(shape = RoundedCornerShape(9999.dp), color = AmberTint) {
                                        Text(
                                            task.scheduledDate,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SecondaryOrange,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                Text(task.description, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                if (task.weatherAdjusted && task.weatherReason.isNotBlank()) {
                                    Surface(shape = RoundedCornerShape(8.dp), color = AmberTint) {
                                        Text(
                                            "Weather-Adjusted: ${task.weatherReason}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SecondaryOrange,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } ?: run {
                if (!state.isLoading) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EventNote, null, tint = DeepEmerald, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No crop plans yet", fontWeight = FontWeight.Bold, color = TextCharcoal)
                            Text("Tap '+ New Plan' above to generate a 7-day weather-aware farming schedule.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }
            }
        }
    }
}
