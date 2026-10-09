package com.kissansevaai.ui.component

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kissansevaai.core.ai.SpeechToText
import com.kissansevaai.core.ai.TextToSpeechEngine
import com.kissansevaai.ui.theme.*

/**
 * Microphone button for speech-to-text with runtime permission handling
 * and locale support detection (e.g. Sanskrit rejection explanation).
 */
@Composable
fun MicButton(
    speechToText: SpeechToText,
    lang: String,
    onResult: (String) -> Unit,
    onError: (String) -> Unit = {},
    onExplanation: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            startListeningInternal(speechToText, lang, onResult, onError, onExplanation) {
                isListening = it
            }
        } else {
            val msg = "Microphone permission was denied. Voice input is disabled; text input is still available."
            onExplanation?.invoke(msg)
            onError(msg)
        }
    }

    // Pulse animation while listening
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    IconButton(
        onClick = {
            if (isListening) {
                speechToText.stopListening()
                isListening = false
                return@IconButton
            }

            // Check if language is supported first
            if (!speechToText.isLanguageSupported(lang)) {
                val reason = speechToText.getUnsupportedReason(lang)
                    ?: "Voice input is not supported for language: $lang"
                onExplanation?.invoke(reason)
                onError(reason)
                return@IconButton
            }

            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                startListeningInternal(speechToText, lang, onResult, onError, onExplanation) {
                    isListening = it
                }
            }
        },
        modifier = modifier
            .size(44.dp)
            .scale(if (isListening) pulseScale else 1f)
            .clip(CircleShape)
            .background(if (isListening) Emerald500 else Slate800),
    ) {
        Icon(
            imageVector = if (!speechToText.isLanguageSupported(lang)) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = if (isListening) "Stop listening" else "Start voice input",
            tint = if (isListening) Color.White else if (!speechToText.isLanguageSupported(lang)) Slate500 else Emerald400
        )
    }
}

private fun startListeningInternal(
    stt: SpeechToText,
    lang: String,
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
    onExplanation: ((String) -> Unit)?,
    setListening: (Boolean) -> Unit
) {
    if (!stt.isLanguageSupported(lang)) {
        val reason = stt.getUnsupportedReason(lang) ?: "Voice input unsupported"
        onExplanation?.invoke(reason)
        onError(reason)
        return
    }

    stt.startListening(
        langCode = lang,
        onResult = { text ->
            setListening(false)
            onResult(text)
        },
        onError = { err ->
            setListening(false)
            onError(err)
        },
        onReadyForSpeech = {
            setListening(true)
        }
    )
}

/**
 * Text-to-speech speaker button with fallback notification.
 */
@Composable
fun TTSButton(
    ttsEngine: TextToSpeechEngine,
    textToSpeak: String,
    lang: String,
    onFallbackExplanation: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var isSpeaking by remember { mutableStateOf(false) }

    IconButton(
        onClick = {
            if (isSpeaking) {
                ttsEngine.stop()
                isSpeaking = false
            } else {
                if (textToSpeak.isBlank()) return@IconButton
                ttsEngine.speak(
                    text = textToSpeak,
                    langCode = lang,
                    onFallback = { orig, fallback ->
                        onFallbackExplanation?.invoke("Sanskrit voice output is not supported on this device. Speaking in Hindi instead.")
                    },
                    onStart = { isSpeaking = true },
                    onDone = { isSpeaking = false },
                    onError = { isSpeaking = false }
                )
            }
        },
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (isSpeaking) Amber500.copy(alpha = 0.2f) else Color.Transparent),
    ) {
        Icon(
            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
            contentDescription = if (isSpeaking) "Stop voice" else "Read aloud",
            tint = if (isSpeaking) Amber500 else Emerald400,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Combined VoiceControls bar matching the web app VoiceControls component.
 */
@Composable
fun VoiceControls(
    speechToText: SpeechToText? = null,
    ttsEngine: TextToSpeechEngine? = null,
    lang: String,
    enableMic: Boolean = false,
    enableSpeaker: Boolean = false,
    textToSpeak: String? = null,
    onMicResult: ((String) -> Unit)? = null,
    onExplanation: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (enableMic && speechToText != null && onMicResult != null) {
            MicButton(
                speechToText = speechToText,
                lang = lang,
                onResult = onMicResult,
                onExplanation = onExplanation,
            )
        }

        if (enableSpeaker && ttsEngine != null && !textToSpeak.isNullOrBlank()) {
            TTSButton(
                ttsEngine = ttsEngine,
                textToSpeak = textToSpeak,
                lang = lang,
                onFallbackExplanation = onExplanation,
            )
        }
    }
}
