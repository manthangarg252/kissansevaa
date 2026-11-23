package com.kissansevaai.feature.loans

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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
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
    var name by remember { mutableStateOf("Ramesh Patel") }
    var age by remember { mutableStateOf("42") }
    var farmSize by remember { mutableStateOf("5") }
    var income by remember { mutableStateOf("350000") }
    var existingLoans by remember { mutableStateOf("0") }
    var creditScore by remember { mutableStateOf("720") }
    var cropType by remember { mutableStateOf("Cotton") }
    var stateField by remember { mutableStateOf("Gujarat") }

    val uriHandler = LocalUriHandler.current

    FeatureScaffold(
        title = stringResource(R.string.loans_title),
        subtitle = "किसान क्रेडिट कार्ड, ऋण एवं ईएमआई कैलकुलेटर",
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Modern Tabs
            Surface(
                color = CardWhite,
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabTitles = listOf("Loans", "EMI Calc", "AI Advisor")
                    val tabIcons = listOf(Icons.Default.AccountBalance, Icons.Default.Calculate, Icons.Default.AutoAwesome)
                    tabTitles.forEachIndexed { index, title ->
                        Surface(
                            shape = RoundedCornerShape(9999.dp),
                            color = if (selectedTab == index) DeepEmerald else SurfaceContainerLow,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = index }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = tabIcons[index],
                                    contentDescription = null,
                                    tint = if (selectedTab == index) Color.White else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedTab == index) Color.White else TextCharcoal
                                )
                            }
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    // Loan Products List
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        LoadingOverlay(isLoansLoading && loans.isEmpty())
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(loans, key = { it.id.ifBlank { it.name } }) { loan ->
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
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = loan.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = TextCharcoal,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(9999.dp),
                                                color = SecondaryFixed,
                                            ) {
                                                Text(
                                                    text = loan.type,
                                                    color = SecondaryOrange,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        if (loan.provider.isNotBlank()) {
                                            Text(text = "Provider: ${loan.provider}", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                                        }

                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            if (loan.interestRate.isNotBlank()) {
                                                Column(Modifier.weight(1f)) {
                                                    Text("Interest Rate", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                                                    Text(loan.interestRate, color = DeepEmerald, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                                }
                                            }
                                            if (loan.maxAmount.isNotBlank()) {
                                                Column(Modifier.weight(1f)) {
                                                    Text("Max Amount", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                                                    Text(loan.maxAmount, color = TextCharcoal, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                                }
                                            }
                                        }

                                        Text(text = loan.description, color = TextCharcoal, style = MaterialTheme.typography.bodyMedium)

                                        if (loan.benefits.isNotEmpty()) {
                                            Text("Key Benefits:", color = TextMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            loan.benefits.forEach { b ->
                                                Text("• $b", color = TextCharcoal, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }

                                        loan.link?.takeIf { it.isNotBlank() }?.let { linkUrl ->
                                            Button(
                                                onClick = {
                                                    try { uriHandler.openUri(linkUrl) } catch (_: Exception) {}
                                                },
                                                modifier = Modifier.align(Alignment.End),
                                                shape = RoundedCornerShape(9999.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Apply / Details", fontWeight = FontWeight.Bold)
                                                    Spacer(Modifier.width(6.dp))
                                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
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
                        modifier = Modifier
                            .fillMaxSize()
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
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("Smart Farm Loan EMI Calculator", style = MaterialTheme.typography.titleMedium, color = TextCharcoal, fontWeight = FontWeight.Bold)

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Loan Amount", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            "₹${loanAmount.roundToLong().toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}",
                                            color = DeepEmerald,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp
                                        )
                                    }
                                    Slider(
                                        value = loanAmount,
                                        onValueChange = { loanAmount = it },
                                        valueRange = 10000f..5000000f,
                                        steps = 498,
                                        colors = SliderDefaults.colors(thumbColor = DeepEmerald, activeTrackColor = DeepEmerald)
                                    )
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("₹10,000", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                                        Text("₹50,00,000", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = interestRate,
                                        onValueChange = { interestRate = it },
                                        label = { Text("Interest Rate (%)") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = tenureMonths,
                                        onValueChange = { tenureMonths = it },
                                        label = { Text("Tenure (Months)") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
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
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(9999.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald)
                                ) {
                                    Text(stringResource(R.string.loans_emi_calc), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                        }

                        emiCalculation?.let { (emi, totalInterest, totalPayable) ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = GreenBg),
                                border = BorderStroke(1.5.dp, GreenTint),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    Modifier.padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("MONTHLY ESTIMATED EMI", color = DeepEmerald, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text("₹$emi", color = DeepEmerald, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)

                                    HorizontalDivider(color = GreenTint, modifier = Modifier.padding(vertical = 4.dp))

                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(stringResource(R.string.loans_total_interest), color = TextMuted, style = MaterialTheme.typography.labelSmall)
                                            Text("₹$totalInterest", color = HarvestAmber, fontWeight = FontWeight.Bold)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(stringResource(R.string.loans_total_payable), color = TextMuted, style = MaterialTheme.typography.labelSmall)
                                            Text("₹$totalPayable", color = TextCharcoal, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // AI Advisor Form
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
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Farm Financial Profile", fontWeight = FontWeight.Bold, color = TextCharcoal)
                                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Age") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
                                    OutlinedTextField(value = farmSize, onValueChange = { farmSize = it }, label = { Text("Farm Size (acres)") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
                                }
                                OutlinedTextField(value = income, onValueChange = { income = it }, label = { Text("Annual Income (₹)") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = existingLoans, onValueChange = { existingLoans = it }, label = { Text("Existing Loans (₹)") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = creditScore, onValueChange = { creditScore = it }, label = { Text("Credit Score") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
                                    OutlinedTextField(value = cropType, onValueChange = { cropType = it }, label = { Text("Crop Type") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
                                }
                                OutlinedTextField(value = stateField, onValueChange = { stateField = it }, label = { Text("State") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                            }
                        }

                        Button(
                            onClick = { viewModel.checkEligibility(LoanProfile(name, age, farmSize, income, existingLoans, creditScore, cropType, stateField)) },
                            enabled = !state.isLoading && name.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(9999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald)
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.AutoAwesome, null)
                                    Text(stringResource(R.string.loans_check_eligibility), fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                                Text(it, color = OnErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                            }
                        }

                        state.result?.let { r ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CardWhite),
                                border = BorderStroke(1.5.dp, GreenTint),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    r.recommendedLoan?.let { Text("${stringResource(R.string.loans_recommended_loan)}: $it", color = DeepEmerald, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                                    r.emiEstimate?.let { Text("${stringResource(R.string.loans_monthly_emi)}: $it", color = TextCharcoal, fontWeight = FontWeight.SemiBold) }
                                    r.riskAdvisory?.let {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = AmberTint,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(stringResource(R.string.loans_risk_advisory), color = SecondaryOrange, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                Text(it, color = TextCharcoal, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                    r.response?.let { Text(it, color = TextCharcoal, style = MaterialTheme.typography.bodyMedium) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
