package com.kissansevaai.core.ai.ondevice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.kissansevaai.core.ai.SpeechToText
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSpeechToText @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpeechToText {

    private fun postOnMain(action: () -> Unit) {
        try {
            val looper = Looper.getMainLooper()
            if (looper != null) {
                Handler(looper).post(action)
            } else {
                action()
            }
        } catch (_: Throwable) {
            action()
        }
    }
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    override val isRecognitionAvailable: Boolean
        get() = try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (_: Exception) {
            false
        }

    /**
     * Checks if the language is supported by on-device / platform STT.
     * Android's SpeechRecognizer has no Sanskrit (sa) acoustic model on standard Android devices.
     */
    override fun isLanguageSupported(langCode: String): Boolean {
        if (!isRecognitionAvailable) return false
        val normalized = normalizeLang(langCode)
        if (normalized == "sa") return false
        return normalized in listOf("en", "hi", "mr", "pa")
    }

    override fun getUnsupportedReason(langCode: String): String? {
        if (!isRecognitionAvailable) {
            return "Voice recognition is not available on this device"
        }
        val normalized = normalizeLang(langCode)
        if (normalized == "sa") {
            return "Voice recognition is not supported in Sanskrit on this device. Please use text input or switch language."
        }
        if (normalized !in listOf("en", "hi", "mr", "pa")) {
            return "Voice recognition is not supported for $langCode"
        }
        return null
    }

    override fun startListening(
        langCode: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onReadyForSpeech: (() -> Unit)?,
    ) {
        val normalized = normalizeLang(langCode)
        val reason = getUnsupportedReason(normalized)
        if (reason != null) {
            onError(reason)
            return
        }

        postOnMain {
            try {
                stopListeningInternal()

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                    speechRecognizer = it
                }

                val localeTag = when (normalized) {
                    "hi" -> "hi-IN"
                    "mr" -> "mr-IN"
                    "pa" -> "pa-IN"
                    "sa" -> "sa-IN"
                    else -> "en-IN"
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        onReadyForSpeech?.invoke()
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        val errorMsg = mapErrorCode(error)
                        Log.w("AndroidSpeechToText", "Speech recognition error code $error: $errorMsg")
                        onError(errorMsg)
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim()
                        if (!text.isNullOrEmpty()) {
                            onResult(text)
                        } else {
                            onError("No speech recognized. Please speak again.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                recognizer.startListening(intent)
            } catch (e: Exception) {
                isListening = false
                Log.e("AndroidSpeechToText", "Failed to start speech recognition", e)
                onError("Failed to start speech recognition: ${e.localizedMessage}")
            }
        }
    }

    override fun stopListening() {
        postOnMain {
            stopListeningInternal()
        }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        isListening = false
    }

    override fun destroy() {
        stopListening()
    }

    private fun normalizeLang(lang: String): String {
        val lower = lang.lowercase().trim()
        return when {
            lower.startsWith("en") -> "en"
            lower.startsWith("hi") -> "hi"
            lower.startsWith("mr") -> "mr"
            lower.startsWith("pa") -> "pa"
            lower.startsWith("sa") -> "sa"
            else -> lower
        }
    }

    private fun mapErrorCode(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone."
            SpeechRecognizer.ERROR_CLIENT -> "Speech recognition service unavailable."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required for voice input."
            SpeechRecognizer.ERROR_NETWORK -> "Network connection required for speech recognition."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out during voice recognition."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak clearly into the microphone."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service is busy. Please try again."
            SpeechRecognizer.ERROR_SERVER -> "Recognition server error. Try again later."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected."
            else -> "Speech recognition error ($error)"
        }
    }
}
