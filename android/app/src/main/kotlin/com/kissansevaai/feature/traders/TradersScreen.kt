package com.kissansevaai.feature.traders

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

    FeatureScaffold(title = stringResource(R.string.traders_title), onBack = onBack) { _ ->
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = {
                    search = it
                    viewModel.fetchTraders(it.ifBlank { null })
                },
                placeholder = { Text(stringResource(R.string.traders_search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
            )

            LoadingOverlay(isLoading)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(traders, key = { it.id ?: it.name }) { t ->
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        border = BorderStroke(1.dp, Slate800),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            // Trader Mandi / Storefront Photo Banner
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(115.dp)
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
                                        modifier = Modifier.fillMaxSize().background(Color.Gray),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = t.name.take(1).uppercase(),
                                            style = MaterialTheme.typography.displayMedium,
                                            color = Color.White
                                        )
                                    }
                                }
                                // Bottom fade scrim
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.2f),
                                                    Slate900.copy(alpha = 0.7f),
                                                    Slate900,
                                                )
                                            )
                                        )
                                )

                                // Verified Badge on Top Left
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Emerald600.copy(alpha = 0.85f),
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
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }

                                // Rating pill on Top Right
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Slate950.copy(alpha = 0.85f),
                                    border = BorderStroke(1.dp, Amber500.copy(alpha = 0.4f)),
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
                                            tint = Amber500,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            "%.1f".format(t.rating),
                                            color = Amber500,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        if (t.reviewCount > 0) {
                                            Text(
                                                " (${t.reviewCount})",
                                                color = Slate400,
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }
                                }
                            }

                            // Trader Details Body
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = t.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate100,
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp),
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = Emerald400,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        t.location,
                                        color = Slate400,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }

                                if (t.dealsIn.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        t.dealsIn.forEach { crop ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Emerald500.copy(alpha = 0.12f),
                                                border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.25f)),
                                            ) {
                                                Text(
                                                    text = crop,
                                                    color = Emerald400,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                // Contact & Negotiate Actions Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Call button
                                    Button(
                                        onClick = {
                                            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                                data = Uri.parse("tel:${t.phone.replace(" ", "")}")
                                            }
                                            context.startActivity(dialIntent)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            Icons.Default.Phone,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            t.phone,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }

                                    // Negotiate button
                                    OutlinedButton(
                                        onClick = {
                                            // Open SMS or dialer for negotiation
                                            val smsIntent = Intent(Intent.ACTION_VIEW).apply {
                                                data = Uri.parse("sms:${t.phone.replace(" ", "")}")
                                            }
                                            context.startActivity(smsIntent)
                                        },
                                        border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.4f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Emerald400),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Message,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Negotiate", fontSize = 12.sp)
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
