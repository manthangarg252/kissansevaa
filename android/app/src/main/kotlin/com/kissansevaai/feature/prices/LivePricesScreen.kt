package com.kissansevaai.feature.prices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
        crop.contains("Soybean", true) -> "https://images.unsplash.com/photo-1599818816934-2e917d0a27e7?auto=format&fit=crop&w=400&q=70"
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

    FeatureScaffold(title = stringResource(R.string.prices_title), onBack = onBack) { _ ->
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                search, { search = it; viewModel.fetch(it.ifBlank { null }) },
                placeholder = { Text(stringResource(R.string.common_search)) },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
            )
            LoadingOverlay(isLoading)
            error?.let { err ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(prices, key = { "${it.crop}_${it.market}" }) { p ->
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Slate900)) {
                        Row(
                            Modifier
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
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        p.crop,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate100,
                                    )
                                    Text(
                                        "${p.market}, ${p.state}",
                                        color = Slate400,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "₹${"%.0f".format(p.price)}/${p.unit}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Emerald400,
                                )
                                Spacer(Modifier.width(4.dp))
                                val (icon, color) = when (p.trend) {
                                    "up" -> Icons.Default.TrendingUp to Emerald400
                                    "down" -> Icons.Default.TrendingDown to Red400
                                    else -> Icons.Default.TrendingFlat to Slate400
                                }
                                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
