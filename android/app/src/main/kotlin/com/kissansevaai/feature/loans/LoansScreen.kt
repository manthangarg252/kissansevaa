package com.kissansevaai.feature.loans

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
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
import com.kissansevaai.core.model.LoanAdvisorRequest
import com.kissansevaai.core.model.LoanAdvisorResult
import com.kissansevaai.core.model.LoanProduct
import com.kissansevaai.core.model.LoanProfile
import com.kissansevaai.core.repository.LoansRepository
import com.kissansevaai.ui.component.FeatureScaffold
import com.kissansevaai.ui.component.LoadingOverlay
import com.kissansevaai.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.roundToLong

@HiltViewModel
class LoansViewModel @Inject constructor(
    private val repository: LoansRepository,
    private val ds: DataStoreManager,
) : ViewModel() {

    private val _loans = MutableStateFlow<List<LoanProduct>>(emptyList())
    val loans: StateFlow<List<LoanProduct>> = _loans

    private val _isLoansLoading = MutableStateFlow(true)
    val isLoansLoading: StateFlow<Boolean> = _isLoansLoading

    private val _uiState = MutableStateFlow(LoansUiState())
    val uiState: StateFlow<LoansUiState> = _uiState

    init {
        loadLoans()
    }

    fun loadLoans() {
        viewModelScope.launch {
            repository.getLoanProducts().collect { res ->
                when (res) {
                    is Resource.Loading -> _isLoansLoading.value = true
                    is Resource.Success -> {
                        _isLoansLoading.value = false
                        _loans.value = res.data
                    }
                    is Resource.Error -> _isLoansLoading.value = false
                }
            }
        }
    }

    fun checkEligibility(profile: LoanProfile) {
        viewModelScope.launch {
            val lang = ds.language.first()
            val req = LoanAdvisorRequest(profile, lang)
            repository.getLoanAdvice(req).collect { res ->
                when (res) {
                    is Resource.Loading -> _uiState.value = LoansUiState(isLoading = true)
                    is Resource.Success -> _uiState.value = LoansUiState(result = res.data)
                    is Resource.Error -> _uiState.value = LoansUiState(error = res.message)
                }
            }
        }
    }
}

data class LoansUiState(val isLoading: Boolean = false, val result: LoanAdvisorResult? = null, val error: String? = null)

