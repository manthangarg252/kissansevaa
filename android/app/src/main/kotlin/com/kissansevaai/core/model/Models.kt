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

// ── Auth & Farm Profile ──

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
    val state: String = "",
    val district: String = "",
    val soilType: String = "",
    val irrigationType: String = "",
    val farmSizeAcres: Double? = null,
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
    val farmName: String = "",
    val state: String = "",
    val district: String = "",
    val city: String = "",
    val village: String = "",
    val country: String = "",
    val postalCode: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationSource: String = "",
    val locationUpdatedAt: String? = null,
    val farmSizeAcres: Double? = null,
    val soilType: String = "",
    val irrigationType: String = "",
    val primaryCrops: List<String> = emptyList(),
)

@Serializable
data class FarmProfileUpdateRequest(
    val name: String? = null,
    val phone: String? = null,
    val languagePreference: String? = null,
    val farmName: String? = null,
    val state: String? = null,
    val district: String? = null,
    val city: String? = null,
    val village: String? = null,
    val country: String? = null,
    val postalCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationSource: String? = null,
    val locationUpdatedAt: String? = null,
    val farmSizeAcres: Double? = null,
    val soilType: String? = null,
    val irrigationType: String? = null,
    val primaryCrops: List<String>? = null,
)

// ── Crop Disease ──

@Serializable
data class CropTreatment(
    val organic: List<String> = emptyList(),
    val chemical: List<String> = emptyList(),
)

@Serializable
data class CropMatch(
    val class_name: String = "",
    val cosine_similarity: Double = 0.0,
)

@Serializable
data class SecondOpinionResult(
    val requested: Boolean = false,
    val farmerConsented: Boolean = false,
    val source: String? = null,
    val cropName: String? = null,
    val diseaseName: String? = null,
    val confidence: Double? = null,
    val isPlantLeaf: Boolean = true,
    val summary: String? = null,
    val severity: String? = null,
    val treatment: CropTreatment = CropTreatment(),
    val prevention: List<String> = emptyList(),
    val warning: String? = null,
    val requestId: String? = null,
    val serviceStatus: String? = null,
)

@Serializable
data class SecondOpinionResponse(
    val ok: Boolean = false,
    val aiAvailable: Boolean = false,
    val analysisId: String? = null,
    val primaryPreserved: Boolean = true,
    val primarySource: String? = null,
    val primaryCrop: String? = null,
    val primaryDisease: String? = null,
    val secondOpinion: SecondOpinionResult? = null,
    val reconciliationStatus: String? = null,
    val reconciliationNote: String? = null,
    val treatmentWithheld: Boolean = true,
    val error: String? = null,
    val serviceStatus: String? = null,
)

@Serializable
data class CropAnalysisResult(
    @SerialName("_id") val id: String? = null,
    val source: String? = null,
    val predictionSource: String? = null,
    val advisorySource: String? = null,
    val cropName: String? = null,
    val diseaseName: String = "",
    val confidence: Double? = null,
    val top_cosine_similarity: Double? = null,
    val neighbor_agreement: Double? = null,
    val needs_review: Boolean = false,
    val treatmentWithheld: Boolean = false,
    val secondOpinionEligible: Boolean = false,
    val matches: List<CropMatch> = emptyList(),
    val warning: String? = null,
    val confidence_type: String? = null,
    val severity: String? = null,
    val summary: String? = null,
    val treatment: CropTreatment = CropTreatment(),
    val prevention: List<String> = emptyList(),
    val language: String? = null,
    val imagePath: String? = null,
    val cropHint: String? = null,
    val symptomDescription: String? = null,
    val createdAt: String? = null,
    val secondOpinion: SecondOpinionResult? = null,
    val reconciliationStatus: String? = null,
    val reconciliationNote: String? = null,
    val aiAvailable: Boolean? = null,
    val serviceStatus: String? = null,
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
    @SerialName("_id") val id: String? = null,
    val animalType: String? = null,
    val conditionName: String? = null,
    @Serializable(with = DoubleFlexibleSerializer::class) val confidence: Double? = null,
    val source: String? = null,
    val severity: String? = null,
    val urgentVetEscalation: Boolean = false,
    val escalationReason: String? = null,
    val prescriptionsWithheld: Boolean = true,
    val safetyNotice: String? = null,
    val symptoms: List<String> = emptyList(),
    val immediateAction: String? = null,
    val longTermCare: String? = null,
    val vetConsultationAdvice: String? = null,
    val imageUrl: String? = null,
    val aiAvailable: Boolean = true,
    val serviceStatus: String? = null,
    val requestId: String? = null,
    val createdAt: String? = null,
    val error: String? = null,
)

