package com.kissansevaai.feature.prices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.kissansevaai.R
import com.kissansevaai.core.model.MarketPrice
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.repository.PricesRepository
import com.kissansevaai.ui.component.FeatureScaffold
import com.kissansevaai.ui.component.LoadingOverlay
import com.kissansevaai.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PricesViewModel @Inject constructor(
    private val repository: PricesRepository,
) : ViewModel() {
    private val _prices = MutableStateFlow<List<MarketPrice>>(emptyList())
    val prices: StateFlow<List<MarketPrice>> = _prices
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init { fetch() }

    fun fetch(crop: String? = null) {
        viewModelScope.launch {
            repository.getMarketPrices(crop = crop).collect { res ->
                when (res) {
                    is Resource.Loading -> {
                        _isLoading.value = true
                        _error.value = null
                    }
                    is Resource.Success -> {
                        _isLoading.value = false
                        _prices.value = res.data
                        _error.value = null
                    }
                    is Resource.Error -> {
                        _isLoading.value = false
                        _error.value = res.message
                    }
                }
            }
        }
    }
}

fun getCropImageUrl(crop: String): String {
    return when {
        crop.contains("Wheat", true) -> "https://images.unsplash.com/photo-1574943320219-553eb213f72d?auto=format&fit=crop&w=400&q=70"
        crop.contains("Rice", true) || crop.contains("Paddy", true) -> "https://images.unsplash.com/photo-1586201375761-83865001e31c?auto=format&fit=crop&w=400&q=70"
        crop.contains("Onion", true) -> "https://images.unsplash.com/photo-1618512496248-a07fe83aa8cb?auto=format&fit=crop&w=400&q=70"
        crop.contains("Tomato", true) -> "https://images.unsplash.com/photo-1592924357228-91a4daadcfea?auto=format&fit=crop&w=400&q=70"
        crop.contains("Cotton", true) -> "https://images.unsplash.com/photo-1605000797499-95a51c5269ae?auto=format&fit=crop&w=400&q=70"
        crop.contains("Soybean", true) -> "https://images.unsplash.com/photo-1595246140625-573b715d11dc?auto=format&fit=crop&w=400&q=70"
        crop.contains("Mustard", true) -> "https://images.unsplash.com/photo-1508747703725-719777637510?auto=format&fit=crop&w=400&q=70"
        crop.contains("Maize", true) || crop.contains("Corn", true) -> "https://images.unsplash.com/photo-1551754655-cd27e38d2076?auto=format&fit=crop&w=400&q=70"
        else -> "https://images.unsplash.com/photo-1464226184884-fa280b87c399?auto=format&fit=crop&w=400&q=70"
    }
}

@Composable
fun LivePricesScreen(onBack: () -> Unit, viewModel: PricesViewModel = hiltViewModel()) {
    val prices by viewModel.prices.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    var search by remember { mutableStateOf("") }

    FeatureScaffold(
        title = stringResource(R.string.prices_title),
        subtitle = "दैनिक मंडी भाव व बाजार रुझान",
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = {
                    search = it
                    viewModel.fetch(it.ifBlank { null })
                },
                placeholder = { Text("Search by crop name (e.g. Wheat, Cotton)...") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = DeepEmerald) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = {
                            search = ""
                            viewModel.fetch(null)
                        }) {
                            Icon(Icons.Default.Clear, null, tint = TextMuted)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DeepEmerald,
                    unfocusedBorderColor = BorderMedium
                )
            )

            var selectedCropFilter by remember { mutableStateOf("All") }

            // Quick Commodity Chips
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(listOf("All", "Cotton", "Wheat", "Rice", "Onion", "Tomato", "Soybean", "Mustard")) { cropName ->
                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = if (selectedCropFilter == cropName) GreenTint else CardWhite,
                        border = BorderStroke(1.dp, if (selectedCropFilter == cropName) DeepEmerald else BorderMedium),
                        modifier = Modifier.clickable {
                            selectedCropFilter = cropName
                            viewModel.fetch(if (cropName == "All") null else cropName)
                        }
                    ) {
                        Text(
                            text = cropName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedCropFilter == cropName) DeepEmerald else TextCharcoal,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            LoadingOverlay(isLoading && prices.isEmpty())
            error?.let { err ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        color = OnErrorContainer,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (prices.isEmpty() && !isLoading) {
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
                        Text("No market rates found", fontWeight = FontWeight.Bold, color = TextCharcoal)
                        Text("No price listings match '$search'. Tap below to view all APMC rates.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        Button(
                            onClick = {
                                search = ""
                                selectedCropFilter = "All"
                                viewModel.fetch(null)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                            shape = RoundedCornerShape(9999.dp)
                        ) {
                            Text("View All Rates", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(prices, key = { "${it.crop}_${it.market}" }) { p ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        border = BorderStroke(1.dp, BorderLight),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                AsyncImage(
                                    model = getCropImageUrl(p.crop),
                                    contentDescription = p.crop,
                                    contentScale = ContentScale.Crop,
                                    placeholder = painterResource(R.drawable.ic_image_placeholder),
                                    error = painterResource(R.drawable.ic_broken_image),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = p.crop,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextCharcoal,
                                    )
                                    Text(
                                        text = "${p.market}, ${p.state}",
                                        color = TextMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "₹${"%.0f".format(p.price)}/${p.unit}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = DeepEmerald,
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    val (icon, color) = when (p.trend) {
                                        "up" -> Icons.Default.TrendingUp to DeepEmerald
                                        "down" -> Icons.Default.TrendingDown to ErrorRed
                                        else -> Icons.AutoMirrored.Filled.TrendingFlat to TextMuted
                                    }
                                    Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = if (p.trend == "up") "Rising" else if (p.trend == "down") "Falling" else "Steady",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = color,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
