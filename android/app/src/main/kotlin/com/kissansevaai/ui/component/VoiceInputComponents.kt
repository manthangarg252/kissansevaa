package com.kissansevaai.ui.component

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kissansevaai.R
import com.kissansevaai.core.ai.FieldExtractor
import com.kissansevaai.core.ai.SpeechToText
import com.kissansevaai.core.ai.voice.AgriculturalVoiceParser
import com.kissansevaai.core.ai.voice.HybridFieldExtractor
import com.kissansevaai.core.ai.voice.VoiceExtractionOutcome
import com.kissansevaai.ui.theme.*
import kotlinx.coroutines.launch

private val VOICE_LANG_OPTIONS = listOf(
    "hi" to "हिन्दी",
    "en" to "English",
    "pa" to "ਪੰਜਾਬੀ",
    "mr" to "मराठी",
    "bho" to "भोजपुरी",
)

/**
 * Reusable accessible VoiceInputField supporting single-field voice dictation,
 * runtime RECORD_AUDIO request on tap, live partial transcription, Stop/Cancel,
 * Retry, language switch, and editable confirmation before committing value.
 */
@Composable
fun VoiceInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    lang: String,
    speechToText: SpeechToText,
    modifier: Modifier = Modifier,
    fieldKey: String = "text",
    placeholder: String = "",
    leadingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    supportingWarning: String? = null,
    onMessage: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var activeLang by remember(lang) { mutableStateOf(lang) }
    var isListening by remember { mutableStateOf(false) }
    var partialTranscript by remember { mutableStateOf("") }
    var pendingConfirmationText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var localeNotice by remember { mutableStateOf<String?>(null) }

    DisposableEffect(speechToText) {
        onDispose {
            if (isListening) {
                speechToText.cancelListening()
            }
        }
    }

    fun beginDictation() {
        val resolution = speechToText.resolveRecognitionLocale(activeLang)
        localeNotice = resolution.notice
        if (!speechToText.isLanguageSupported(activeLang)) {
            val reason = speechToText.getUnsupportedReason(activeLang)
                ?: "Voice input unavailable for $activeLang"
            errorMessage = reason
            onMessage(reason)
            return
        }
        errorMessage = null
        partialTranscript = ""
        speechToText.startListening(
            langCode = activeLang,
            onReadyForSpeech = { isListening = true },
            onPartialResult = { partial -> partialTranscript = partial },
            onResult = { transcript ->
                isListening = false
                partialTranscript = ""
                val outcome = AgriculturalVoiceParser.parse(transcript, listOf(fieldKey), activeLang)
                val mapped = outcome.extractedFields[fieldKey] ?: outcome.normalizedTranscript.ifBlank { transcript }
                pendingConfirmationText = mapped
                if (outcome.ambiguousFields[fieldKey] != null) {
                    errorMessage = outcome.ambiguousFields[fieldKey]
                }
            },
            onError = { err ->
                isListening = false
                partialTranscript = ""
                errorMessage = err
                onMessage(err)
            },
            onCancelled = {
                isListening = false
                partialTranscript = ""
            },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginDictation()
        } else {
            val msg = if (activeLang.startsWith("hi")) {
                "माइक्रोफ़ोन अनुमति अस्वीकार कर दी गई है। आप टाइप करके भर सकते हैं।"
            } else {
                "Microphone permission denied. Please enable it or type manually."
            }
            errorMessage = msg
            onMessage(msg)
        }
    }

    fun launchWithPermission() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) beginDictation() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val pulse = rememberInfiniteTransition(label = "voiceFieldPulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    ).value

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder) }) else null,
            leadingIcon = leadingIcon,
            singleLine = singleLine,
            isError = !supportingWarning.isNullOrBlank(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DeepEmerald,
                unfocusedBorderColor = BorderMedium,
            ),
            trailingIcon = {
                val micDesc = if (isListening) {
                    stringResource(R.string.voice_stop_listening)
                } else {
                    stringResource(R.string.voice_tap_to_speak, label)
                }
                IconButton(
                    onClick = {
                        if (isListening) speechToText.cancelListening() else launchWithPermission()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .scale(if (isListening) pulse else 1f)
                        .clip(CircleShape)
                        .background(if (isListening) DeepEmerald else GreenBg)
                        .semantics { contentDescription = micDesc },
                ) {
                    Icon(
                        imageVector = when {
                            isListening -> Icons.Default.Stop
                            !speechToText.isLanguageSupported(activeLang) -> Icons.Default.MicOff
                            else -> Icons.Default.Mic
                        },
                        contentDescription = micDesc,
                        tint = if (isListening) Color.White else DeepEmerald,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (!supportingWarning.isNullOrBlank()) {
            Text(
                text = supportingWarning,
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryOrange,
            )
        }

        if (isListening) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = GreenBg,
                border = BorderStroke(1.dp, DeepEmerald),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (activeLang.startsWith("hi")) "सुन रहा है ($activeLang)..." else "Listening ($activeLang)...",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = DeepEmerald,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { speechToText.stopListening() }) {
                                Text(stringResource(R.string.voice_stop_listening), color = DeepEmerald)
                            }
                            TextButton(onClick = { speechToText.cancelListening() }) {
                                Text(stringResource(R.string.voice_cancel), color = ErrorRed)
                            }
                        }
                    }
                    localeNotice?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                    if (partialTranscript.isNotBlank()) {
                        Text(
                            text = partialTranscript,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextCharcoal,
                        )
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        VOICE_LANG_OPTIONS.filter { speechToText.isLanguageSupported(it.first) }.forEach { (code, title) ->
                            FilterChip(
                                selected = activeLang == code,
                                onClick = {
                                    activeLang = code
                                    speechToText.cancelListening()
                                    launchWithPermission()
                                },
                                label = { Text(title) },
                            )
                        }
                    }
                }
            }
        }

        pendingConfirmationText?.let { draft ->
            var editableDraft by remember(draft) { mutableStateOf(draft) }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AmberTint,
                border = BorderStroke(1.dp, SecondaryOrange),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (activeLang.startsWith("hi")) "बोला गया पाठ जांचें और लागू करें:" else "Review & confirm spoken value for $label:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SecondaryOrange,
                    )
                    OutlinedTextField(
                        value = editableDraft,
                        onValueChange = { editableDraft = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onValueChange(editableDraft.trim())
                                pendingConfirmationText = null
                                errorMessage = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                            shape = RoundedCornerShape(9999.dp),
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.voice_confirm_apply))
                        }
                        OutlinedButton(
                            onClick = {
                                pendingConfirmationText = null
                                launchWithPermission()
                            },
                            shape = RoundedCornerShape(9999.dp),
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.voice_retry))
                        }
                        TextButton(onClick = { pendingConfirmationText = null }) {
                            Text(stringResource(R.string.voice_cancel), color = TextMuted)
                        }
                    }
                }
            }
        }

        errorMessage?.takeIf { !isListening }?.let { err ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ErrorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(err, color = ErrorRed, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { launchWithPermission() }) {
                        Text(stringResource(R.string.voice_retry), color = DeepEmerald)
                    }
                }
            }
        }
    }
}

