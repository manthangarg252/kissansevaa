package com.kissansevaai

import com.kissansevaai.core.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * Parsing tests verifying that all @Serializable data classes
 * correctly deserialize the JSON fixtures from src/test/resources.
 */
class ModelParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private fun loadFixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

    @Test
    fun `parse auth response`() {
        val raw = loadFixture("auth_response.json")
        val result = json.decodeFromString<AuthResponse>(raw)
        assertEquals("Live Test Farmer", result.name)
        assertEquals("livefarmer@kissansevaai.com", result.email)
        assertNotNull(result.token)
        assertNotNull(result.id)
        assertTrue(result.userId.isNotEmpty())
    }

    @Test
    fun `parse crop analysis result`() {
        val raw = loadFixture("crop_analysis.json")
        val result = json.decodeFromString<CropAnalysisResult>(raw)
        assertEquals("Early Blight", result.diseaseName)
        assertEquals("Tomato", result.cropName)
        assertEquals("medium", result.severity)
        assertEquals("gemini", result.source)
        assertEquals(0.94, result.confidence!!, 0.01)
        assertTrue(result.treatment.organic.isNotEmpty())
        assertTrue(result.treatment.chemical.isNotEmpty())
        assertTrue(result.prevention.isNotEmpty())
        assertNotNull(result.summary)
    }

    @Test
    fun `parse livestock result`() {
        val raw = loadFixture("livestock_analysis.json")
        val result = json.decodeFromString<LivestockResult>(raw)
        assertEquals("Foot and Mouth Disease", result.conditionName)
        assertEquals(88.0, result.confidence!!, 0.01)
        assertEquals(4, result.symptoms.size)
        assertNotNull(result.immediateAction)
        assertNotNull(result.longTermCare)
        assertNotNull(result.vetConsultationAdvice)
    }

    @Test
    fun `parse livestock result with numeric confidence and mock source`() {
        val jsonStr = """{"conditionName": "Bovine Dermatitis", "confidence": 0.91, "source": "mock"}"""
        val result = json.decodeFromString<LivestockResult>(jsonStr)
        assertEquals("Bovine Dermatitis", result.conditionName)
        assertEquals(0.91, result.confidence!!, 0.01)
        assertEquals("mock", result.source)
    }

    @Test
    fun `parse market prices list`() {
        val raw = loadFixture("market_prices.json")
        val result = json.decodeFromString<List<MarketPrice>>(raw)
        assertEquals(6, result.size)
        val wheat = result[0]
        assertEquals("Wheat", wheat.crop)
        assertEquals(2300.0, wheat.effectivePrice, 0.01)
        assertEquals("Nagpur APMC", wheat.market)
        assertEquals("Maharashtra", wheat.state)
    }

    @Test
    fun `parse traders list`() {
        val raw = loadFixture("traders.json")
        val result = json.decodeFromString<List<Trader>>(raw)
        assertEquals(2, result.size)
        val first = result[0]
        assertEquals("Punjab Agro Commodities", first.name)
        assertEquals(4.8, first.rating, 0.01)
        assertTrue(first.dealsIn.contains("Wheat"))
    }

    @Test
    fun `parse IoT sensor data`() {
        val raw = loadFixture("iot_live.json")
        val result = json.decodeFromString<SensorData>(raw)
        assertEquals(28.5, result.temperature!!, 0.01)
        assertEquals(65.0, result.humidity!!, 0.01)
        assertEquals(42.0, result.soilMoisture!!, 0.01)
        assertEquals(6.8, result.phLevel!!, 0.01)
    }

    @Test
    fun `parse text response`() {
        val raw = loadFixture("text_response.json")
        val result = json.decodeFromString<TextResponse>(raw)
        assertTrue(result.response.contains("farm profile"))
    }

    @Test
    fun `parse field extraction response`() {
        val raw = loadFixture("field_extraction.json")
        val result = json.decodeFromString<FieldExtractionResponse>(raw)
        assertEquals("Punjab", result.extractedFields["location"])
        assertEquals("Loamy", result.extractedFields["soilType"])
        assertEquals("Wheat", result.extractedFields["cropName"])
    }

    @Test
    fun `parse empty or missing fields gracefully`() {
        // Minimal crop analysis with only required field
        val minimal = """{"diseaseName": "Unknown"}"""
        val result = json.decodeFromString<CropAnalysisResult>(minimal)
        assertEquals("Unknown", result.diseaseName)
        assertNull(result.cropName)
        assertNull(result.severity)
        assertEquals(emptyList<String>(), result.prevention)
    }
}
