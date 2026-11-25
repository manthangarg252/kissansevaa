package com.kissansevaai.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kissansevaai.R
import com.kissansevaai.core.ai.SpeechToText
import com.kissansevaai.core.ai.TextToSpeechEngine
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.repository.ChatRepository
import com.kissansevaai.ui.component.FeatureScaffold
import com.kissansevaai.ui.component.MicButton
import com.kissansevaai.ui.component.TTSButton
import com.kissansevaai.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val category: String = "general",
    val groundedSources: List<String> = emptyList(),
    val aiAvailable: Boolean = true,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val dataStore: DataStoreManager,
    val speechToText: SpeechToText,
    val ttsEngine: TextToSpeechEngine,
) : ViewModel() {

    val language: StateFlow<String> = dataStore.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    val messages: StateFlow<List<ChatMessage>> = repository.getSessionMessages("default")
        .map { list ->
            list.map {
                ChatMessage(
                    text = it.text,
                    isUser = it.sender == "user",
                    category = it.category,
                    groundedSources = it.groundedSources,
                    aiAvailable = it.aiAvailable,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    init {
        viewModelScope.launch { repository.syncServerHistory("default") }
    }

    fun send(text: String, mode: String = "general") {
        viewModelScope.launch {
            _isSending.value = true
            try {
                val lang = dataStore.language.first()
                repository.sendMessage(text, lang, mode, sessionId = "default").collect { res ->
                    when (res) {
                        is Resource.Loading -> _isSending.value = true
                        is Resource.Success, is Resource.Error -> _isSending.value = false
                    }
                }
            } catch (_: Exception) {
                _isSending.value = false
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory("default") }
    }
}

@Composable
fun ChatbotScreen(onBack: () -> Unit, viewModel: ChatViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val language by viewModel.language.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var selectedMode by remember { mutableStateOf("General") }

    val modes = listOf("General", "Crop Doctor", "Livestock", "Mandi Rates", "Schemes", "Weather", "Crop Planner")
    val suggestions = listOf(
        "What is wheat rate in Nagpur mandi?",
        "Am I eligible for PM-KISAN & PMFBY?",
        "Is weather good for spraying today?",
        "Next 7 day tasks for flowering Tomato",
    )

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    FeatureScaffold(
        title = stringResource(R.string.chatbot_title),
        subtitle = "Tool-Grounded Multi-Turn AI Assistant",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Mode Chips & Clear Button Row
            Surface(
                color = CardWhite,
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(modes) { mode ->
                            Surface(
                                shape = RoundedCornerShape(9999.dp),
                                color = if (selectedMode == mode) GreenTint else SurfaceContainerLow,
                                border = BorderStroke(1.dp, if (selectedMode == mode) DeepEmerald else BorderLight),
                                modifier = Modifier.clickable { selectedMode = mode }
                            ) {
                                Text(
                                    text = mode,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedMode == mode) DeepEmerald else TextCharcoal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearHistory() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear History", tint = TextMuted)
                        }
                    }
                }
            }

            // Chat Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                if (messages.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CardWhite),
                            border = BorderStroke(1.dp, BorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = GreenTint,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.SmartToy, null, tint = DeepEmerald, modifier = Modifier.size(22.dp))
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.chat_greeting_title), fontWeight = FontWeight.Bold, color = TextCharcoal)
                                    Text(
                                        stringResource(R.string.chat_greeting_body),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextCharcoal,
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        }
                    }
                }

                items(messages) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (!msg.isUser) {
                            Surface(
                                shape = CircleShape,
                                color = GreenTint,
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(bottom = 2.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.SmartToy, null, tint = DeepEmerald, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                        }

                        Card(
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (msg.isUser) 16.dp else 4.dp,
                                bottomEnd = if (msg.isUser) 4.dp else 16.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    msg.isUser -> DeepEmerald
                                    !msg.aiAvailable -> AmberTint
                                    else -> CardWhite
                                },
                            ),
                            border = if (!msg.isUser) BorderStroke(1.dp, BorderLight) else null,
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = if (msg.isUser) Modifier.widthIn(max = 280.dp) else Modifier.weight(1f, fill = false),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = msg.text,
                                    color = if (msg.isUser) Color.White else TextCharcoal,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp
                                )
                                if (!msg.isUser && msg.groundedSources.isNotEmpty()) {
                                    Surface(shape = RoundedCornerShape(8.dp), color = GreenBg) {
                                        Text(
                                            text = "Grounded: ${msg.groundedSources.joinToString(", ")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DeepEmerald,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (!msg.isUser) {
                            Spacer(Modifier.width(6.dp))
                            TTSButton(
                                ttsEngine = viewModel.ttsEngine,
                                textToSpeak = msg.text,
                                lang = language,
                                onFallbackExplanation = { fallbackMsg ->
                                    coroutineScope.launch { snackbarHostState.showSnackbar(fallbackMsg) }
                                }
                            )
                        }
                    }
                }

                if (isSending) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 40.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = DeepEmerald, strokeWidth = 2.dp)
                            Text(stringResource(R.string.chat_thinking), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }
            }

            // Quick suggestion pills
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(suggestions) { sugg ->
                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = CardWhite,
                        border = BorderStroke(1.dp, BorderMedium),
                        modifier = Modifier.clickable {
                            viewModel.send(sugg, selectedMode.lowercase())
                        }
                    ) {
                        Text(
                            text = sugg,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextCharcoal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Input Bar
            Surface(
                color = CardWhite,
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(stringResource(R.string.chatbot_hint)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepEmerald,
                            unfocusedBorderColor = BorderMedium
                        )
                    )

                    MicButton(
                        speechToText = viewModel.speechToText,
                        lang = language,
                        onResult = { recognizedText ->
                            input = if (input.isBlank()) recognizedText else "$input $recognizedText"
                        },
                        onExplanation = { explanation ->
                            coroutineScope.launch { snackbarHostState.showSnackbar(explanation) }
                        }
                    )

                    FilledIconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                viewModel.send(input.trim(), selectedMode.lowercase())
                                input = ""
                            }
                        },
                        enabled = input.isNotBlank() && !isSending,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = DeepEmerald,
                            contentColor = Color.White
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.chatbot_send))
                    }
                }
            }
        }
    }
}
