package com.kissansevaai.feature.traders

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.model.Trader
import com.kissansevaai.core.repository.TradersRepository
import com.kissansevaai.ui.component.FeatureScaffold
import com.kissansevaai.ui.component.LoadingOverlay
import com.kissansevaai.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TradersViewModel @Inject constructor(
    private val repository: TradersRepository,
) : ViewModel() {
    private val _traders = MutableStateFlow<List<Trader>>(emptyList())
    val traders: StateFlow<List<Trader>> = _traders
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init { fetchTraders() }

    fun fetchTraders(search: String? = null) {
        viewModelScope.launch {
            repository.getTraders(crop = search).collect { res ->
                when (res) {
                    is Resource.Loading -> {
                        _isLoading.value = true
                        _error.value = null
                    }
                    is Resource.Success -> {
                        _isLoading.value = false
                        _traders.value = res.data
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

fun getTraderImageUrl(trader: Trader): String? {
    if (!trader.imageUrl.isNullOrBlank()) return trader.imageUrl
    return null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TradersScreen(onBack: () -> Unit, viewModel: TradersViewModel = hiltViewModel()) {
    val traders by viewModel.traders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    var search by remember { mutableStateOf("") }
    val context = LocalContext.current

    FeatureScaffold(
        title = stringResource(R.string.traders_title),
        subtitle = "सीधे बड़े व सत्यापित व्यापारियों से संपर्क",
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Search field
            OutlinedTextField(
                value = search,
                onValueChange = {
                    search = it
                    viewModel.fetchTraders(it.ifBlank { null })
                },
                placeholder = { Text(stringResource(R.string.traders_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = DeepEmerald) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = {
                            search = ""
                            viewModel.fetchTraders(null)
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

            var selectedCommodityFilter by remember { mutableStateOf("All") }

            // Quick Commodity Chips
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(listOf("All", "Wheat", "Cotton", "Rice", "Onion", "Tomato", "Soybean")) { cropName ->
                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = if (selectedCommodityFilter == cropName) GreenTint else CardWhite,
                        border = BorderStroke(1.dp, if (selectedCommodityFilter == cropName) DeepEmerald else BorderMedium),
                        modifier = Modifier.clickable {
                            selectedCommodityFilter = cropName
                            viewModel.fetchTraders(if (cropName == "All") null else cropName)
                        }
                    ) {
                        Text(
                            text = cropName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedCommodityFilter == cropName) DeepEmerald else TextCharcoal,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            LoadingOverlay(isLoading && traders.isEmpty())
            error?.let {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(it, color = OnErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }

            if (traders.isEmpty() && !isLoading) {
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
                        Text(stringResource(R.string.traders_no_traders_found), fontWeight = FontWeight.Bold, color = TextCharcoal)
                        Text(stringResource(R.string.traders_no_traders_desc), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        Button(
                            onClick = {
                                search = ""
                                selectedCommodityFilter = "All"
                                viewModel.fetchTraders(null)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                            shape = RoundedCornerShape(9999.dp)
                        ) {
                            Text(stringResource(R.string.traders_view_all), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(traders, key = { it.id ?: it.name }) { t ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        border = BorderStroke(1.dp, BorderLight),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            // Trader Storefront / Market Image Header
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            ) {
                                val imageUrl = getTraderImageUrl(t)
                                if (imageUrl != null) {
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = t.name,
                                        contentScale = ContentScale.Crop,
                                        placeholder = painterResource(R.drawable.ic_image_placeholder),
                                        error = painterResource(R.drawable.ic_broken_image),
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(SurfaceContainerHigh),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = t.name.take(1).uppercase(),
                                            style = MaterialTheme.typography.displayMedium,
                                            color = DeepEmerald
                                        )
                                    }
                                }

                                // Verified Badge on Top Left
                                Surface(
                                    shape = RoundedCornerShape(9999.dp),
                                    color = DeepEmerald,
                                    shadowElevation = 2.dp,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .align(Alignment.TopStart),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "VERIFIED BUYER",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }

                                // Rating Badge on Top Right
                                if (t.rating != null) {
                                    Surface(
                                        shape = RoundedCornerShape(9999.dp),
                                        color = CardWhite,
                                        shadowElevation = 2.dp,
                                        modifier = Modifier
                                            .padding(10.dp)
                                            .align(Alignment.TopEnd),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Star,
                                                contentDescription = null,
                                                tint = HarvestAmber,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(3.dp))
                                            Text(
                                                text = "%.1f".format(t.rating),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = TextCharcoal
                                            )
                                        }
                                    }
                                }
                            }

                            // Card Content
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = t.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextCharcoal
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = DeepEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = t.location,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                    )
                                }

                                // Deals in: Commodity Tags
                                if (t.dealsIn.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Deals in:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = TextMuted
                                        )
                                        t.dealsIn.forEach { crop ->
                                            Surface(
                                                shape = RoundedCornerShape(9999.dp),
                                                color = GreenTint,
                                                border = BorderStroke(1.dp, DeepEmerald.copy(alpha = 0.2f))
                                            ) {
                                                Text(
                                                    text = crop,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DeepEmerald,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = BorderLight)

                                // Action Buttons: Call & Chat
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${t.phone}"))
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        shape = RoundedCornerShape(9999.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald)
                                    ) {
                                        Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.traders_call_btn), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${t.phone}"))
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        shape = RoundedCornerShape(9999.dp),
                                        border = BorderStroke(1.5.dp, DeepEmerald)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Message, null, tint = DeepEmerald, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.traders_action_message), color = DeepEmerald, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
