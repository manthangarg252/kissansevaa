package com.kissansevaai

import android.content.Context
import com.kissansevaai.core.ai.ondevice.AndroidSpeechToText
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SpeechToTextTest {

    private lateinit var context: Context
    private lateinit var stt: AndroidSpeechToText

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockkStatic(android.speech.SpeechRecognizer::class)
        every { android.speech.SpeechRecognizer.isRecognitionAvailable(any()) } returns true
        stt = AndroidSpeechToText(context)
    }

    @Test
    fun `isRecognitionAvailable returns true when platform supports it`() {
        assertTrue(stt.isRecognitionAvailable)
    }

    @Test
    fun `isLanguageSupported returns true for English, Hindi, Marathi, Punjabi`() {
        assertTrue(stt.isLanguageSupported("en"))
        assertTrue(stt.isLanguageSupported("hi"))
        assertTrue(stt.isLanguageSupported("mr"))
        assertTrue(stt.isLanguageSupported("pa"))
    }

    @Test
    fun `isLanguageSupported explicitly rejects Sanskrit (sa)`() {
        assertFalse(stt.isLanguageSupported("sa"))
        assertFalse(stt.isLanguageSupported("sa-IN"))
    }

    @Test
    fun `getUnsupportedReason returns meaningful explanation for Sanskrit`() {
        val reason = stt.getUnsupportedReason("sa")
        assertNotNull(reason)
        assertTrue(reason!!.contains("Sanskrit", ignoreCase = true))
        assertTrue(reason.contains("text input", ignoreCase = true))
    }

    @Test
    fun `getUnsupportedReason returns null for supported languages`() {
        assertNull(stt.getUnsupportedReason("en"))
        assertNull(stt.getUnsupportedReason("hi"))
        assertNull(stt.getUnsupportedReason("mr"))
        assertNull(stt.getUnsupportedReason("pa"))
    }

    @Test
    fun `startListening creates SpeechRecognizer, attaches RecognitionListener, and calls startListening`() {
        val mockRecognizer = mockk<android.speech.SpeechRecognizer>(relaxed = true)
        every { android.speech.SpeechRecognizer.createSpeechRecognizer(any()) } returns mockRecognizer

        stt.startListening(
            langCode = "en",
            onResult = {},
            onError = {}
        )

        io.mockk.verify {
            android.speech.SpeechRecognizer.createSpeechRecognizer(context)
            mockRecognizer.setRecognitionListener(any())
            mockRecognizer.startListening(any())
        }
    }

}
