package com.kissansevaai

import android.content.Context
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kissansevaai.core.ai.ondevice.AndroidSpeechToText
import com.kissansevaai.core.ai.ondevice.AndroidTextToSpeech
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PlatformVoiceDiagnosticTest {

    @Test
    fun reportRealPlatformVoiceSupport() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. STT Checks
        val isSttAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        println("=== STT DIAGNOSTIC REPORT ===")
        println("SpeechRecognizer.isRecognitionAvailable(context): $isSttAvailable")

        val stt = AndroidSpeechToText(context)
        val languages = listOf("en", "hi", "mr", "pa", "sa")
        for (lang in languages) {
            val supported = stt.isLanguageSupported(lang)
            val reason = stt.getUnsupportedReason(lang)
            println("STT isLanguageSupported('$lang'): $supported (reason: $reason)")
        }

        // 2. TTS Checks
        println("=== TTS DIAGNOSTIC REPORT ===")
        val latch = CountDownLatch(1)
        var ttsEngine: TextToSpeech? = null
        var initStatus = -999

        ttsEngine = TextToSpeech(context) { status ->
            initStatus = status
            latch.countDown()
        }

        latch.await(5, TimeUnit.SECONDS)
        println("TextToSpeech onInit status: $initStatus (SUCCESS=${TextToSpeech.SUCCESS})")

        val locales = mapOf(
            "en" to Locale("en", "IN"),
            "hi" to Locale("hi", "IN"),
            "mr" to Locale("mr", "IN"),
            "pa" to Locale("pa", "IN"),
            "sa" to Locale("sa", "IN")
        )

        for ((code, loc) in locales) {
            val availCode = ttsEngine?.isLanguageAvailable(loc) ?: -999
            val availStr = when (availCode) {
                TextToSpeech.LANG_AVAILABLE -> "LANG_AVAILABLE (0)"
                TextToSpeech.LANG_COUNTRY_AVAILABLE -> "LANG_COUNTRY_AVAILABLE (1)"
                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "LANG_COUNTRY_VAR_AVAILABLE (2)"
                TextToSpeech.LANG_MISSING_DATA -> "LANG_MISSING_DATA (-1)"
                TextToSpeech.LANG_NOT_SUPPORTED -> "LANG_NOT_SUPPORTED (-2)"
                else -> "UNKNOWN ($availCode)"
            }
            val isAvailableBool = availCode >= TextToSpeech.LANG_AVAILABLE
            println("TTS isLanguageAvailable('$code' / $loc): $availStr (Boolean: $isAvailableBool)")
        }

        ttsEngine?.shutdown()
        println("=== END OF VOICE DIAGNOSTIC REPORT ===")
    }
}