data class LivestockIntakeFields(
    val animalType: String = "Cattle",
    val animalAge: String = "",
    val symptomDuration: String = "",
    val feverStatus: String = "unknown",
    val appetiteStatus: String = "unknown",
    val milkYieldChange: String = "unknown",
    val lesionsOrUlcers: Boolean = false,
    val isolatedFromHerd: Boolean = false,
    val symptomsNotes: String = "",
)

// ── Weather ──

@Serializable
data class WeatherCurrent(
    val temperatureC: Double = 0.0,
    val feelsLikeC: Double = 0.0,
    val humidityPct: Double = 0.0,
    val windSpeedKmh: Double = 0.0,
    val precipitationMm: Double = 0.0,
    val weatherCode: Int = 0,
    val conditionLabel: String = "",
)

@Serializable
data class WeatherDaily(
    val date: String = "",
    val tempMaxC: Double = 0.0,
    val tempMinC: Double = 0.0,
    val precipitationMm: Double = 0.0,
    val precipitationProbPct: Double = 0.0,
    val windMaxKmh: Double = 0.0,
    val weatherCode: Int = 0,
    val conditionLabel: String = "",
)

@Serializable
data class WeatherAdvisory(
    val id: String = "",
    val category: String = "",
    val severity: String = "info",
    val sprayWindowSuitable: Boolean? = null,
    val title: String = "",
    val message: String = "",
)

@Serializable
data class WeatherForecast(
    val locationKey: String = "",
    val locationName: String = "",
    val state: String = "",
    val district: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val provider: String = "open-meteo",
    val current: WeatherCurrent = WeatherCurrent(),
    val daily: List<WeatherDaily> = emptyList(),
    val advisories: List<WeatherAdvisory> = emptyList(),
    val sprayWindowSuitable: Boolean = true,
    val aiSummary: String? = null,
    val aiAvailable: Boolean = false,
    val aiServiceStatus: String? = null,
    val fetchedAt: String = "",
    val expiresAt: String = "",
    val source: String = "live",
    val isStale: Boolean = false,
    val staleReason: String? = null,
)

@Serializable
data class WeatherAlertRequest(
    val location: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val crop: String = "",
    val soilType: String = "",
    val language: String = "en",
    val includeAi: Boolean = true,
)

// ── Smart Crop Planner ──

