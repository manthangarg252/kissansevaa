package com.kissansevaai

import android.content.Context
import com.kissansevaai.core.ai.ondevice.AndroidTextToSpeech
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Locale

class TextToSpeechTest {

    private lateinit var context: Context
    private lateinit var tts: AndroidTextToSpeech

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        tts = AndroidTextToSpeech(context)
    }

    @Test
    fun `getLocaleForLanguage maps languages correctly`() {
        assertEquals(Locale("en", "IN"), tts.getLocaleForLanguage("en"))
        assertEquals(Locale("hi", "IN"), tts.getLocaleForLanguage("hi"))
        assertEquals(Locale("mr", "IN"), tts.getLocaleForLanguage("mr"))
        assertEquals(Locale("pa", "IN"), tts.getLocaleForLanguage("pa"))
        
        // Sanskrit correctly maps to sa-IN
        assertEquals(Locale("sa", "IN"), tts.getLocaleForLanguage("sa"))
    }
    
    @Test
    fun `Sanskrit TTS falls back to Hindi and triggers onFallback callback`() {
        mockkConstructor(android.speech.tts.TextToSpeech::class)
        every { anyConstructed<android.speech.tts.TextToSpeech>().isLanguageAvailable(Locale("sa", "IN")) } returns android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
        every { anyConstructed<android.speech.tts.TextToSpeech>().isLanguageAvailable(Locale("hi", "IN")) } returns android.speech.tts.TextToSpeech.LANG_AVAILABLE
        every { anyConstructed<android.speech.tts.TextToSpeech>().speak(any(), any(), any(), any()) } returns android.speech.tts.TextToSpeech.SUCCESS

        val ttsInstance = AndroidTextToSpeech(context)
        ttsInstance.onInit(android.speech.tts.TextToSpeech.SUCCESS)

        var fallbackFired = false
        var originalLangReceived: String? = null
        var fallbackLangReceived: String? = null

        ttsInstance.speak(
            text = "कृषि सेवा",
            langCode = "sa",
            onFallback = { orig, fb ->
                fallbackFired = true
                originalLangReceived = orig
                fallbackLangReceived = fb
            }
        )

        assertTrue(fallbackFired)
        assertEquals("sa", originalLangReceived)
        assertEquals("hi", fallbackLangReceived)
    }
}
