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
import com.kissansevaai.core.ai.RecognitionLocaleResolution
import com.kissansevaai.core.ai.SpeechToText
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private var activeCancelledCallback: (() -> Unit)? = null
    private var userCancelled = false

    override val isRecognitionAvailable: Boolean
        get() = try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (_: Exception) {
            false
        }

    override fun resolveRecognitionLocale(langCode: String): RecognitionLocaleResolution {
        val normalized = normalizeLang(langCode)
        return when (normalized) {
            "hi" -> RecognitionLocaleResolution("hi", "hi", "hi-IN")
            "mr" -> RecognitionLocaleResolution("mr", "mr", "mr-IN")
            "pa" -> RecognitionLocaleResolution("pa", "pa", "pa-IN")
            "bho" -> RecognitionLocaleResolution(
                requestedLang = "bho",
                effectiveLang = "hi",
                localeTag = "hi-IN",
                usedFallback = true,
                notice = "Bhojpuri speech uses Hindi (hi-IN) acoustic recognition on Android; agricultural words remain normalized.",
            )
            "sa" -> RecognitionLocaleResolution("sa", "sa", "sa-IN")
            else -> RecognitionLocaleResolution(normalized.ifBlank { "en" }, "en", "en-IN")
        }
    }

    /**
     * Checks if the language is supported by on-device / platform STT.
     * Android's SpeechRecognizer has no Sanskrit (sa) acoustic model on standard Android devices.
     */
    override fun isLanguageSupported(langCode: String): Boolean {
        if (!isRecognitionAvailable) return false
        val normalized = normalizeLang(langCode)
        if (normalized == "sa") return false
        return normalized in listOf("en", "hi", "mr", "pa", "bho")
    }

    override fun getUnsupportedReason(langCode: String): String? {
        if (!isRecognitionAvailable) {
            return "Voice recognition is not available on this device"
        }
        val normalized = normalizeLang(langCode)
        if (normalized == "sa") {
            return "Voice recognition is not supported in Sanskrit on this device. Please use text input or switch language."
        }
        if (normalized !in listOf("en", "hi", "mr", "pa", "bho")) {
            return "Voice recognition is not supported for $langCode"
        }
        return null
    }

    override fun startListening(
        langCode: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onReadyForSpeech: (() -> Unit)?,
        onPartialResult: ((String) -> Unit)?,
        onCancelled: (() -> Unit)?,
    ) {
        val normalized = normalizeLang(langCode)
        val reason = getUnsupportedReason(normalized)
        if (reason != null) {
            onError(reason)
            return
        }

        val resolution = resolveRecognitionLocale(normalized)

        postOnMain {
            try {
                destroyInternal()
                userCancelled = false
                activeCancelledCallback = onCancelled

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                    speechRecognizer = it
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, resolution.localeTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, resolution.localeTag)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
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
                        val wasCancelled = userCancelled
                        isListening = false
                        activeCancelledCallback = null
                        if (wasCancelled) return
                        val errorMsg = mapErrorCode(error)
                        Log.w("AndroidSpeechToText", "Speech recognition error code $error: $errorMsg")
                        onError(errorMsg)
                    }

                    override fun onResults(results: Bundle?) {
                        val wasCancelled = userCancelled
                        isListening = false
                        activeCancelledCallback = null
                        if (wasCancelled) return
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim()
                        if (!text.isNullOrEmpty()) {
                            onResult(text)
                        } else {
                            onError("No speech recognized. Please speak again.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        if (userCancelled) return
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                        if (!partial.isNullOrEmpty()) {
                            onPartialResult?.invoke(partial)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                recognizer.startListening(intent)
            } catch (e: Exception) {
                isListening = false
                activeCancelledCallback = null
                Log.e("AndroidSpeechToText", "Failed to start speech recognition", e)
                onError("Failed to start speech recognition: ${e.localizedMessage}")
            }
        }
    }

    override fun stopListening() {
        postOnMain {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {}
            isListening = false
        }
    }

    override fun cancelListening() {
        postOnMain {
            userCancelled = true
            val cb = activeCancelledCallback
            activeCancelledCallback = null
            destroyInternal()
            cb?.invoke()
        }
    }

    private fun destroyInternal() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        isListening = false
    }

    override fun destroy() {
        postOnMain {
            userCancelled = true
            activeCancelledCallback = null
            destroyInternal()
        }
    }

    private fun normalizeLang(lang: String): String {
        val lower = lang.lowercase().trim()
        return when {
            lower.startsWith("en") -> "en"
            lower.startsWith("hi") -> "hi"
            lower.startsWith("mr") -> "mr"
            lower.startsWith("pa") -> "pa"
            lower.startsWith("bho") -> "bho"
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
