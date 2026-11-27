package com.kissansevaai.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Enforces strict cloud HTTPS configuration in release builds while preserving
 * local emulator/ADB/LAN development flexibility in debug builds.
 */
object CloudUrlValidator {

    private val private172Regex = Regex("""^172\.(1[6-9]|2\d|3[0-1])\.\d{1,3}\.\d{1,3}$""")
    private val ipv410Regex = Regex("""^10\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")

    fun isLocalOrPrivateHost(host: String): Boolean {
        val h = host.trim().lowercase().removePrefix("[").removeSuffix("]")
        if (h.isEmpty()) return true
        return h == "localhost" ||
            h.endsWith(".localhost") ||
            h.endsWith(".local") ||
            h == "0.0.0.0" ||
            h == "::1" ||
            h.startsWith("127.") ||
            h.startsWith("192.168.") ||
            ipv410Regex.matches(h) ||
            private172Regex.matches(h)
    }

    fun requireValidProductionUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        val parsed = trimmed.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid production API URL: '$rawUrl'")
        require(parsed.scheme == "https") {
            "Production API URL must use HTTPS (got '${parsed.scheme}')"
        }
        require(!isLocalOrPrivateHost(parsed.host)) {
            "Production API URL must not point to local or private host '${parsed.host}'"
        }
        return "$trimmed/"
    }

    fun resolveEffectiveBaseUrl(
        isDebug: Boolean,
        buildConfigBaseUrl: String,
        storedCustomUrl: String?,
        isEmulator: Boolean,
    ): String {
        if (!isDebug) {
            return requireValidProductionUrl(buildConfigBaseUrl)
        }
        val candidate = storedCustomUrl?.trim()?.takeIf { it.isNotEmpty() } ?: run {
            if (isEmulator) {
                "http://10.0.2.2:5050"
            } else {
                // Default physical USB debugging via adb reverse tcp:5050 tcp:5050; falls back fast to LAN
                "http://127.0.0.1:5050"
            }
        }
        return if (candidate.endsWith("/")) candidate else "$candidate/"
    }

    fun shouldAttachDevHostFallback(isDebug: Boolean): Boolean = isDebug

    fun resolveInterceptorOverrideUrl(
        isDebug: Boolean,
        storedCustomUrl: String?,
        currentRequestUrl: HttpUrl,
    ): HttpUrl? {
        if (!isDebug) return null
        val custom = storedCustomUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val target = custom.toHttpUrlOrNull() ?: return null
        return currentRequestUrl.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .build()
    }
}