@Composable
fun LoansScreen(onBack: () -> Unit, viewModel: LoansViewModel = hiltViewModel()) {
    val loans by viewModel.loans.collectAsState()
    val isLoansLoading by viewModel.isLoansLoading.collectAsState()
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    // EMI Calculator State
    var loanAmount by remember { mutableFloatStateOf(100000f) }
    var interestRate by remember { mutableStateOf("7.0") }
    var tenureMonths by remember { mutableStateOf("12") }
    var emiCalculation by remember { mutableStateOf<Triple<Long, Long, Long>?>(null) }

    // AI Form State
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var farmSize by remember { mutableStateOf("") }
    var income by remember { mutableStateOf("") }
    var existingLoans by remember { mutableStateOf("") }
    var creditScore by remember { mutableStateOf("") }
    var cropType by remember { mutableStateOf("") }
    var stateField by remember { mutableStateOf("") }

    val uriHandler = LocalUriHandler.current

    FeatureScaffold(
        title = stringResource(R.string.loans_title),
        subtitle = stringResource(R.string.loans_subtitle),
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
                    text = { Text("Loan Products", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.AccountBalance, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("EMI Calculator", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Calculate, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("AI Advisor", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
                )
            }

            when (selectedTab) {
                0 -> {
                    // Loan Products List
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        LoadingOverlay(isLoansLoading && loans.isEmpty())
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(loans, key = { it.id.ifBlank { it.name } }) { loan ->
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
                                                text = loan.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Slate100,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Emerald600.copy(alpha = 0.2f),
                                            ) {
                                                Text(
                                                    text = loan.type,
                                                    color = Emerald400,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        if (loan.provider.isNotBlank()) {
                                            Text(text = "Provider: ${loan.provider}", color = Slate400, style = MaterialTheme.typography.bodySmall)
                                        }

                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            if (loan.interestRate.isNotBlank()) {
                                                Column(Modifier.weight(1f)) {
                                                    Text("Interest Rate", color = Slate400, style = MaterialTheme.typography.labelSmall)
                                                    Text(loan.interestRate, color = Emerald400, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                }
                                            }
                                            if (loan.maxAmount.isNotBlank()) {
                                                Column(Modifier.weight(1f)) {
                                                    Text("Max Amount", color = Slate400, style = MaterialTheme.typography.labelSmall)
                                                    Text(loan.maxAmount, color = Slate200, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                }
                                            }
                                        }

                                        Text(text = loan.description, color = Slate300, style = MaterialTheme.typography.bodyMedium)

                                        if (loan.benefits.isNotEmpty()) {
                                            Text("Key Benefits:", color = Slate400, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            loan.benefits.forEach { b ->
                                                Text("• $b", color = Slate300, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }

                                        loan.link?.takeIf { it.isNotBlank() }?.let { linkUrl ->
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
                                                Text("Apply / Details", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Smart EMI Calculator
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Slate800),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("Smart Farm Loan EMI Calculator", style = MaterialTheme.typography.titleMedium, color = Slate100, fontWeight = FontWeight.Bold)

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Loan Amount", color = Slate400, style = MaterialTheme.typography.labelMedium)
                                        Text("₹${loanAmount.roundToLong().toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}", color = Emerald400, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = loanAmount,
                                        onValueChange = { loanAmount = it },
                                        valueRange = 10000f..5000000f,
                                        steps = 498,
                                        colors = SliderDefaults.colors(thumbColor = Emerald400, activeTrackColor = Emerald600)
                                    )
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("₹10,000", color = Slate500, style = MaterialTheme.typography.labelSmall)
                                        Text("₹50,00,000", color = Slate500, style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = interestRate,
                                        onValueChange = { interestRate = it },
                                        label = { Text("Interest Rate (%)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = tenureMonths,
                                        onValueChange = { tenureMonths = it },
                                        label = { Text("Tenure (Months)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Button(
                                    onClick = {
                                        val p = loanAmount.toDouble()
                                        val r = (interestRate.toDoubleOrNull() ?: 7.0) / 12.0 / 100.0
                                        val n = tenureMonths.toIntOrNull() ?: 12
                                        if (r > 0.0 && n > 0) {
                                            val factor = (1.0 + r).pow(n.toDouble())
                                            val emi = (p * r * factor) / (factor - 1.0)
                                            val totalPayable = emi * n
                                            val totalInterest = totalPayable - p
                                            emiCalculation = Triple(emi.roundToLong(), totalInterest.roundToLong(), totalPayable.roundToLong())
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                                ) {
                                    Text(stringResource(R.string.loans_emi_calc), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        emiCalculation?.let { (emi, totalInterest, totalPayable) ->
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Slate900),
                                border = null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("MONTHLY ESTIMATED EMI", color = Slate400, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text("₹$emi", color = Emerald400, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)

                                    HorizontalDivider(color = Slate800, modifier = Modifier.padding(vertical = 4.dp))

                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(stringResource(R.string.loans_total_interest), color = Slate400, style = MaterialTheme.typography.labelSmall)
                                            Text("₹$totalInterest", color = Amber500, fontWeight = FontWeight.Bold)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(stringResource(R.string.loans_total_payable), color = Slate400, style = MaterialTheme.typography.labelSmall)
                                            Text("₹$totalPayable", color = Slate100, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // AI Advisor Form
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Slate800), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(age, { age = it }, label = { Text("Age") }, singleLine = true, modifier = Modifier.weight(1f))
                                    OutlinedTextField(farmSize, { farmSize = it }, label = { Text("Farm Size") }, singleLine = true, modifier = Modifier.weight(1f))
                                }
                                OutlinedTextField(income, { income = it }, label = { Text("Annual Income") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(existingLoans, { existingLoans = it }, label = { Text("Existing Loans") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(creditScore, { creditScore = it }, label = { Text("Credit Score") }, singleLine = true, modifier = Modifier.weight(1f))
                                    OutlinedTextField(cropType, { cropType = it }, label = { Text("Crop Type") }, singleLine = true, modifier = Modifier.weight(1f))
                                }
                                OutlinedTextField(stateField, { stateField = it }, label = { Text("State") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        Button(
                            onClick = { viewModel.checkEligibility(LoanProfile(name, age, farmSize, income, existingLoans, creditScore, cropType, stateField)) },
                            enabled = !state.isLoading && name.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        ) { Text(stringResource(R.string.loans_check_eligibility)) }
                        LoadingOverlay(state.isLoading)
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        state.result?.let { r ->
                            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Slate800), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    r.recommendedLoan?.let { Text("${stringResource(R.string.loans_recommended_loan)}: $it", color = Emerald400, style = MaterialTheme.typography.titleMedium) }
                                    r.emiEstimate?.let { Text("${stringResource(R.string.loans_monthly_emi)}: $it", color = Slate200) }
                                    r.riskAdvisory?.let { Spacer(Modifier.height(4.dp)); Text(stringResource(R.string.loans_risk_advisory), color = Amber500, style = MaterialTheme.typography.titleSmall); Text(it, color = Slate300) }
                                    r.response?.let { Text(it, color = Slate200) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
