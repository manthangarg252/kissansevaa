package com.kissansevaai.core.ai

data class RecognitionLocaleResolution(
    val requestedLang: String,
    val effectiveLang: String,
    val localeTag: String,
    val usedFallback: Boolean = false,
    val notice: String? = null,
)

/**
 * Abstraction for Speech-to-Text (STT) capabilities.
 */
interface SpeechToText {
    /**
     * Whether the speech recognition service is installed and available on this device.
     */
    val isRecognitionAvailable: Boolean

    /**
     * Resolves the recognition locale cleanly (e.g. falling back to hi-IN if Bhojpuri pack isn't separate).
     */
    fun resolveRecognitionLocale(langCode: String): RecognitionLocaleResolution = RecognitionLocaleResolution(
        requestedLang = langCode,
        effectiveLang = langCode,
        localeTag = if (langCode.startsWith("hi")) "hi-IN" else "en-IN",
    )

    /**
     * Returns true if speech recognition is available and supports the given language code.
     */
    fun isLanguageSupported(langCode: String): Boolean

    /**
     * Explanatory reason if the given language is not supported for voice input.
     */
    fun getUnsupportedReason(langCode: String): String?

    /**
     * Starts listening for speech in the requested language with optional partial transcription & cancel callbacks.
     *
     * @param langCode The language code ('en', 'hi', 'mr', 'pa', 'bho', 'sa').
     * @param onResult Callback invoked with the final recognized text string.
     * @param onError Callback invoked when an error occurs with a user-friendly message.
     * @param onReadyForSpeech Optional callback invoked when the microphone is active and listening.
     * @param onPartialResult Optional callback invoked with interim partial transcription when supported.
     * @param onCancelled Optional callback invoked when listening is cancelled by the user.
     */
    fun startListening(
        langCode: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onReadyForSpeech: (() -> Unit)? = null,
        onPartialResult: ((String) -> Unit)? = null,
        onCancelled: (() -> Unit)? = null,
    )

    /**
     * Stops listening cleanly while preserving any final callback in progress.
     */
    fun stopListening()

    /**
     * Cancels active recognition immediately without emitting a result error.
     */
    fun cancelListening() = stopListening()

    /**
     * Releases any resources held by the recognizer.
     */
    fun destroy()
}
