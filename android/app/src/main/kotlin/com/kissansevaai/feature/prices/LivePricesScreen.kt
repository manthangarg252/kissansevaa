package com.kissansevaai.feature.prices

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.*
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
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.model.MarketComparisonDto
import com.kissansevaai.core.model.MarketPrice
import com.kissansevaai.core.model.MarketStatsDto
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
    private val _stats = MutableStateFlow<MarketStatsDto?>(null)
    val stats: StateFlow<MarketStatsDto?> = _stats
    private val _comparisons = MutableStateFlow<List<MarketComparisonDto>>(emptyList())
    val comparisons: StateFlow<List<MarketComparisonDto>> = _comparisons
    private val _favoriteKeys = MutableStateFlow<Set<String>>(emptySet())
    val favoriteKeys: StateFlow<Set<String>> = _favoriteKeys

    private var activeCrop: String? = null
    private var activeCategory: String? = null

    init {
        fetch()
        loadMeta()
    }

    private fun loadMeta() {
        viewModelScope.launch {
            repository.getMarketStats().collect { res ->
                if (res is Resource.Success) _stats.value = res.data
            }
        }
        viewModelScope.launch {
            repository.compareMarkets(activeCrop, activeCategory).collect { res ->
                if (res is Resource.Success) _comparisons.value = res.data.comparisons
            }
        }
        viewModelScope.launch {
            repository.getFavorites().collect { _favoriteKeys.value = it }
        }
    }

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
            if (refresh) loadMeta()
        }
    }

    fun refreshNow() {
        fetch(crop = activeCrop, category = activeCategory, refresh = true)
    }

    fun toggleFavorite(item: MarketPrice) {
        val key = "${item.crop.lowercase()}|${item.market.lowercase()}"
        val currentlyFav = _favoriteKeys.value.contains(key)
        _favoriteKeys.value = if (currentlyFav) _favoriteKeys.value - key else _favoriteKeys.value + key
        viewModelScope.launch {
            repository.toggleFavorite(item, currentlyFav)
        }
    }
}

fun getCropImageUrl(crop: String): String {
    val c = crop.lowercase()
    return when {
        c.contains("wheat") || c.contains("गेहूं") -> "https://images.unsplash.com/photo-1574943320219-553eb213f72d?auto=format&fit=crop&w=400&q=70"
        c.contains("rice") || c.contains("paddy") || c.contains("धान") || c.contains("चावल") -> "https://images.unsplash.com/photo-1586201375761-83865001e31c?auto=format&fit=crop&w=400&q=70"
        c.contains("cotton") || c.contains("कपास") -> "https://images.unsplash.com/photo-1605000797499-95a51c5269ae?auto=format&fit=crop&w=400&q=70"
        c.contains("onion") || c.contains("प्याज") -> "https://images.unsplash.com/photo-1618512496248-a07fe83aa8cb?auto=format&fit=crop&w=400&q=70"
        c.contains("potato") || c.contains("आलू") -> "https://images.unsplash.com/photo-1518977676601-b53f82aba655?auto=format&fit=crop&w=400&q=70"
        c.contains("tomato") || c.contains("टमाटर") -> "https://images.unsplash.com/photo-1592924357228-91a4daadcfea?auto=format&fit=crop&w=400&q=70"
        c.contains("apple") || c.contains("सेब") -> "https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6?auto=format&fit=crop&w=400&q=70"
        c.contains("banana") || c.contains("केला") -> "https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?auto=format&fit=crop&w=400&q=70"
        c.contains("mango") || c.contains("आम") -> "https://images.unsplash.com/photo-1553279768-865429fa0078?auto=format&fit=crop&w=400&q=70"
        else -> "https://images.unsplash.com/photo-1464226184884-fa280b87c399?auto=format&fit=crop&w=400&q=70"
    }
}

