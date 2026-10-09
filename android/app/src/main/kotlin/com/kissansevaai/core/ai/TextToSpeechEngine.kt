package com.kissansevaai.core.ai

/**
 * Abstraction for Text-to-Speech (TTS) capabilities.
 */
interface TextToSpeechEngine {
    /**
     * Whether the underlying TTS engine has initialized successfully.
     */
    val isInitialized: Boolean

    /**
     * Checks if TTS supports the specified language code ('en', 'hi', 'mr', 'pa', 'sa').
     */
    fun isLanguageAvailable(langCode: String): Boolean

    /**
     * Speaks the given text in the requested language.
     * If the language is unsupported (e.g. Sanskrit), falls back to Hindi and notifies [onFallback].
     *
     * @param text The text to speak.
     * @param langCode The language code.
     * @param onFallback Invoked if a fallback language was substituted (e.g., "sa" -> "hi").
     * @param onStart Invoked when audio playback begins.
     * @param onDone Invoked when audio playback finishes.
     * @param onError Invoked if an error occurs.
     */
    fun speak(
        text: String,
        langCode: String,
        onFallback: ((originalLang: String, fallbackLang: String) -> Unit)? = null,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
    )

    /**
     * Stops current playback.
     */
    fun stop()

    /**
     * Releases TTS resources.
     */
    fun shutdown()
}
