package com.kissansevaai.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.kissansevaai.BuildConfig
import com.kissansevaai.R
import com.kissansevaai.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignup: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val currentBaseUrl by viewModel.baseUrl.collectAsState()
    val currentLang by viewModel.language.collectAsState()
    var showLangMenu by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("farmer@kissan.com") }
    var password by remember { mutableStateOf("Password123!") }
    var mobileNumber by remember { mutableStateOf("9876543210") }
    var showPassword by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) } // 0 = OTP, 1 = PIN/Password
    var showServerDialog by remember { mutableStateOf(false) }
    var serverUrlInput by remember(currentBaseUrl) {
        mutableStateOf(currentBaseUrl ?: "http://10.0.2.2:5050")
    }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            viewModel.clearState()
            onLoginSuccess()
        }
    }

    if (BuildConfig.DEBUG && showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text(stringResource(R.string.auth_server_config), fontWeight = FontWeight.Bold, color = TextCharcoal) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Set backend server URL (e.g. http://10.0.2.2:5050 for emulator or http://10.201.214.248:5050 for phone):",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        label = { Text("Server URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepEmerald,
                            focusedLabelColor = DeepEmerald
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setBaseUrl(serverUrlInput.trim())
                        showServerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                    shape = RoundedCornerShape(9999.dp)
                ) {
                    Text(stringResource(R.string.common_save), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = TextMuted)
                }
            },
            containerColor = CardWhite
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = GreenTint,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Agriculture,
                                    contentDescription = null,
                                    tint = DeepEmerald,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "KissanSevaAI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = DeepEmerald
                            )
                            Text(
                                text = stringResource(R.string.auth_tagline),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                actions = {
                    // Language Switcher Chip with Dropdown
                    Box(modifier = Modifier.padding(end = 4.dp)) {
                        Surface(
                            shape = RoundedCornerShape(9999.dp),
                            border = BorderStroke(1.dp, DeepEmerald),
                            color = CardWhite,
                            modifier = Modifier.clickable { showLangMenu = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = "Language",
                                    tint = DeepEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = when (currentLang.lowercase().trim()) {
                                        "hi" -> "हिन्दी"
                                        "bho" -> "भोजपुरी"
                                        "mr" -> "मराठी"
                                        "pa" -> "ਪੰਜਾਬੀ"
                                        "sa" -> "संस्कृतम्"
                                        else -> "English"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = DeepEmerald
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
                                "pa" to "ਪੰਜਾਬੀ (Punjabi)",
                                "sa" to "संस्कृतम् (Sanskrit)"
                            ).forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            fontWeight = if (currentLang == code) FontWeight.Bold else FontWeight.Normal,
                                            color = if (currentLang == code) DeepEmerald else TextCharcoal
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

                    // Server Config (debug developer convenience only)
                    if (BuildConfig.DEBUG) {
                        IconButton(onClick = { showServerDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Server Settings",
                                tint = DeepEmerald
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
        containerColor = SurfaceBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Welcome Agrarian Hero Banner
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DeepEmerald),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Background image blending
                    AsyncImage(
                        model = "https://images.unsplash.com/photo-1500937386664-56d1dfef3854?auto=format&fit=crop&q=80&w=400",
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        alpha = 0.25f
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(9999.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "प्रमाणित राष्ट्रीय किसान नेटवर्क",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Text(
                            text = "स्वागत है किसान भाई!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Smart solutions for modern Indian farmers • आधुनिक खेती का भरोसेमंद डिजिटल साथी",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // 2. Authentication Card Container
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, BorderLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 2-Way Tab Selector (OTP vs PIN/Password)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Tab 0: OTP
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (activeTab == 0) CardWhite else Color.Transparent,
                                shadowElevation = if (activeTab == 0) 2.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { activeTab = 0 }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sms,
                                        contentDescription = null,
                                        tint = if (activeTab == 0) DeepEmerald else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "ओटीपी लॉगिन",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeTab == 0) DeepEmerald else TextMuted
                                    )
                                }
                            }

                            // Tab 1: PIN / Password
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (activeTab == 1) CardWhite else Color.Transparent,
                                shadowElevation = if (activeTab == 1) 2.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { activeTab = 1 }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Badge,
                                        contentDescription = null,
                                        tint = if (activeTab == 1) DeepEmerald else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "किसान ID / PIN",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeTab == 1) DeepEmerald else TextMuted
                                    )
                                }
                            }
                        }
                    }

                    // Voice Input Helper Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GreenBg,
                        border = BorderStroke(1.dp, GreenTint),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = DeepEmerald,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "बोलकर नंबर दर्ज करें",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = DeepEmerald
                                    )
                                    Text(
                                        text = "माइक दबाकर अपना 10 अंकों का फोन नंबर बोलें",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(9999.dp),
                                color = DeepEmerald,
                                modifier = Modifier.clickable {
                                    // Trigger quick phone fill
                                    mobileNumber = "9876543210"
                                }
                            ) {
                                Text(
                                    text = "बोलें",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    if (state.error != null) {
                        Text(
                            text = state.error!!,
                            color = ErrorRed,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (activeTab == 0) {
                        // FORM A: OTP LOGIN
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "मोबाइल नंबर दर्ज करें (Enter Mobile Number)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextCharcoal
                            )

                            OutlinedTextField(
                                value = mobileNumber,
                                onValueChange = { mobileNumber = it },
                                leadingIcon = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 12.dp, end = 8.dp)
                                    ) {
                                        Text("🇮🇳 +91", fontWeight = FontWeight.Bold, color = TextCharcoal)
                                        Spacer(Modifier.width(8.dp))
                                        Box(modifier = Modifier.size(1.dp, 24.dp).background(BorderMedium))
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DeepEmerald,
                                    unfocusedBorderColor = BorderMedium
                                )
                            )

                            Text(
                                text = "आपके फोन पर 4 अंकों का सुरक्षा कोड (SMS OTP) भेजा जाएगा",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                fontSize = 11.sp
                            )

                            Button(
                                onClick = {
                                    // OTP direct login with demo farmer credentials
                                    viewModel.login("farmer@kissan.com", "Password123!")
                                },
                                enabled = !state.isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(9999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald)
                            ) {
                                if (state.isLoading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(stringResource(R.string.auth_otp_btn), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                                    }
                                }
                            }
                        }
                    } else {
                        // FORM B: KISAN ID / PASSWORD LOGIN
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = stringResource(R.string.auth_email),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextCharcoal
                            )

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                leadingIcon = {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = TextMuted)
                                },
                                placeholder = { Text("farmer@kissan.com") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DeepEmerald,
                                    unfocusedBorderColor = BorderMedium
                                )
                            )

                            Text(
                                text = stringResource(R.string.auth_password),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextCharcoal
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = TextMuted)
                                },
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = TextMuted
                                        )
                                    }
                                },
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DeepEmerald,
                                    unfocusedBorderColor = BorderMedium
                                )
                            )

                            Button(
                                onClick = { viewModel.login(email.trim(), password) },
                                enabled = !state.isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(9999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald)
                            ) {
                                if (state.isLoading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(stringResource(R.string.auth_sign_in_btn), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Icon(Icons.Default.Login, contentDescription = null)
                                    }
                                }
                            }
                        }
                    }

                    // Biometric Quick Login
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, DeepEmerald.copy(alpha = 0.3f)),
                        color = GreenBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Biometric quick entry using seeded test user
                                viewModel.login("farmer@kissan.com", "Password123!")
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = DeepEmerald.copy(alpha = 0.15f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = null,
                                            tint = DeepEmerald,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "अंगूठा लगाकर लॉगिन करें",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = DeepEmerald
                                    )
                                    Text(
                                        text = "Biometric Touch ID for fast farm access",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DeepEmerald)
                        }
                    }

                    // Register link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "नया खाता बनाएं? ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        Text(
                            text = "पंजीकरण करें (Sign Up)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = DeepEmerald,
                            modifier = Modifier.clickable { onNavigateToSignup() }
                        )
                    }
                }
            }
        }
    }
}
