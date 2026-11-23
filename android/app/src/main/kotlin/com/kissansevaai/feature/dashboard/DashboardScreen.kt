package com.kissansevaai.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kissansevaai.ui.navigation.Screen
import com.kissansevaai.ui.theme.*

private data class ToolkitModule(
    val title: String,
    val hindiSubtitle: String,
    val tag: String,
    val tagBg: Color,
    val tagColor: Color,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val screen: Screen,
)

private val modules = listOf(
    ToolkitModule(
        title = "Crop Disease Detection",
        hindiSubtitle = "फसल रोग पहचान व निदान",
        tag = "AI Scan",
        tagBg = GreenTint,
        tagColor = DeepEmerald,
        icon = Icons.Default.CenterFocusStrong,
        iconBg = GreenTintLight,
        iconTint = DeepEmerald,
        screen = Screen.CropDisease,
    ),
    ToolkitModule(
        title = "Live Mandi Prices",
        hindiSubtitle = "दैनिक मंडी भाव व रुझान",
        tag = "Live ↑",
        tagBg = AmberTint,
        tagColor = HarvestAmber,
        icon = Icons.Default.TrendingUp,
        iconBg = AmberBg,
        iconTint = HarvestAmber,
        screen = Screen.LivePrices,
    ),
    ToolkitModule(
        title = "Government Schemes",
        hindiSubtitle = "सरकारी अनुदान एवं योजनाएं",
        tag = "PM-KISAN",
        tagBg = SurfaceContainerHigh,
        tagColor = DeepEmerald,
        icon = Icons.Default.AccountBalance,
        iconBg = GreenTintLight,
        iconTint = DeepEmerald,
        screen = Screen.Schemes,
    ),
    ToolkitModule(
        title = "Loans & Credit Hub",
        hindiSubtitle = "किसान क्रेडिट कार्ड एवं ऋण",
        tag = "KCC 4%",
        tagBg = SecondaryFixed,
        tagColor = SecondaryOrange,
        icon = Icons.Default.CreditCard,
        iconBg = AmberBg,
        iconTint = SecondaryOrange,
        screen = Screen.Loans,
    ),
    ToolkitModule(
        title = "Traders & Buyers",
        hindiSubtitle = "सीधे बड़े व्यापारियों से संपर्क",
        tag = "Verified",
        tagBg = GreenTint,
        tagColor = DeepEmerald,
        icon = Icons.Default.Handshake,
        iconBg = SurfaceContainerLow,
        iconTint = TextCharcoal,
        screen = Screen.Traders,
    ),
    ToolkitModule(
        title = "Livestock Care",
        hindiSubtitle = "पशुपालन एवं स्वास्थ्य",
        tag = "Care & Vet",
        tagBg = AmberTint,
        tagColor = HarvestAmber,
        icon = Icons.Default.Pets,
        iconBg = AmberBg,
        iconTint = HarvestAmber,
        screen = Screen.Livestock,
    ),
    ToolkitModule(
        title = "Carbon Credits",
        hindiSubtitle = "कार्बन क्रेडिट कमाई",
        tag = "Green ₹",
        tagBg = GreenTint,
        tagColor = DeepEmerald,
        icon = Icons.Default.Eco,
        iconBg = GreenTintLight,
        iconTint = DeepEmerald,
        screen = Screen.Carbon,
    ),
    ToolkitModule(
        title = "Market Insights",
        hindiSubtitle = "बाजार विश्लेषण व रुझान",
        tag = "Forecast",
        tagBg = PrimaryFixed.copy(alpha = 0.4f),
        tagColor = DeepEmerald,
        icon = Icons.Default.Insights,
        iconBg = SurfaceContainerHigh,
        iconTint = DeepEmerald,
        screen = Screen.MarketInsights,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigate: (Screen) -> Unit,
    onLogout: () -> Unit,
    viewModel: DashboardViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val lang by viewModel.language.collectAsState()
    val sensorData by viewModel.sensorData.collectAsState()
    val mandiPrices by viewModel.mandiPrices.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showLangMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = GreenTint,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Eco,
                                    contentDescription = null,
                                    tint = DeepEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = "KissanSevaAI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = DeepEmerald,
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    // Language Switcher Chip with Dropdown
                    Box {
                        Surface(
                            shape = RoundedCornerShape(9999.dp),
                            border = BorderStroke(1.dp, DeepEmerald),
                            color = CardWhite,
                            modifier = Modifier
                                .clickable {
                                    showLangMenu = true
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = DeepEmerald,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = when (lang.lowercase().trim()) {
                                        "hi" -> "हिन्दी"
                                        "bho" -> "भोजपुरी"
                                        "mr" -> "मराठी"
                                        "pa" -> "ਪੰਜਾਬੀ"
                                        else -> "English"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = DeepEmerald,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showLangMenu,
                            onDismissRequest = { showLangMenu = false }
                        ) {
                            listOf(
                                "en" to "English",
                                "hi" to "हिन्दी (Hindi)",
                                "bho" to "भोजपुरी (Bhojpuri)",
                                "mr" to "मराठी (Marathi)",
                                "pa" to "ਪੰਜਾਬੀ (Punjabi)"
                            ).forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            fontWeight = if (lang == code) FontWeight.Bold else FontWeight.Normal,
                                            color = if (lang == code) DeepEmerald else TextCharcoal
                                        )
                                    },
                                    onClick = {
                                        viewModel.setLanguage(code)
                                        showLangMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Voice / Mic Quick Action
                    IconButton(onClick = { onNavigate(Screen.Chatbot) }) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Assistant",
                            tint = DeepEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Overflow Menu
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Settings (सेटिंग्स)") },
                                onClick = {
                                    showMenu = false
                                    onNavigate(Screen.Settings)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = DeepEmerald)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Logout (लॉगआउट)", color = ErrorRed) },
                                onClick = {
                                    showMenu = false
                                    onLogout()
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = ErrorRed)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CardWhite,
                    titleContentColor = TextCharcoal
                ),
            )
        },
        floatingActionButton = {
            // Floating Kissan Bot Action
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(9999.dp),
                    color = TextCharcoal,
                    shadowElevation = 6.dp,
                    modifier = Modifier.clickable { onNavigate(Screen.Chatbot) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(PrimaryFixed)
                        )
                        Text(
                            text = "किसान AI • Kissan Bot",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                FloatingActionButton(
                    onClick = { onNavigate(Screen.Chatbot) },
                    containerColor = DeepEmerald,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "Chat with Kissan AI",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        containerColor = SurfaceBg,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Farmer Greeting & Live Network Banner
            item {
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
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(DeepEmerald)
                                )
                                Text(
                                    text = "Live Agro Network",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TertiaryGreen
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "नमस्ते, किसान भाई!",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextCharcoal
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = DeepEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Rajkot, Gujarat • Plot 1 (Groundnut & Cotton)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }

                        // Farmer Avatar Image
                        Surface(
                            shape = CircleShape,
                            border = BorderStroke(2.dp, PrimaryFixed),
                            modifier = Modifier.size(52.dp)
                        ) {
                            AsyncImage(
                                model = "https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&q=80&w=200",
                                contentDescription = "Farmer Profile",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // 2. Weather & Spraying Advisory Banner (Bento Hero)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = BorderStroke(1.dp, BorderLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = AmberTint,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.WbSunny,
                                            contentDescription = null,
                                            tint = HarvestAmber,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "31°C Sunny • मौसम",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextCharcoal
                                    )
                                    Text(
                                        text = "Wind 8 km/h • Humidity 64% • Rain 10%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(9999.dp),
                                color = SecondaryFixed,
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Text(
                                    text = "Clear Sky",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SecondaryOrange,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Actionable Spraying Alert
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GreenBg,
                            border = BorderStroke(1.dp, GreenTint),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val advisoryHeading = when (lang.lowercase().trim()) {
                                "hi" -> "छिड़काव सलाह • AI Advisory"
                                "bho" -> "छिड़काव सलाह • AI सलाह"
                                "mr" -> "फवारणी सल्ला • AI Advisory"
                                "pa" -> "ਛਿੜਕਾਅ ਸਲਾਹ • AI Advisory"
                                else -> "Spraying Advisory • AI Alert"
                            }
                            val advisoryBody = when (lang.lowercase().trim()) {
                                "hi" -> "अगले 48 घंटे में मौसम साफ रहेगा। कीटनाशक और पोषक तत्वों के छिड़काव के लिए यह उपयुक्त समय है। हवा की गति कम है।"
                                "bho" -> "अगिला 48 घंटा में मौसम साफ रही। कीटनाशक अउर खाद छिड़के खातिर ई बहुत बढ़िया समय बा। हवा के गति धीमा बा।"
                                "mr" -> "पुढील 48 तास हवामान कोरडे राहील. कीटकनाशक आणि खत फवारणीसाठी ही उत्तम वेळ आहे. वाऱ्याचा वेग मंद आहे."
                                "pa" -> "ਅਗਲੇ 48 ਘੰਟਿਆਂ ਵਿੱਚ ਮੌਸਮ ਸਾਫ਼ ਰਹੇਗਾ। ਕੀਟਨਾਸ਼ਕ ਅਤੇ ਖਾਦ ਦੇ ਛਿੜਕਾਅ ਲਈ ਇਹ ਢੁਕਵਾਂ ਸਮਾਂ ਹੈ। ਹਵਾ ਦੀ ਗਤੀ ਘੱਟ ਹੈ।"
                                else -> "Next 48 hrs provide an ideal dry window for foliar pesticide & micronutrient spray. Low wind drift."
                            }
                            val speakText = when (lang.lowercase().trim()) {
                                "hi" -> "छिड़काव सलाह: अगले 48 घंटे में मौसम साफ रहेगा। कीटनाशक और पोषक तत्वों के छिड़काव के लिए यह उपयुक्त समय है। हवा की गति कम है।"
                                "bho" -> "छिड़काव सलाह: अगिला 48 घंटा में मौसम साफ रही। कीटनाशक अउर खाद छिड़के खातिर ई बहुत बढ़िया समय बा। हवा के गति धीमा बा।"
                                "mr" -> "फवारणी सल्ला: पुढील 48 तास हवामान कोरडे राहील. कीटकनाशक आणि खत फवारणीसाठी ही उत्तम वेळ आहे."
                                "pa" -> "ਛਿੜਕਾਅ ਸਲਾਹ: ਅਗਲੇ 48 ਘੰਟਿਆਂ ਵਿੱਚ ਮੌਸਮ ਸਾਫ਼ ਰਹੇਗਾ। ਕੀਟਨਾਸ਼ਕ ਅਤੇ ਖਾਦ ਦੇ ਛਿੜਕਾਅ ਲਈ ਇਹ ਢੁਕਵਾਂ ਸਮਾਂ ਹੈ।"
                                else -> "Spraying advisory: Next 48 hours provide an ideal dry window for foliar pesticide and micronutrient spray with low wind drift."
                            }
                            val speakBtnText = when (lang.lowercase().trim()) {
                                "hi" -> if (isSpeaking) "सुनें..." else "बोलें"
                                "bho" -> if (isSpeaking) "सुनल जाव..." else "बोलीं"
                                "mr" -> if (isSpeaking) "ऐका..." else "बोला"
                                "pa" -> if (isSpeaking) "ਸੁਣੋ..." else "ਬੋਲੋ"
                                else -> if (isSpeaking) "Playing..." else "Listen"
                            }

                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PestControl,
                                            contentDescription = null,
                                            tint = DeepEmerald,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = advisoryHeading,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = DeepEmerald
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = advisoryBody,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextCharcoal
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(9999.dp),
                                    color = if (isSpeaking) GreenTint else CardWhite,
                                    border = BorderStroke(1.dp, if (isSpeaking) DeepEmerald else BorderMedium),
                                    modifier = Modifier.clickable {
                                        viewModel.speakWeatherAdvisory(speakText)
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isSpeaking) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                            contentDescription = null,
                                            tint = DeepEmerald,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = speakBtnText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = DeepEmerald
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. IoT Farm Telemetry Live Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = DeepEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "IoT Farm Telemetry • लाइव सेंसर",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextCharcoal
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(SproutMint)
                            )
                            Text(
                                text = "All 4 Online",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TertiaryGreen
                            )
                        }
                    }

                    // 2x2 Telemetry Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Sensor 1: Soil Moisture
                        TelemetryCard(
                            label = "Soil Moisture",
                            value = "${sensorData.soilMoisture ?: 45.0}%",
                            status = "Optimal (संतुलित)",
                            statusColor = DeepEmerald,
                            icon = Icons.Default.WaterDrop,
                            iconTint = SproutMint,
                            iconBg = GreenTintLight,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigate(Screen.IoTMonitoring) }
                        )
                        // Sensor 2: Temperature
                        TelemetryCard(
                            label = "Temperature",
                            value = "${sensorData.temperature ?: 27.8}°C",
                            status = "Normal for Cotton",
                            statusColor = TextMuted,
                            icon = Icons.Default.DeviceThermostat,
                            iconTint = HarvestAmber,
                            iconBg = AmberTint,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigate(Screen.IoTMonitoring) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Sensor 3: Humidity
                        TelemetryCard(
                            label = "Humidity",
                            value = "${sensorData.humidity ?: 64.0}%",
                            status = "Plot 1 Microclimate",
                            statusColor = TextMuted,
                            icon = Icons.Default.Air,
                            iconTint = Blue500,
                            iconBg = SurfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigate(Screen.IoTMonitoring) }
                        )
                        // Sensor 4: pH Level
                        TelemetryCard(
                            label = "pH Level",
                            value = "${sensorData.phLevel ?: 6.7} pH",
                            status = "Good Nutrient Intake",
                            statusColor = DeepEmerald,
                            icon = Icons.Default.Science,
                            iconTint = TertiaryGreen,
                            iconBg = GreenTintLight,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigate(Screen.IoTMonitoring) }
                        )
                    }
                }
            }

            // 4. Smart Farming Toolkit Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Smart Farming Toolkit • उपकरण",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextCharcoal
                    )
                    Text(
                        text = "8 Modules",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = DeepEmerald
                    )
                }
            }

            // 5. Smart Farming Toolkit Modules (4 pairs of 2)
            items(modules.chunked(2).size) { rowIndex ->
                val pair = modules.chunked(2)[rowIndex]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolkitCard(
                        module = pair[0],
                        onClick = { onNavigate(pair[0].screen) },
                        modifier = Modifier.weight(1f)
                    )
                    if (pair.size > 1) {
                        ToolkitCard(
                            module = pair[1],
                            onClick = { onNavigate(pair[1].screen) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // 6. Today's Mandi Rates Snapshot
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = BorderStroke(1.dp, BorderLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Today's Mandi Rates • आज के भाव",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextCharcoal
                                )
                                Text(
                                    text = "Live updates from nearby APMC markets",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                            TextButton(onClick = { onNavigate(Screen.LivePrices) }) {
                                Text(
                                    text = "View All →",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = DeepEmerald
                                )
                            }
                        }

                        if (mandiPrices.isNotEmpty()) {
                            mandiPrices.forEach { p ->
                                MandiPriceRow(
                                    cropName = p.crop,
                                    mandiLocation = "${p.market}, ${p.state}",
                                    price = "₹${p.effectivePrice.toLong()}/q",
                                    trend = if (p.trend == "up") "Rising" else if (p.trend == "down") "Falling" else "Steady",
                                    isUp = if (p.trend == "up") true else if (p.trend == "down") false else null
                                )
                            }
                        } else {
                            // Fallback default snapshot rows
                            MandiPriceRow(
                                cropName = "Cotton (कपास)",
                                mandiLocation = "Rajkot, Gujarat",
                                price = "₹7,100/q",
                                trend = "+₹120",
                                isUp = true
                            )
                            MandiPriceRow(
                                cropName = "Wheat (गेहूं)",
                                mandiLocation = "Gondal APMC",
                                price = "₹2,450/q",
                                trend = "+₹40",
                                isUp = true
                            )
                            MandiPriceRow(
                                cropName = "Groundnut (मूंगफली)",
                                mandiLocation = "Rajkot Mandi",
                                price = "₹6,300/q",
                                trend = "Steady",
                                isUp = null
                            )
                        }
                    }
                }
            }

            // 7. Voice-First CTA Bar
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DeepEmerald),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(Screen.Chatbot) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(9999.dp),
                                color = Color.White.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "Voice Agro-AI",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "बोलकर सवाल पूछें",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Ask in Hindi, Gujarati, or English",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnPrimaryContainer
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = CardWhite,
                            shadowElevation = 6.dp,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice Search",
                                    tint = DeepEmerald,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Spacing for FAB
            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun TelemetryCard(
    label: String,
    value: String,
    status: String,
    statusColor: Color,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
                Surface(
                    shape = CircleShape,
                    color = iconBg,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = TextCharcoal
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = statusColor
            )
        }
    }
}

@Composable
private fun ToolkitCard(
    module: ToolkitModule,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.height(138.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = module.iconBg,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(module.icon, contentDescription = null, tint = module.iconTint, modifier = Modifier.size(22.dp))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(9999.dp),
                    color = module.tagBg
                ) {
                    Text(
                        text = module.tag,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = module.tagColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 11.sp
                    )
                }
            }

            Column {
                Text(
                    text = module.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextCharcoal,
                    maxLines = 1
                )
                Text(
                    text = module.hindiSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MandiPriceRow(
    cropName: String,
    mandiLocation: String,
    price: String,
    trend: String,
    isUp: Boolean?
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SurfaceBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = cropName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextCharcoal
                )
                Text(
                    text = mandiLocation,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = price,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DeepEmerald
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (isUp == true) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = DeepEmerald,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = trend,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isUp == true) DeepEmerald else TextMuted
                    )
                }
            }
        }
    }
}
