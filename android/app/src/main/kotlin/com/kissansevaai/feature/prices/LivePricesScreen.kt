package com.kissansevaai.feature.prices

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var activeCrop: String? = null
    private var activeCategory: String? = null

    init { fetch() }

    fun fetch(crop: String? = null, category: String? = null, refresh: Boolean = false) {
        activeCrop = crop
        activeCategory = category
        viewModelScope.launch {
            if (refresh) _isRefreshing.value = true
            repository.getMarketPrices(
                crop = crop,
                category = if (category.isNullOrBlank() || category.equals("all", ignoreCase = true)) null else category,
                refresh = refresh
            ).collect { res ->
                when (res) {
                    is Resource.Loading -> {
                        _isLoading.value = true
                        _error.value = null
                    }
                    is Resource.Success -> {
                        _isLoading.value = false
                        _isRefreshing.value = false
                        _prices.value = res.data
                        _error.value = null
                    }
                    is Resource.Error -> {
                        _isLoading.value = false
                        _isRefreshing.value = false
                        _error.value = res.message
                    }
                }
            }
        }
    }

    fun refreshNow() {
        fetch(crop = activeCrop, category = activeCategory, refresh = true)
    }
}

fun getCropImageUrl(crop: String): String {
    val c = crop.lowercase()
    return when {
        // Crops
        c.contains("wheat") || c.contains("गेहूं") -> "https://images.unsplash.com/photo-1574943320219-553eb213f72d?auto=format&fit=crop&w=400&q=70"
        c.contains("rice") || c.contains("paddy") || c.contains("धान") || c.contains("चावल") -> "https://images.unsplash.com/photo-1586201375761-83865001e31c?auto=format&fit=crop&w=400&q=70"
        c.contains("cotton") || c.contains("कपास") -> "https://images.unsplash.com/photo-1605000797499-95a51c5269ae?auto=format&fit=crop&w=400&q=70"
        c.contains("soybean") || c.contains("सोयाबीन") -> "https://images.unsplash.com/photo-1595246140625-573b715d11dc?auto=format&fit=crop&w=400&q=70"
        c.contains("mustard") || c.contains("sarson") || c.contains("सरसों") -> "https://images.unsplash.com/photo-1508747703725-719777637510?auto=format&fit=crop&w=400&q=70"
        c.contains("maize") || c.contains("corn") || c.contains("मक्का") -> "https://images.unsplash.com/photo-1551754655-cd27e38d2076?auto=format&fit=crop&w=400&q=70"
        c.contains("gram") || c.contains("chana") || c.contains("चना") -> "https://images.unsplash.com/photo-1515543237350-b3eea1ec8082?auto=format&fit=crop&w=400&q=70"
        c.contains("sugarcane") || c.contains("गन्ना") -> "https://images.unsplash.com/photo-1590682680695-43b964a3ae17?auto=format&fit=crop&w=400&q=70"
        c.contains("groundnut") || c.contains("peanut") || c.contains("मूंगफली") -> "https://images.unsplash.com/photo-1567892328013-17b2b005e830?auto=format&fit=crop&w=400&q=70"
        c.contains("turmeric") || c.contains("हल्दी") -> "https://images.unsplash.com/photo-1615485290382-441e4d049cb5?auto=format&fit=crop&w=400&q=70"
        c.contains("bajra") || c.contains("बाजरा") -> "https://images.unsplash.com/photo-1586201375761-83865001e31c?auto=format&fit=crop&w=400&q=70"

        // Vegetables
        c.contains("onion") || c.contains("प्याज") -> "https://images.unsplash.com/photo-1618512496248-a07fe83aa8cb?auto=format&fit=crop&w=400&q=70"
        c.contains("potato") || c.contains("आलू") -> "https://images.unsplash.com/photo-1518977676601-b53f82aba655?auto=format&fit=crop&w=400&q=70"
        c.contains("tomato") || c.contains("टमाटर") -> "https://images.unsplash.com/photo-1592924357228-91a4daadcfea?auto=format&fit=crop&w=400&q=70"
        c.contains("chilli") || c.contains("मिर्च") -> "https://images.unsplash.com/photo-1563729784474-d77dbb933a9e?auto=format&fit=crop&w=400&q=70"
        c.contains("cauliflower") || c.contains("फूलगोभी") -> "https://images.unsplash.com/photo-1568584711075-3d021a7c3ca3?auto=format&fit=crop&w=400&q=70"
        c.contains("cabbage") || c.contains("पत्तागोभी") -> "https://images.unsplash.com/photo-1594282486552-05b4d80fbb9f?auto=format&fit=crop&w=400&q=70"
        c.contains("okra") || c.contains("bhindi") || c.contains("भिंडी") || c.contains("lady finger") -> "https://images.unsplash.com/photo-1425543103986-22abb7d7e8d2?auto=format&fit=crop&w=400&q=70"
        c.contains("brinjal") || c.contains("eggplant") || c.contains("बैंगन") -> "https://images.unsplash.com/photo-1615485290382-441e4d049cb5?auto=format&fit=crop&w=400&q=70"
        c.contains("carrot") || c.contains("गाजर") -> "https://images.unsplash.com/photo-1598170845058-32b9d6a5c317?auto=format&fit=crop&w=400&q=70"
        c.contains("garlic") || c.contains("लहसुन") -> "https://images.unsplash.com/photo-1615477550927-6ec84457cf18?auto=format&fit=crop&w=400&q=70"
        c.contains("ginger") || c.contains("अदरक") -> "https://images.unsplash.com/photo-1615485290382-441e4d049cb5?auto=format&fit=crop&w=400&q=70"

        // Fruits
        c.contains("apple") || c.contains("सेब") -> "https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6?auto=format&fit=crop&w=400&q=70"
        c.contains("banana") || c.contains("केला") -> "https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?auto=format&fit=crop&w=400&q=70"
        c.contains("mango") || c.contains("आम") -> "https://images.unsplash.com/photo-1553279768-865429fa0078?auto=format&fit=crop&w=400&q=70"
        c.contains("pomegranate") || c.contains("अनार") -> "https://images.unsplash.com/photo-1541344999736-83eca872f241?auto=format&fit=crop&w=400&q=70"
        c.contains("orange") || c.contains("संतरा") || c.contains("mandarin") -> "https://images.unsplash.com/photo-1611080626919-7cf5a9dbab5b?auto=format&fit=crop&w=400&q=70"
        c.contains("grapes") || c.contains("अंगूर") -> "https://images.unsplash.com/photo-1537640538966-79f369143f8f?auto=format&fit=crop&w=400&q=70"
        c.contains("papaya") || c.contains("पपीता") -> "https://images.unsplash.com/photo-1517282009859-f000ec3b26fe?auto=format&fit=crop&w=400&q=70"
        c.contains("guava") || c.contains("अमरूद") -> "https://images.unsplash.com/photo-1536511135899-808381ddae5a?auto=format&fit=crop&w=400&q=70"
        c.contains("watermelon") || c.contains("तरबूज") -> "https://images.unsplash.com/photo-1587049352846-4a222e784d38?auto=format&fit=crop&w=400&q=70"
        c.contains("lemon") || c.contains("नींबू") -> "https://images.unsplash.com/photo-1534948216015-843149f72be3?auto=format&fit=crop&w=400&q=70"

        else -> "https://images.unsplash.com/photo-1464226184884-fa280b87c399?auto=format&fit=crop&w=400&q=70"
    }
}

