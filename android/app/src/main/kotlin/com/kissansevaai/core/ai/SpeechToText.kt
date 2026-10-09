package com.kissansevaai.core.ai

/**
 * Abstraction for Speech-to-Text (STT) capabilities.
 */
interface SpeechToText {
    /**
     * Whether the speech recognition service is installed and available on this device.
     */
    val isRecognitionAvailable: Boolean

    /**
     * Returns true if speech recognition is available and supports the given language code.
     */
    fun isLanguageSupported(langCode: String): Boolean

    /**
     * Explanatory reason if the given language is not supported for voice input.
     */
    fun getUnsupportedReason(langCode: String): String?

    /**
     * Starts listening for speech in the requested language.
     *
     * @param langCode The language code ('en', 'hi', 'mr', 'pa', 'sa').
     * @param onResult Callback invoked with the recognized text string.
     * @param onError Callback invoked when an error occurs with a user-friendly message.
     * @param onReadyForSpeech Optional callback invoked when the microphone is active and listening.
     */
    fun startListening(
        langCode: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onReadyForSpeech: (() -> Unit)? = null
    )

    /**
     * Stops listening.
     */
    fun stopListening()

    /**
     * Releases any resources held by the recognizer.
     */
    fun destroy()
}
