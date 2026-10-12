package com.kissansevaai.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kissansevaai.R
import com.kissansevaai.ui.navigation.Screen
import com.kissansevaai.ui.theme.*

private data class DashCard(
    val titleRes: Int,
    val icon: ImageVector,
    val color: Color,
    val screen: Screen,
    val imageUrl: String,
)

private val cards = listOf(
    DashCard(
        titleRes = R.string.dashboard_cards_crop_detect,
        icon = Icons.Default.Grass,
        color = Emerald500,
        screen = Screen.CropDisease,
        imageUrl = "https://images.unsplash.com/photo-1523348837708-15d4a09cfac2?auto=format&fit=crop&w=400&q=70",
    ),
    DashCard(
        titleRes = R.string.dashboard_cards_livestock,
        icon = Icons.Default.Pets,
        color = Blue500,
        screen = Screen.Livestock,
        imageUrl = "https://images.unsplash.com/photo-1500595046743-cd271d694d30?auto=format&fit=crop&w=400&q=70",
    ),
    DashCard(
        titleRes = R.string.dashboard_cards_market,
        icon = Icons.Default.TrendingUp,
        color = Purple500,
        screen = Screen.MarketInsights,
        imageUrl = "https://images.unsplash.com/photo-1464226184884-fa280b87c399?auto=format&fit=crop&w=400&q=70",
    ),
    DashCard(
        titleRes = R.string.dashboard_cards_iot,
        icon = Icons.Default.Sensors,
        color = Orange500,
        screen = Screen.IoTMonitoring,
        imageUrl = "https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=400&q=70",
    ),
    DashCard(
        titleRes = R.string.dashboard_cards_schemes,
        icon = Icons.Default.AccountBalance,
        color = Indigo500,
        screen = Screen.Schemes,
        imageUrl = "https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?auto=format&fit=crop&w=400&q=70",
    ),
    DashCard(
        titleRes = R.string.dashboard_cards_loans,
        icon = Icons.Default.CreditCard,
        color = Amber500,
        screen = Screen.Loans,
        imageUrl = "https://images.unsplash.com/photo-1559526324-4b87b5e36e44?auto=format&fit=crop&w=400&q=70",
    ),
    DashCard(
        titleRes = R.string.dashboard_cards_traders,
        icon = Icons.Default.Store,
        color = Emerald400,
        screen = Screen.Traders,
        imageUrl = "https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&w=400&q=70",
    ),
    DashCard(
        titleRes = R.string.dashboard_cards_carbon,
        icon = Icons.Default.Eco,
        color = Emerald600,
        screen = Screen.Carbon,
        imageUrl = "https://images.unsplash.com/photo-1511497584788-876760111969?auto=format&fit=crop&w=400&q=70",
    ),
    DashCard(
        titleRes = R.string.dashboard_cards_prices,
        icon = Icons.Default.BarChart,
        color = Blue600,
        screen = Screen.LivePrices,
        imageUrl = "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?auto=format&fit=crop&w=400&q=70",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onNavigate: (Screen) -> Unit, onLogout: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { onNavigate(Screen.Settings) }) { Icon(Icons.Default.Settings, "Settings") }
                    IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, "Logout", tint = Red400) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate900),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigate(Screen.Chatbot) },
                containerColor = Emerald600,
                contentColor = Color.White,
            ) { Icon(Icons.Default.Chat, "AI Chatbot") }
        },
        containerColor = Slate950,
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.dashboard_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Slate400,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                items(cards) { card ->
                    Card(
                        onClick = { onNavigate(card.screen) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        modifier = Modifier
                            .height(156.dp)
                            .fillMaxWidth(),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Rich photo image background
                            AsyncImage(
                                model = card.imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                placeholder = painterResource(R.drawable.ic_image_placeholder),
                                error = painterResource(R.drawable.ic_broken_image),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(0.55f),
                            )

                            // Gradient overlay for contrast and sleek dark aesthetics
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.25f),
                                                Slate950.copy(alpha = 0.70f),
                                                Slate950.copy(alpha = 0.95f),
                                            )
                                        )
                                    )
                            )

                            // Foreground icon & label
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = card.color.copy(alpha = 0.25f),
                                    border = BorderStroke(1.dp, card.color.copy(alpha = 0.45f)),
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(card.icon, null, tint = card.color, modifier = Modifier.size(22.dp))
                                    }
                                }
                                Text(
                                    stringResource(card.titleRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Start,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
