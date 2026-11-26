package com.kissansevaai.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kissansevaai.core.ai.FieldExtractor
import com.kissansevaai.core.ai.SpeechToText

/**
 * Preserved wrapper delegating to [FillByVoiceButton] so all existing callers stay compatible.
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
    FillByVoiceButton(
        fields = fields,
        lang = lang,
        speechToText = speechToText,
        fieldExtractor = fieldExtractor,
        onFill = onFill,
        onMessage = onMessage,
        modifier = modifier,
    )
}
