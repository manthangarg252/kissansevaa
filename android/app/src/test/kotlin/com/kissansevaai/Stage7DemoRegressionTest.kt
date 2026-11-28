package com.kissansevaai

import com.kissansevaai.core.model.MarketPrice
import com.kissansevaai.core.model.Scheme
import com.kissansevaai.core.model.TaskStatusUpdateRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage7DemoRegressionTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `MarketPrice parses arrivalDate for mandi observation date display`() {
        val raw = """
            {"_id":"agm_1","crop":"Wheat","price":2300,"market":"Nagpur APMC","state":"Maharashtra","arrivalDate":"27/11/2026"}
        """.trimIndent()
        val parsed = json.decodeFromString<MarketPrice>(raw)
        assertEquals("27/11/2026", parsed.arrivalDate)
        assertEquals("Nagpur APMC", parsed.market)
    }

    @Test
    fun `Scheme parses Hindi fields cleanly for Hindi UI display`() {
        val raw = """
            {"id":"pm-kisan","name":"PM-KISAN","nameHi":"पीएम-किसान सम्मान निधि","category":"Income Support","categoryHi":"आय सहायता","description":"Support","descriptionHi":"वार्षिक आय सहायता","benefits":"₹6000","benefitsHi":"₹6,000 प्रति वर्ष"}
        """.trimIndent()
        val parsed = json.decodeFromString<Scheme>(raw)
        assertEquals("पीएम-किसान सम्मान निधि", parsed.nameHi)
        assertEquals("₹6,000 प्रति वर्ष", parsed.benefitsHi)
    }

    @Test
    fun `TaskStatusUpdateRequest serializes task edit fields cleanly`() {
        val req = TaskStatusUpdateRequest(
            status = null,
            reminderEnabled = false,
            title = "Edited Task",
            description = "Check irrigation",
            scheduledDate = "2026-11-29",
        )
        val encoded = json.encodeToString(req)
        assertTrue(encoded.contains("Edited Task"))
        assertTrue(encoded.contains("2026-11-29"))
        assertTrue(encoded.contains("\"reminderEnabled\":false"))
    }
}