/**
 * Form-wide reusable "Fill by Voice" action supporting multi-field Hindi/English/regional
 * speech extraction, partial transcription, stop/cancel/retry, language switching,
 * ambiguity highlighting, editable field preview, and explicit farmer confirmation before applying.
 */
@Composable
fun FillByVoiceButton(
    fields: List<String>,
    lang: String,
    speechToText: SpeechToText,
    fieldExtractor: FieldExtractor,
    onFill: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
    fieldLabels: Map<String, String> = emptyMap(),
    onAmbiguitiesDetected: (Map<String, String>) -> Unit = {},
    onMessage: (String) -> Unit = {},
    buttonLabel: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeLang by remember(lang) { mutableStateOf(lang) }
    var isListening by remember { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }
    var errorNotice by remember { mutableStateOf<String?>(null) }
    var localeNotice by remember { mutableStateOf<String?>(null) }
    var pendingOutcome by remember { mutableStateOf<VoiceExtractionOutcome?>(null) }

    DisposableEffect(speechToText) {
        onDispose {
            if (isListening) speechToText.cancelListening()
        }
    }

    fun extractFromTranscript(transcript: String) {
        isExtracting = true
        scope.launch {
            try {
                val outcome = if (fieldExtractor is HybridFieldExtractor) {
                    fieldExtractor.extractDetailed(transcript, fields, activeLang, allowRemoteFallback = true)
                } else {
                    val map = fieldExtractor.extract(transcript, fields, activeLang)
                    VoiceExtractionOutcome(
                        rawTranscript = transcript,
                        normalizedTranscript = AgriculturalVoiceParser.normalizeTranscriptCropTerms(transcript),
                        extractedFields = map,
                    )
                }
                isExtracting = false
                pendingOutcome = outcome
                onAmbiguitiesDetected(outcome.ambiguousFields)
                if (outcome.extractedFields.isEmpty() && outcome.ambiguousFields.isEmpty()) {
                    onMessage("Spoken text captured. Review or edit fields manually.")
                }
            } catch (e: Exception) {
                isExtracting = false
                val msg = "Voice extraction error: ${e.message ?: "unknown"}"
                errorNotice = msg
                onMessage(msg)
            }
        }
    }

    fun startDictation() {
        val res = speechToText.resolveRecognitionLocale(activeLang)
        localeNotice = res.notice
        if (!speechToText.isLanguageSupported(activeLang)) {
            val reason = speechToText.getUnsupportedReason(activeLang) ?: "Voice input unsupported"
            errorNotice = reason
            onMessage(reason)
            return
        }
        errorNotice = null
        partialText = ""
        onMessage("Listening for form details...")
        speechToText.startListening(
            langCode = activeLang,
            onReadyForSpeech = { isListening = true },
            onPartialResult = { partial -> partialText = partial },
            onResult = { transcript ->
                isListening = false
                partialText = ""
                extractFromTranscript(transcript)
            },
            onError = { err ->
                isListening = false
                partialText = ""
                errorNotice = err
                onMessage(err)
            },
            onCancelled = {
                isListening = false
                partialText = ""
            },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startDictation()
        } else {
            val msg = if (activeLang.startsWith("hi")) {
                "माइक्रोफ़ोन अनुमति अस्वीकार की गई। कृपया सेटिंग्स से अनुमति दें या टाइप करें।"
            } else {
                "Microphone permission denied. Voice auto-fill unavailable."
            }
            errorNotice = msg
            onMessage(msg)
        }
    }

    fun triggerVoice() {
        if (isListening) {
            speechToText.cancelListening()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startDictation() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    pendingOutcome?.let { outcome ->
        var editableTranscript by remember(outcome) { mutableStateOf(outcome.rawTranscript) }
        val editableValues = remember(outcome) {
            mutableStateMapOf<String, String>().apply {
                fields.forEach { k -> put(k, outcome.extractedFields[k] ?: "") }
                outcome.extractedFields.forEach { (k, v) -> if (!containsKey(k)) put(k, v) }
            }
        }

        AlertDialog(
            onDismissRequest = { pendingOutcome = null },
            containerColor = CardWhite,
            title = {
                Text(
                    text = stringResource(R.string.voice_review_title),
                    fontWeight = FontWeight.Bold,
                    color = DeepEmerald,
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = if (activeLang.startsWith("hi")) "सुना गया वाक्य (संपादित कर सकते हैं):" else "Recognized Speech (editable before applying):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextCharcoal,
                    )
                    OutlinedTextField(
                        value = editableTranscript,
                        onValueChange = { editableTranscript = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        trailingIcon = {
                            IconButton(onClick = { extractFromTranscript(editableTranscript) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Re-parse transcript", tint = DeepEmerald)
                            }
                        },
                    )

                    if (outcome.hasAmbiguities || outcome.warnings.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AmberTint,
                            border = BorderStroke(1.dp, SecondaryOrange),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Warning, null, tint = SecondaryOrange, modifier = Modifier.size(16.dp))
                                    Text("Needs Confirmation", fontWeight = FontWeight.Bold, color = SecondaryOrange, style = MaterialTheme.typography.labelMedium)
                                }
                                outcome.ambiguousFields.values.distinct().forEach { warn ->
                                    Text("• $warn", style = MaterialTheme.typography.bodySmall, color = TextCharcoal)
                                }
                                outcome.warnings.filterNot { w -> w in outcome.ambiguousFields.values }.forEach { warn ->
                                    Text("• $warn", style = MaterialTheme.typography.bodySmall, color = TextCharcoal)
                                }
                            }
                        }
                    }

                    val displayKeys = editableValues.keys.toList()
                    displayKeys.forEach { key ->
                        val niceLabel = fieldLabels[key] ?: key.replace(Regex("([A-Z])"), " $1").replaceFirstChar { it.uppercase() }
                        val isAmbiguous = outcome.ambiguousFields.containsKey(key)
                        OutlinedTextField(
                            value = editableValues[key] ?: "",
                            onValueChange = { editableValues[key] = it },
                            label = { Text(if (isAmbiguous) "$niceLabel (Confirm!)" else niceLabel) },
                            isError = isAmbiguous && (editableValues[key].isNullOrBlank()),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = editableValues.mapValues { it.value.trim() }.filterValues { it.isNotBlank() }
                        onFill(cleaned)
                        pendingOutcome = null
                        if (cleaned.isNotEmpty()) {
                            onMessage("Applied ${cleaned.size} voice-filled field(s) — review before saving.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                    shape = RoundedCornerShape(9999.dp),
                ) {
                    Text(stringResource(R.string.voice_confirm_apply), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            pendingOutcome = null
                            triggerVoice()
                        },
                        shape = RoundedCornerShape(9999.dp),
                    ) {
                        Text(stringResource(R.string.voice_retry), color = DeepEmerald)
                    }
                    TextButton(onClick = { pendingOutcome = null }) {
                        Text(stringResource(R.string.voice_cancel), color = TextMuted)
                    }
                }
            },
        )
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val defaultText = buttonLabel ?: stringResource(R.string.voice_fill_button)
        val desc = if (isListening) stringResource(R.string.voice_stop_listening) else defaultText
        OutlinedButton(
            onClick = { triggerVoice() },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .semantics { contentDescription = desc },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, if (isListening) SecondaryOrange else DeepEmerald),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (isListening) GreenBg else CardWhite,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isExtracting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = DeepEmerald)
                    Text(if (activeLang.startsWith("hi")) "फ़ॉर्म भरा जा रहा है..." else "Extracting details...", color = DeepEmerald, fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = when {
                            isListening -> Icons.Default.Stop
                            !speechToText.isLanguageSupported(activeLang) -> Icons.Default.MicOff
                            else -> Icons.Default.Mic
                        },
                        contentDescription = desc,
                        tint = if (isListening) SecondaryOrange else DeepEmerald,
                    )
                    Text(
                        text = if (isListening) stringResource(R.string.voice_stop_listening) else defaultText,
                        color = if (isListening) SecondaryOrange else DeepEmerald,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (isListening) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = GreenBg,
                border = BorderStroke(1.dp, DeepEmerald),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (activeLang.startsWith("hi")) "सुन रहा है — बोलें..." else "Listening ($activeLang) — speak naturally...",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = DeepEmerald,
                        )
                        Row {
                            TextButton(onClick = { speechToText.stopListening() }) {
                                Text("Done", color = DeepEmerald)
                            }
                            TextButton(onClick = { speechToText.cancelListening() }) {
                                Text(stringResource(R.string.voice_cancel), color = ErrorRed)
                            }
                        }
                    }
                    localeNotice?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = TextMuted) }
                    if (partialText.isNotBlank()) {
                        Text(partialText, style = MaterialTheme.typography.bodySmall, color = TextCharcoal)
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        VOICE_LANG_OPTIONS.filter { speechToText.isLanguageSupported(it.first) }.forEach { (code, title) ->
                            FilterChip(
                                selected = activeLang == code,
                                onClick = {
                                    activeLang = code
                                    speechToText.cancelListening()
                                    startDictation()
                                },
                                label = { Text(title) },
                            )
                        }
                    }
                }
            }
        }

        errorNotice?.takeIf { !isListening }?.let { err ->
            Surface(shape = RoundedCornerShape(10.dp), color = ErrorContainer, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(err, color = ErrorRed, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { triggerVoice() }) {
                        Text(stringResource(R.string.voice_retry), color = DeepEmerald)
                    }
                    IconButton(onClick = { errorNotice = null }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
