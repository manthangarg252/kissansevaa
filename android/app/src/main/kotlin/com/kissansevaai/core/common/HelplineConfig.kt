package com.kissansevaai.core.common

import com.kissansevaai.BuildConfig

data class HelplineStatus(
    val isConfigured: Boolean,
    val displayNumber: String,
    val dialNumber: String,
    val dialUriString: String?,
    val validationError: String? = null,
)

/**
 * Validates the project-configured SARVAM_HELPLINE_NUMBER without hardcoding or inventing numbers.
 * Enforces an explicit international country code prefix (e.g. +91 followed by 10 digits)
 * or 10-15 total digits in E.164 format.
 */
object SarvamHelplineConfig {
    private val E164_REGEX = Regex("^\\+[1-9]\\d{9,14}$")

    fun resolve(rawNumber: String = BuildConfig.SARVAM_HELPLINE_NUMBER): HelplineStatus {
        val trimmed = rawNumber.trim()
        if (trimmed.isEmpty()) {
            return HelplineStatus(
                isConfigured = false,
                displayNumber = "",
                dialNumber = "",
                dialUriString = null,
                validationError = "SARVAM_HELPLINE_NUMBER is not configured in project settings.",
            )
        }

        val normalized = trimmed.replace(Regex("[\\s\\-().]"), "")
        if (!E164_REGEX.matches(normalized)) {
            return HelplineStatus(
                isConfigured = false,
                displayNumber = trimmed,
                dialNumber = "",
                dialUriString = null,
                validationError = "Invalid helpline number format. Include country code (e.g. +91XXXXXXXXXX).",
            )
        }

        if (normalized.startsWith("+91") && normalized.length != 13) {
            return HelplineStatus(
                isConfigured = false,
                displayNumber = trimmed,
                dialNumber = "",
                dialUriString = null,
                validationError = "Indian helpline numbers with +91 must contain 10 digits after +91.",
            )
        }

        return HelplineStatus(
            isConfigured = true,
            displayNumber = normalized,
            dialNumber = normalized,
            dialUriString = "tel:$normalized",
            validationError = null,
        )
    }
}
