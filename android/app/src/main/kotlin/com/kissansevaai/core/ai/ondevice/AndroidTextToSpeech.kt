package com.kissansevaai.core.ai.ondevice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.kissansevaai.core.ai.TextToSpeechEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidTextToSpeech @Inject constructor(
    @ApplicationContext private val context: Context,
) : TextToSpeechEngine, TextToSpeech.OnInitListener {

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

    private var tts: TextToSpeech? = null
    override var isInitialized: Boolean = false
        private set

    private var onInitCallbacks = mutableListOf<() -> Unit>()
    private var currentUtteranceId: String? = null
    private var currentOnDone: (() -> Unit)? = null
    private var currentOnError: ((String) -> Unit)? = null
    private var currentOnStart: (() -> Unit)? = null

    init {
        postOnMain {
            try {
                tts = TextToSpeech(context, this)
            } catch (e: Exception) {
                Log.e("AndroidTextToSpeech", "Failed to instantiate TextToSpeech", e)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    postOnMain { currentOnStart?.invoke() }
                }

                override fun onDone(utteranceId: String?) {
                    postOnMain {
                        currentOnDone?.invoke()
                        clearCallbacks()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    postOnMain {
                        currentOnError?.invoke("TTS playback error")
                        clearCallbacks()
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    postOnMain {
                        currentOnError?.invoke("TTS error code: $errorCode")
                        clearCallbacks()
                    }
                }
            })

            synchronized(onInitCallbacks) {
                onInitCallbacks.forEach { it.invoke() }
                onInitCallbacks.clear()
            }
            Log.i("AndroidTextToSpeech", "TextToSpeech successfully initialized")
        } else {
            isInitialized = false
            Log.e("AndroidTextToSpeech", "TextToSpeech initialization failed with status: $status")
        }
    }

    private fun runWhenReady(action: () -> Unit) {
        if (isInitialized) {
            action()
        } else {
            synchronized(onInitCallbacks) {
                onInitCallbacks.add(action)
            }
        }
    }

    fun getLocaleForLanguage(langCode: String): Locale {
        return when (normalizeLang(langCode)) {
            "hi" -> Locale("hi", "IN")
            "mr" -> Locale("mr", "IN")
            "pa" -> Locale("pa", "IN")
            "sa" -> Locale("sa", "IN")
            else -> Locale("en", "IN")
        }
    }

    /**
     * Directly returns the Android TextToSpeech raw integer return code:
     * LANG_AVAILABLE (0), LANG_COUNTRY_AVAILABLE (1), LANG_COUNTRY_VAR_AVAILABLE (2),
     * LANG_MISSING_DATA (-1), LANG_NOT_SUPPORTED (-2).
     */
    fun checkLanguageAvailabilityCode(langCode: String): Int {
        val engine = tts ?: return TextToSpeech.LANG_NOT_SUPPORTED
        val locale = getLocaleForLanguage(langCode)
        return try {
            engine.isLanguageAvailable(locale)
        } catch (_: Exception) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun isLanguageAvailable(langCode: String): Boolean {
        val code = checkLanguageAvailabilityCode(langCode)
        return code >= TextToSpeech.LANG_AVAILABLE
    }

    override fun speak(
        text: String,
        langCode: String,
        onFallback: ((originalLang: String, fallbackLang: String) -> Unit)?,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?,
    ) {
        runWhenReady {
            postOnMain {
                try {
                    val engine = tts
                    if (engine == null) {
                        onError?.invoke("TTS engine not available")
                        return@postOnMain
                    }

                    val normalized = normalizeLang(langCode)
                    val requestedLocale = getLocaleForLanguage(normalized)
                    val availCode = try {
                        engine.isLanguageAvailable(requestedLocale)
                    } catch (_: Exception) {
                        TextToSpeech.LANG_NOT_SUPPORTED
                    }

                    val finalLocale = if (availCode < TextToSpeech.LANG_AVAILABLE) {
                        // Fall back to Hindi per i18n plan (especially for Sanskrit 'sa')
                        val fallbackLocale = Locale("hi", "IN")
                        val fallbackCode = try {
                            engine.isLanguageAvailable(fallbackLocale)
                        } catch (_: Exception) {
                            TextToSpeech.LANG_NOT_SUPPORTED
                        }

                        val target = if (fallbackCode >= TextToSpeech.LANG_AVAILABLE) {
                            fallbackLocale
                        } else {
                            Locale.ENGLISH
                        }

                        onFallback?.invoke(normalized, if (target == fallbackLocale) "hi" else "en")
                        target
                    } else {
                        requestedLocale
                    }

                    engine.language = finalLocale

                    val utteranceId = UUID.randomUUID().toString()
                    currentUtteranceId = utteranceId
                    currentOnStart = onStart
                    currentOnDone = onDone
                    currentOnError = onError

                    val params = Bundle().apply {
                        putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                    }

                    engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                } catch (e: Exception) {
                    Log.e("AndroidTextToSpeech", "Failed to speak text", e)
                    onError?.invoke(e.localizedMessage ?: "TTS failed")
                }
            }
        }
    }

    override fun stop() {
        postOnMain {
            try {
                tts?.stop()
                clearCallbacks()
            } catch (_: Exception) {}
        }
    }

    private fun clearCallbacks() {
        currentUtteranceId = null
        currentOnStart = null
        currentOnDone = null
        currentOnError = null
    }

    override fun shutdown() {
        postOnMain {
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (_: Exception) {}
            tts = null
            isInitialized = false
        }
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
}