@Serializable
data class CropPlanDto(
    @SerialName("_id") val id: String? = null,
    val cropName: String = "",
    val variety: String = "",
    val stage: String = "vegetative",
    val soilType: String = "Loamy",
    val irrigationType: String = "Drip / Canal",
    val farmSizeAcres: Double = 1.0,
    val locationName: String = "India",
    val sowingDate: String = "",
    val customTask: String = "",
    val startDate: String = "",
    val status: String = "active",
    val generationSource: String = "rules_only",
    val aiAdvisoryNote: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class PlannerTaskDto(
    @SerialName("_id") val id: String? = null,
    val planId: String = "",
    val dayOffset: Int = 0,
    val scheduledDate: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "inspection",
    val priority: String = "medium",
    val status: String = "pending",
    val completedAt: String? = null,
    val weatherAdjusted: Boolean = false,
    val weatherReason: String = "",
    val reminderEnabled: Boolean = true,
)

@Serializable
data class CreatePlanRequest(
    val cropName: String,
    val variety: String = "",
    val stage: String = "vegetative",
    val soilType: String = "Loamy",
    val irrigationType: String = "Drip / Canal",
    val farmSizeAcres: Double = 1.0,
    val locationName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val sowingDate: String = "",
    val customTask: String = "",
    val language: String = "en",
    val clientRequestId: String? = null,
    val includeAi: Boolean = true,
)

@Serializable
data class CropPlanBundleResponse(
    val plan: CropPlanDto,
    val tasks: List<PlannerTaskDto> = emptyList(),
    val weatherSource: String? = null,
    val aiAvailable: Boolean = false,
    val aiServiceStatus: String? = null,
)

@Serializable
data class PlannerListResponse(
    val plans: List<CropPlanDto> = emptyList(),
    val tasks: List<PlannerTaskDto> = emptyList(),
)

@Serializable
data class TaskStatusUpdateRequest(
    val status: String,
    val reminderEnabled: Boolean? = null,
)

@Serializable
data class RegeneratePlanRequest(
    val stage: String? = null,
)

// ── Chat ──

@Serializable
data class ChatRequest(
    val message: String,
    val language: String = "en",
    val mode: String? = null,
    val category: String? = null,
    val sessionId: String = "default",
    val clientRequestId: String? = null,
)

@Serializable
data class ChatResponse(
    val response: String,
    val sessionId: String = "default",
    val category: String = "general",
    val groundedSources: List<String> = emptyList(),
    val aiAvailable: Boolean = true,
    val serviceStatus: String? = null,
    val groundedContextSummary: String? = null,
)

@Serializable
data class ChatTurnDto(
    @SerialName("_id") val id: String? = null,
    val message: String = "",
    val response: String = "",
    val category: String = "general",
    val sessionId: String = "default",
    val groundedSources: List<String> = emptyList(),
    val createdAt: String? = null,
)

@Serializable
data class ChatHistoryResponse(
    val sessionId: String = "default",
    val turns: List<ChatTurnDto> = emptyList(),
)

// ── Market ──

fun getCommodityCategory(crop: String): String {
    val lower = crop.lowercase()
    val vegetables = listOf("onion", "प्याज", "potato", "आलू", "tomato", "टमाटर", "chilli", "मिर्च", "cauliflower", "फूलगोभी", "cabbage", "पत्तागोभी", "okra", "भिंडी", "brinjal", "बैंगन", "carrot", "गाजर", "garlic", "लहसुन", "ginger", "अदरक", "spinach", "पालक", "peas", "मटर", "gourd", "लौकी", "करेला", "capsicum")
    val fruits = listOf("apple", "सेब", "banana", "केला", "mango", "आम", "pomegranate", "अनार", "orange", "संतरा", "grapes", "अंगूर", "papaya", "पपीता", "guava", "अमरूद", "watermelon", "तरबूज", "lemon", "नींबू", "pineapple", "mosambi", "strawberry")

    for (v in vegetables) {
        if (lower.contains(v)) return "vegetable"
    }
    for (f in fruits) {
        if (lower.contains(f)) return "fruit"
    }
    return "crop"
}

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
    val district: String = "",
    val state: String = "",
    val trend: String = "stable",
    val category: String? = null,
    val sourceBadge: String? = null,
    val updatedAt: String? = null,
    val lastUpdated: String? = null,
) {
    val effectivePrice: Double
        get() = if (price > 0.0) price else (modalPrice ?: 0.0)

    val effectiveCategory: String
        get() = category?.ifBlank { null } ?: getCommodityCategory(crop)
}

@Serializable
data class MarketStatsDto(
    val total: Int = 0,
    val cropsCount: Int = 0,
    val vegetablesCount: Int = 0,
    val fruitsCount: Int = 0,
    val lastUpdated: String? = null,
    val lastLiveSuccessAt: String? = null,
    val sourceStatus: String = "seeded_reference",
    val lastFetchError: String? = null,
    val apiKeyConfigured: Boolean = false,
)

