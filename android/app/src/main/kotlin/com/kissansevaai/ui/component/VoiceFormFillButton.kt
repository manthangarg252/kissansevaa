package com.kissansevaai.ui.component

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kissansevaai.core.ai.FieldExtractor
import com.kissansevaai.core.ai.SpeechToText
import com.kissansevaai.ui.theme.Emerald400
import com.kissansevaai.ui.theme.Emerald600
import com.kissansevaai.ui.theme.Slate800
import kotlinx.coroutines.launch

/**
 * Voice form filling button that transcribes speech and uses [FieldExtractor]
 * to automatically populate form fields.
 */
@Composable
fun VoiceFormFillButton(
    fields: List<String>,
    lang: String,
    speechToText: SpeechToText,
    fieldExtractor: FieldExtractor,
    onFill: (Map<String, String>) -> Unit,
    onMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isListening by remember { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceFormFill(speechToText, fieldExtractor, fields, lang, onFill, onMessage, scope,
                setListening = { isListening = it },
                setExtracting = { isExtracting = it }
            )
        } else {
            onMessage("Microphone permission denied. Voice auto-fill unavailable.")
        }
    }

    OutlinedButton(
        onClick = {
            if (isListening) {
                speechToText.stopListening()
                isListening = false
                return@OutlinedButton
            }

            if (!speechToText.isLanguageSupported(lang)) {
                val reason = speechToText.getUnsupportedReason(lang)
                    ?: "Voice input is not supported for $lang"
                onMessage(reason)
                return@OutlinedButton
            }

            val hasMicPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasMicPermission) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                startVoiceFormFill(speechToText, fieldExtractor, fields, lang, onFill, onMessage, scope,
                    setListening = { isListening = it },
                    setExtracting = { isExtracting = it }
                )
            }
        },
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isListening) Emerald600.copy(alpha = 0.2f) else Slate800
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isExtracting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Emerald400)
                Text("Filling fields...", color = Emerald400)
            } else {
                Icon(
                    imageVector = if (!speechToText.isLanguageSupported(lang)) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Voice Form Fill",
                    tint = if (isListening) Emerald400 else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isListening) "Listening..." else "Fill with Voice",
                    color = if (isListening) Emerald400 else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun startVoiceFormFill(
    speechToText: SpeechToText,
    fieldExtractor: FieldExtractor,
    fields: List<String>,
    lang: String,
    onFill: (Map<String, String>) -> Unit,
    onMessage: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    setListening: (Boolean) -> Unit,
    setExtracting: (Boolean) -> Unit,
) {
    onMessage("Listening for form details...")
    speechToText.startListening(
        langCode = lang,
        onReadyForSpeech = { setListening(true) },
        onResult = { transcript ->
            setListening(false)
            setExtracting(true)
            onMessage("Extracting fields from speech...")
            scope.launch {
                try {
                    val extracted = fieldExtractor.extract(transcript, fields, lang)
                    setExtracting(false)
                    if (extracted.isNotEmpty()) {
                        onFill(extracted)
                        onMessage("Form fields updated from voice: ${extracted.keys.joinToString(", ")}")
                    } else {
                        onMessage("No matching fields found in speech.")
                    }
                } catch (e: Exception) {
                    setExtracting(false)
                    onMessage("Voice extraction error: ${e.message}")
                }
            }
        },
        onError = { err ->
            setListening(false)
            setExtracting(false)
            onMessage(err)
        }
    )
}