@Composable
fun LivePricesScreen(onBack: () -> Unit, viewModel: PricesViewModel = hiltViewModel()) {
    val prices by viewModel.prices.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val error by viewModel.error.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val comparisons by viewModel.comparisons.collectAsState()
    val favoriteKeys by viewModel.favoriteKeys.collectAsState()

    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedCropFilter by remember { mutableStateOf("All") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var showCompareMode by remember { mutableStateOf(false) }

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
        subtitle = "दैनिक मंडी भाव (APMC Mandi Rates)",
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Honest Source & Freshness Status Banner
            stats?.let { st ->
                val isLive = st.sourceStatus.contains("live")
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isLive) GreenBg else AmberTint,
                    border = BorderStroke(1.dp, if (isLive) GreenTint else SecondaryOrange),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = "Source: ${st.sourceStatus.replace('_', ' ')} • ${st.total} listings",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isLive) DeepEmerald else SecondaryOrange,
                        )
                        st.lastFetchError?.takeIf { it.isNotBlank() }?.let { fetchErr ->
                            Text(fetchErr, style = MaterialTheme.typography.labelSmall, color = TextCharcoal, fontSize = 10.sp)
                        }
                    }
                }
            }

            // Category Selector Tabs
            Surface(
                color = CardWhite,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val categories = listOf(
                        Triple("All", "All", "🌐"),
                        Triple("crop", "Crops", "🌾"),
                        Triple("vegetable", "Veg", "🥬"),
                        Triple("fruit", "Fruit", "🍎")
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
                                    search = ""
                                    viewModel.fetch(
                                        crop = null,
                                        category = if (key == "All") null else key
                                    )
                                }
                        ) {
                            Text(
                                text = "$emoji $label",
                                color = if (isSelected) Color.White else TextCharcoal,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Search + Refresh + Compare / Favorites toggles
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
                    placeholder = { Text(stringResource(R.string.prices_search_placeholder), fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = DeepEmerald) },
                    trailingIcon = {
                        if (search.isNotEmpty()) {
                            IconButton(onClick = {
                                search = ""
                                viewModel.fetch(null, if (selectedCategory == "All") null else selectedCategory)
                            }) {
                                Icon(Icons.Default.Clear, null, tint = TextMuted)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DeepEmerald, unfocusedBorderColor = BorderMedium)
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GreenBg,
                    border = BorderStroke(1.dp, GreenTint),
                    modifier = Modifier.size(52.dp).clickable { viewModel.refreshNow() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Live Mandi Refresh",
                            tint = DeepEmerald,
                            modifier = Modifier.size(22.dp).rotate(if (isRefreshing) rotation else 0f)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { favoritesOnly = !favoritesOnly; showCompareMode = false },
                    leadingIcon = { Icon(Icons.Default.Star, null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Saved (${favoriteKeys.size})") },
                )
                FilterChip(
                    selected = showCompareMode,
                    onClick = { showCompareMode = !showCompareMode },
                    leadingIcon = { Icon(Icons.Default.CompareArrows, null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Compare Mandis") },
                )
            }

            LoadingOverlay(isLoading && prices.isEmpty())

            error?.let { err ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = err, color = OnErrorContainer, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (showCompareMode) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(comparisons) { cmp ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = CardWhite),
                            border = BorderStroke(1.dp, BorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        cmp.crop,
                                        fontWeight = FontWeight.Bold,
                                        color = TextCharcoal,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Surface(shape = RoundedCornerShape(9999.dp), color = GreenTint) {
                                        Text(
                                            "Spread ₹${cmp.spreadPerQuintal.toInt()}/q",
                                            color = DeepEmerald,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    "Highest: ₹${cmp.highestMandi.price.toInt()}/q @ ${cmp.highestMandi.market}, ${cmp.highestMandi.state}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DeepEmerald
                                )
                                Text(
                                    "Lowest: ₹${cmp.lowestMandi.price.toInt()}/q @ ${cmp.lowestMandi.market}, ${cmp.lowestMandi.state}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            } else {
                val displayPrices = remember(prices, favoritesOnly, favoriteKeys) {
                    if (!favoritesOnly) prices
                    else prices.filter { favoriteKeys.contains("${it.crop.lowercase()}|${it.market.lowercase()}") }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(displayPrices, key = { "${it.crop}_${it.market}_${it.id}" }) { p ->
                        val favKey = "${p.crop.lowercase()}|${p.market.lowercase()}"
                        val isFav = favoriteKeys.contains(favKey)
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CardWhite),
                            border = BorderStroke(1.dp, if (isFav) DeepEmerald else BorderLight),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    AsyncImage(
                                        model = getCropImageUrl(p.crop),
                                        contentDescription = p.crop,
                                        contentScale = ContentScale.Crop,
                                        placeholder = painterResource(R.drawable.ic_image_placeholder),
                                        error = painterResource(R.drawable.ic_broken_image),
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "₹${"%.0f".format(p.effectivePrice)}/${p.unit}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = DeepEmerald,
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
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
                                    IconButton(onClick = { viewModel.toggleFavorite(p) }) {
                                        Icon(
                                            imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "Favorite Mandi",
                                            tint = if (isFav) HarvestAmber else TextMuted,
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
}