@Composable
fun LivePricesScreen(onBack: () -> Unit, viewModel: PricesViewModel = hiltViewModel()) {
    val prices by viewModel.prices.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val error by viewModel.error.collectAsState()
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") } // "All", "crop", "vegetable", "fruit"
    var selectedCropFilter by remember { mutableStateOf("All") }

    val infiniteTransition = rememberInfiniteTransition(label = "refreshAnim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    FeatureScaffold(
        title = stringResource(R.string.prices_title),
        subtitle = "दैनिक मंडी भाव (Live Mandi Rates)",
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category Selector Tabs (All, Crops, Vegetables, Fruits)
            Surface(
                color = CardWhite,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val categories = listOf(
                        Triple("All", "All", "🌐"),
                        Triple("crop", "Crops", "🌾"),
                        Triple("vegetable", "Vegetables", "🥬"),
                        Triple("fruit", "Fruits", "🍎")
                    )

                    categories.forEach { (key, label, emoji) ->
                        val isSelected = selectedCategory == key
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) DeepEmerald else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedCategory = key
                                    selectedCropFilter = "All"
                                    viewModel.fetch(
                                        crop = search.ifBlank { null },
                                        category = if (key == "All") null else key
                                    )
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$emoji $label",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else TextCharcoal,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Search Bar with Live Refresh button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = {
                        search = it
                        selectedCropFilter = "All"
                        viewModel.fetch(
                            crop = it.ifBlank { null },
                            category = if (selectedCategory == "All") null else selectedCategory
                        )
                    },
                    placeholder = { Text(stringResource(R.string.prices_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = DeepEmerald) },
                    trailingIcon = {
                        if (search.isNotEmpty()) {
                            IconButton(onClick = {
                                search = ""
                                viewModel.fetch(
                                    crop = null,
                                    category = if (selectedCategory == "All") null else selectedCategory
                                )
                            }) {
                                Icon(Icons.Default.Clear, null, tint = TextMuted)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DeepEmerald,
                        unfocusedBorderColor = BorderMedium
                    )
                )

                // Refresh Button with Live API integration
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GreenBg,
                    border = BorderStroke(1.dp, GreenTint),
                    modifier = Modifier.size(52.dp).clickable {
                        viewModel.refreshNow()
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Live Mandi Refresh",
                            tint = DeepEmerald,
                            modifier = Modifier
                                .size(22.dp)
                                .rotate(if (isRefreshing) rotation else 0f)
                        )
                    }
                }
            }

            // Category-Specific Quick Commodity Chips
            val quickChips = when (selectedCategory) {
                "crop" -> listOf("All", "Wheat", "Rice", "Cotton", "Soybean", "Mustard", "Maize", "Chana", "Sugarcane", "Groundnut", "Turmeric")
                "vegetable" -> listOf("All", "Onion", "Potato", "Tomato", "Chilli", "Cauliflower", "Cabbage", "Lady Finger", "Brinjal", "Carrot", "Garlic", "Ginger")
                "fruit" -> listOf("All", "Apple", "Banana", "Mango", "Pomegranate", "Orange", "Grapes", "Papaya", "Guava", "Watermelon", "Lemon")
                else -> listOf("All", "Wheat", "Rice", "Onion", "Potato", "Tomato", "Apple", "Banana", "Cotton", "Soybean")
            }

            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(quickChips) { cropName ->
                    val isChipSelected = selectedCropFilter == cropName
                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = if (isChipSelected) GreenTint else CardWhite,
                        border = BorderStroke(1.dp, if (isChipSelected) DeepEmerald else BorderMedium),
                        modifier = Modifier.clickable {
                            selectedCropFilter = cropName
                            search = if (cropName == "All") "" else cropName
                            viewModel.fetch(
                                crop = if (cropName == "All") null else cropName,
                                category = if (selectedCategory == "All") null else selectedCategory
                            )
                        }
                    ) {
                        Text(
                            text = cropName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isChipSelected) DeepEmerald else TextCharcoal,
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
                        Text(stringResource(R.string.prices_no_rates_found), fontWeight = FontWeight.Bold, color = TextCharcoal)
                        Text(stringResource(R.string.prices_no_rates_desc), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        Button(
                            onClick = {
                                search = ""
                                selectedCategory = "All"
                                selectedCropFilter = "All"
                                viewModel.fetch(null, null)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                            shape = RoundedCornerShape(9999.dp)
                        ) {
                            Text(stringResource(R.string.prices_view_all), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(prices, key = { "${it.crop}_${it.market}_${it.id}" }) { p ->
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = p.crop,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextCharcoal,
                                        )
                                        // Category Badge
                                        val (catLabel, catBg, catColor) = when (p.effectiveCategory) {
                                            "vegetable" -> Triple("🥬 Veg", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                                            "fruit" -> Triple("🍎 Fruit", Color(0xFFFBE9E7), Color(0xFFD84315))
                                            else -> Triple("🌾 Crop", GreenTint, DeepEmerald)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(9999.dp),
                                            color = catBg
                                        ) {
                                            Text(
                                                text = catLabel,
                                                color = catColor,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
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
