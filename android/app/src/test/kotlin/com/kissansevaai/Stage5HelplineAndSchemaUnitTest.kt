package com.kissansevaai

import com.kissansevaai.core.common.SarvamHelplineConfig
import com.kissansevaai.core.model.LivestockResult
import com.kissansevaai.core.model.SecondOpinionResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class Stage5HelplineAndSchemaUnitTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `sarvam helpline validator accepts valid country-coded number and rejects blank or invalid`() {
        val valid = SarvamHelplineConfig.resolve("+917965861355")
        assertTrue(valid.isConfigured)
        assertEquals("+917965861355", valid.displayNumber)
        assertEquals("tel:+917965861355", valid.dialUriString)
        assertNull(valid.validationError)

        val formatted = SarvamHelplineConfig.resolve("+91 79658-61355")
        assertTrue(formatted.isConfigured)
        assertEquals("tel:+917965861355", formatted.dialUriString)

        val blank = SarvamHelplineConfig.resolve("")
        assertFalse(blank.isConfigured)
        assertNull(blank.dialUriString)
        assertNotNull(blank.validationError)

        val noCountryCode = SarvamHelplineConfig.resolve("7965861355")
        assertFalse(noCountryCode.isConfigured)
        assertNull(noCountryCode.dialUriString)

        val badLen = SarvamHelplineConfig.resolve("+9112345")
        assertFalse(badLen.isConfigured)
    }

    @Test
    fun `build config contains configured SARVAM_HELPLINE_NUMBER matching gradle configuration`() {
        val resolved = SarvamHelplineConfig.resolve(BuildConfig.SARVAM_HELPLINE_NUMBER)
        assertTrue("Expected configured SARVAM_HELPLINE_NUMBER in BuildConfig", resolved.isConfigured)
        assertEquals("+917965861355", resolved.displayNumber)
    }

    @Test
    fun `livestock and crop second opinion DTOs parse rich stage 5 fields cleanly`() {
        val livestockPayload = """
            {
              "_id": "live_s5_1",
              "animalType": "Buffalo",
              "conditionName": "Suspected Hemorrhagic Septicemia / Acute Fever",
              "confidence": 0.68,
              "severity": "High",
              "urgentVetEscalation": true,
              "escalationReason": "Fever accompanied by appetite loss or sudden milk drop",
              "prescriptionsWithheld": true,
              "visibleObservations": ["Drooping ears", "Nasal discharge"],
              "possibleConcerns": ["Bacterial fever", "Heat stress"],
              "missingInformation": ["Exact rectal temperature"],
              "warningSigns": ["Open-mouth breathing", "Sudden collapse"],
              "veterinaryUrgency": "Emergency - Immediate Vet / 1962",
              "uncertaintyNote": "Screening aid only; BVSc examination required."
            }
        """.trimIndent()
        val parsedLive = json.decodeFromString<LivestockResult>(livestockPayload)
        assertEquals("Emergency - Immediate Vet / 1962", parsedLive.veterinaryUrgency)
        assertEquals(2, parsedLive.visibleObservations.size)
        assertEquals(2, parsedLive.possibleConcerns.size)
        assertEquals(1, parsedLive.missingInformation.size)
        assertEquals(2, parsedLive.warningSigns.size)
        assertTrue(parsedLive.prescriptionsWithheld)
        assertNotNull(parsedLive.uncertaintyNote)

        val cropSecPayload = """
            {
              "ok": true,
              "aiAvailable": true,
              "primaryPreserved": true,
              "reconciliationStatus": "inconclusive",
              "reconciliationNote": "Visual screening unclear; treatments withheld.",
              "treatmentWithheld": true,
              "secondOpinion": {
                "requested": true,
                "farmerConsented": true,
                "source": "gemini",
                "cropName": "Apple",
                "diseaseName": "Inconclusive Visual Screening",
                "confidence": 0.45
              }
            }
        """.trimIndent()
        val parsedSec = json.decodeFromString<SecondOpinionResponse>(cropSecPayload)
        assertEquals("inconclusive", parsedSec.reconciliationStatus)
        assertTrue(parsedSec.treatmentWithheld)
        assertEquals("Apple", parsedSec.secondOpinion?.cropName)
    }
}
