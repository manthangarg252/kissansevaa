package com.kissansevaai.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage6CloudConfigTest {

    @Test
    fun `release build uses https cloud url and ignores any stored localhost or LAN override`() {
        val resolved = CloudUrlValidator.resolveEffectiveBaseUrl(
            isDebug = false,
            buildConfigBaseUrl = "https://api.kissansevaai.example.com",
            storedCustomUrl = "http://127.0.0.1:5050",
            isEmulator = true,
        )
        assertEquals("https://api.kissansevaai.example.com/", resolved)
    }

    @Test
    fun `release build disables dev host fallback and interceptor overrides`() {
        assertFalse(CloudUrlValidator.shouldAttachDevHostFallback(isDebug = false))
        assertTrue(CloudUrlValidator.shouldAttachDevHostFallback(isDebug = true))

        val reqUrl = "https://api.kissansevaai.example.com/api/weather/current".toHttpUrl()
        assertNull(
            CloudUrlValidator.resolveInterceptorOverrideUrl(
                isDebug = false,
                storedCustomUrl = "http://192.168.1.7:5050",
                currentRequestUrl = reqUrl,
            )
        )
    }

    @Test
    fun `release validator rejects http, localhost, emulator, and private lan addresses`() {
        val invalidUrls = listOf(
            "http://api.kissansevaai.example.com",
            "https://localhost:8443",
            "https://127.0.0.1",
            "https://10.0.2.2:5050",
            "https://192.168.1.7:5050",
            "https://10.201.214.248",
            "https://172.16.0.10",
            "",
        )
        for (candidate in invalidUrls) {
            var thrown = false
            try {
                CloudUrlValidator.requireValidProductionUrl(candidate)
            } catch (_: IllegalArgumentException) {
                thrown = true
            }
            assertTrue("Expected rejection for $candidate", thrown)
        }
    }

    @Test
    fun `debug build preserves emulator and usb defaults plus custom override`() {
        assertEquals(
            "http://10.0.2.2:5050/",
            CloudUrlValidator.resolveEffectiveBaseUrl(
                isDebug = true,
                buildConfigBaseUrl = "http://10.0.2.2:5050",
                storedCustomUrl = null,
                isEmulator = true,
            ),
        )
        assertEquals(
            "http://127.0.0.1:5050/",
            CloudUrlValidator.resolveEffectiveBaseUrl(
                isDebug = true,
                buildConfigBaseUrl = "http://10.0.2.2:5050",
                storedCustomUrl = null,
                isEmulator = false,
            ),
        )
        val rewritten = CloudUrlValidator.resolveInterceptorOverrideUrl(
            isDebug = true,
            storedCustomUrl = "http://192.168.1.7:5050",
            currentRequestUrl = "http://10.0.2.2:5050/api/crop/analyze".toHttpUrl(),
        )
        assertEquals("http://192.168.1.7:5050/api/crop/analyze", rewritten.toString())
    }
}
