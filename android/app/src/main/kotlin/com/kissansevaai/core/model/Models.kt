package com.kissansevaai.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

// ── Auth ──

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val name: String, val email: String, val password: String)

@Serializable
data class AuthResponse(
    val id: String? = null,
    @SerialName("_id") val mongoId: String? = null,
    val name: String,
    val email: String,
    val token: String,
) {
    val userId: String
        get() = id ?: mongoId ?: ""
}

@Serializable
data class UserProfile(
    @SerialName("_id") val id: String,
    val name: String,
    val email: String,
    val phone: String? = null,
    val languagePreference: String = "en",
)

// ── Crop Disease ──

@Serializable
data class CropTreatment(
    val organic: List<String> = emptyList(),
    val chemical: List<String> = emptyList(),
)

@Serializable
data class CropAnalysisResult(
    @SerialName("_id") val id: String? = null,
    val source: String? = null,
    val cropName: String? = null,
    val diseaseName: String = "",
    val confidence: Double? = null,
    val severity: String? = null,
    val summary: String? = null,
    val treatment: CropTreatment = CropTreatment(),
    val prevention: List<String> = emptyList(),
    val language: String? = null,
    val imagePath: String? = null,
    val createdAt: String? = null,
)

object DoubleFlexibleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DoubleFlexible", PrimitiveKind.DOUBLE)
    override fun serialize(encoder: Encoder, value: Double?) {
        if (value != null) encoder.encodeDouble(value) else encoder.encodeNull()
    }
    override fun deserialize(decoder: Decoder): Double? {
        val jsonDecoder = decoder as? JsonDecoder ?: return try { decoder.decodeDouble() } catch (_: Exception) { null }
        val elem = jsonDecoder.decodeJsonElement()
        return when {
            elem is JsonPrimitive && elem.isString -> elem.content.replace("%", "").trim().toDoubleOrNull()
            elem is JsonPrimitive -> elem.doubleOrNull
            else -> null
        }
    }
}

// ── Livestock ──

@Serializable
data class LivestockResult(
    val conditionName: String? = null,
    @Serializable(with = DoubleFlexibleSerializer::class) val confidence: Double? = null,
    val source: String? = null,
    val symptoms: List<String> = emptyList(),
    val immediateAction: String? = null,
    val longTermCare: String? = null,
    val vetConsultationAdvice: String? = null,
    val imageUrl: String? = null,
)

// ── Chat ──

@Serializable
data class ChatRequest(
    val message: String,
    val language: String = "en",
    val mode: String? = null,
)

@Serializable
data class ChatResponse(val response: String)

// ── Market ──

@Serializable
data class MarketPrice(
    @SerialName("_id") val id: String? = null,
    val crop: String = "",
    val price: Double = 0.0,
    val modalPrice: Double? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val unit: String = "quintal",
    val market: String = "",
    val state: String = "",
    val trend: String = "stable",
    val updatedAt: String? = null,
    val lastUpdated: String? = null,
) {
    val effectivePrice: Double
        get() = if (price > 0.0) price else (modalPrice ?: 0.0)
}

@Serializable
data class MarketRecommendationRequest(
    val location: String,
    val soilType: String,
    val farmSize: String,
    val currentCrop: String,
    val irrigation: String,
    val language: String = "en",
)

// ── Schemes ──

@Serializable
data class Scheme(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val description: String = "",
    val eligibility: List<String> = emptyList(),
    val benefits: String = "",
    val link: String? = null,
    val phone: String? = null,
)

@Serializable
data class SchemeRecommendationRequest(
    val state: String,
    val farmSize: String,
    val crop: String,
    val soilType: String,
    val landType: String,
    val language: String = "en",
)

// ── Loans ──

@Serializable
data class LoanProduct(
    val id: String = "",
    val name: String = "",
    val type: String = "Govt",
    val description: String = "",
    val benefits: List<String> = emptyList(),
    val eligibility: List<String> = emptyList(),
    val link: String? = null,
    val provider: String = "",
    val interestRate: String = "",
    val maxAmount: String = "",
    val tenure: String = "",
    val features: List<String> = emptyList(),
)

@Serializable
data class LoanAdvisorRequest(
    val profile: LoanProfile,
    val language: String = "en",
)

@Serializable
data class LoanProfile(
    val name: String = "",
    val age: String = "",
    val farmSize: String = "",
    val annualIncome: String = "",
    val existingLoans: String = "",
    val creditScore: String = "",
    val cropType: String = "",
    val state: String = "",
)

@Serializable
data class LoanAdvisorResult(
    val recommendedLoan: String? = null,
    val emiEstimate: String? = null,
    val riskAdvisory: String? = null,
    val response: String? = null,
)

// ── Carbon ──

@Serializable
data class CarbonEstimateRequest(
    val farmSize: String,
    val cropType: String,
    val method: String,
    val irrigation: String,
    val language: String = "en",
)

// ── Traders ──

@Serializable
data class Trader(
    @SerialName("_id") val id: String? = null,
    val name: String,
    val location: String,
    val dealsIn: List<String> = emptyList(),
    val phone: String,
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val imageUrl: String? = null,
)

@Serializable
data class TraderAdvisoryRequest(val query: String, val language: String = "en")

// ── IoT ──

@Serializable
data class SensorData(
    val temperature: Double? = null,
    val humidity: Double? = null,
    val soilMoisture: Double? = null,
    val phLevel: Double? = null,
    val timestamp: String? = null,
)

@Serializable
data class IoTInsightsRequest(val sensorData: SensorData, val language: String = "en")

// ── Field Extraction ──

@Serializable
data class FieldExtractionRequest(
    val text: String,
    val fields: List<String>,
    val language: String = "en",
)

@Serializable
data class FieldExtractionResponse(
    val extractedFields: Map<String, String?> = emptyMap(),
)

// ── Generic text response (used by advisory, carbon, chat endpoints) ──

@Serializable
data class TextResponse(val response: String)

@Serializable
data class StatusResponse(val status: String)

@Serializable
data class ErrorResponse(val error: String)