@Serializable
data class MarketMandiPoint(
    val market: String = "",
    val state: String = "",
    val price: Double = 0.0,
)

@Serializable
data class MarketComparisonDto(
    val crop: String = "",
    val category: String = "crop",
    val mandisCount: Int = 0,
    val highestMandi: MarketMandiPoint = MarketMandiPoint(),
    val lowestMandi: MarketMandiPoint = MarketMandiPoint(),
    val spreadPerQuintal: Double = 0.0,
)

@Serializable
data class MarketCompareResponse(
    val stats: MarketStatsDto = MarketStatsDto(),
    val comparisons: List<MarketComparisonDto> = emptyList(),
)

@Serializable
data class MarketFavoriteDto(
    @SerialName("_id") val id: String? = null,
    val crop: String = "",
    val market: String = "",
    val state: String = "",
    val category: String = "crop",
)

@Serializable
data class MarketFavoriteRequest(
    val crop: String,
    val market: String = "",
    val state: String = "",
    val category: String = "crop",
)

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
    val officialPortal: String? = null,
    val phone: String? = null,
    val states: List<String> = emptyList(),
    val documents: List<String> = emptyList(),
    val applicationSteps: List<String> = emptyList(),
    val lastVerifiedAt: String? = null,
)

@Serializable
data class SchemeCatalogResponse(
    val catalogVersion: String = "",
    val lastVerifiedAt: String = "",
    val verificationNote: String = "",
    val total: Int = 0,
    val schemes: List<Scheme> = emptyList(),
    val savedIds: List<String> = emptyList(),
)

@Serializable
data class SchemeEligibilityMatch(
    val scheme: Scheme,
    val eligibilityStatus: String = "",
    val score: Int = 0,
    val matchedReasons: List<String> = emptyList(),
    val cautions: List<String> = emptyList(),
)

@Serializable
data class SchemeEligibilityResponse(
    val catalogVersion: String = "",
    val lastVerifiedAt: String = "",
    val matches: List<SchemeEligibilityMatch> = emptyList(),
    val aiSummary: String? = null,
    val aiAvailable: Boolean = false,
    val aiServiceStatus: String? = null,
)

@Serializable
data class SchemeRecommendationRequest(
    val state: String,
    val farmSize: String,
    val crop: String,
    val soilType: String,
    val landType: String,
    val hasLivestock: Boolean = false,
    val language: String = "en",
)

@Serializable
data class SchemeExplainRequest(
    val schemeId: String,
    val language: String = "en",
)

@Serializable
data class SchemeExplainResponse(
    val scheme: Scheme,
    val explanation: String? = null,
    val aiAvailable: Boolean = false,
    val serviceStatus: String? = null,
)

@Serializable
data class SavedSchemeDto(
    @SerialName("_id") val id: String? = null,
    val schemeId: String = "",
    val status: String = "saved",
    val notes: String = "",
    val createdAt: String? = null,
)

@Serializable
data class SaveSchemeRequest(
    val schemeId: String,
    val status: String = "saved",
    val notes: String = "",
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

// ── Offline Batch Sync & AI Status ──

@Serializable
data class SyncOperationItem(
    val operationId: String,
    val opType: String,
    val payload: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
)

@Serializable
data class SyncBatchRequest(
    val operations: List<SyncOperationItem>,
)

@Serializable
data class SyncResultEntry(
    val operationId: String? = null,
    val opType: String? = null,
    val status: String = "",
    val resultId: String? = null,
    val error: String? = null,
)

@Serializable
data class SyncBatchResponse(
    val ok: Boolean = false,
    val applied: Int = 0,
    val alreadyApplied: Int = 0,
    val failed: Int = 0,
    val results: List<SyncResultEntry> = emptyList(),
)

// ── Generic text & status responses ──

@Serializable
data class TextResponse(val response: String)

@Serializable
data class StatusResponse(val status: String)

@Serializable
data class ErrorResponse(val error: String)
