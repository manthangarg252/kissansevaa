package com.kissansevaai.feature.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kissansevaai.R
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.repository.ChatRepository
import com.kissansevaai.ui.component.FeatureScaffold
import com.kissansevaai.ui.theme.*
import com.kissansevaai.core.ai.SpeechToText
import com.kissansevaai.core.ai.TextToSpeechEngine
import com.kissansevaai.ui.component.MicButton
import com.kissansevaai.ui.component.TTSButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(val text: String, val isUser: Boolean)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val dataStore: DataStoreManager,
    val speechToText: SpeechToText,
    val ttsEngine: TextToSpeechEngine,
) : ViewModel() {

    val language: StateFlow<String> = dataStore.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    val messages: StateFlow<List<ChatMessage>> = repository.getMessages()
        .map { list -> list.map { ChatMessage(it.text, it.sender == "user") } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    fun send(text: String) {
        viewModelScope.launch {
            _isSending.value = true
            try {
                val lang = dataStore.language.first()
                repository.sendMessage(text, lang).collect { res ->
                    when (res) {
                        is Resource.Loading -> _isSending.value = true
                        is Resource.Success -> _isSending.value = false
                        is Resource.Error -> _isSending.value = false
                    }
                }
            } catch (_: Exception) {
                _isSending.value = false
            }
        }
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

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    FeatureScaffold(
        title = stringResource(R.string.chatbot_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(messages) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (msg.isUser) Emerald600 else Slate800,
                            ),
                            modifier = if (msg.isUser) Modifier.widthIn(max = 260.dp) else Modifier.weight(1f, fill = false),
                        ) {
                            Text(msg.text, Modifier.padding(12.dp), color = if (msg.isUser) Slate950 else Slate200, style = MaterialTheme.typography.bodyMedium)
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
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = Emerald500, strokeWidth = 2.dp)
                        }
                    }
                }
            }
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    placeholder = { Text(stringResource(R.string.chatbot_hint)) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                )

                // Voice STT mic button
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
                    onClick = { if (input.isNotBlank()) { viewModel.send(input.trim()); input = "" } },
                    enabled = input.isNotBlank() && !isSending,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Emerald600),
                ) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.chatbot_send)) }
            }
        }
    }
}
