package com.kissansevaai.core.network

import com.kissansevaai.core.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface KissanApi {

    // ── Auth ──
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @GET("api/auth/me")
    suspend fun getMe(): UserProfile

    // ── Crop Disease ──
    @Multipart
    @POST("api/crop/analyze")
    suspend fun analyzeCrop(
        @Part image: MultipartBody.Part,
        @Part("location") location: RequestBody,
        @Part("soilType") soilType: RequestBody,
        @Part("language") language: RequestBody,
    ): CropAnalysisResult

    @GET("api/crop/history")
    suspend fun getCropHistory(): List<CropAnalysisResult>

    // ── Livestock ──
    @Multipart
    @POST("api/livestock/analyze")
    suspend fun analyzeLivestock(
        @Part image: MultipartBody.Part,
        @Part("animalType") animalType: RequestBody,
        @Part("language") language: RequestBody,
    ): LivestockResult

    // ── Chat ──
    @POST("api/chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse

    // ── Market ──
    @GET("api/market")
    suspend fun getMarketPrices(
        @Query("crop") crop: String? = null,
        @Query("state") state: String? = null,
    ): List<MarketPrice>

    @POST("api/market/recommendations")
    suspend fun getMarketRecommendations(@Body request: MarketRecommendationRequest): TextResponse

    // ── Schemes ──
    @POST("api/schemes/recommendations")
    suspend fun getSchemeRecommendations(@Body request: SchemeRecommendationRequest): TextResponse

    // ── Carbon ──
    @POST("api/carbon/estimate")
    suspend fun estimateCarbon(@Body request: CarbonEstimateRequest): TextResponse

    // ── Loans ──
    @POST("api/loans/advisor")
    suspend fun getLoanAdvice(@Body request: LoanAdvisorRequest): LoanAdvisorResult

    // ── Traders ──
    @GET("api/traders")
    suspend fun getTraders(
        @Query("location") location: String? = null,
        @Query("search") search: String? = null,
    ): List<Trader>

    @POST("api/traders/advisory")
    suspend fun getTraderAdvisory(@Body request: TraderAdvisoryRequest): TextResponse

    // ── IoT ──
    @GET("api/iot/live")
    suspend fun getIoTLive(): SensorData

    @POST("api/iot/insights")
    suspend fun getIoTInsights(@Body request: IoTInsightsRequest): TextResponse

    // ── Field Extraction ──
    @POST("api/extract-fields")
    suspend fun extractFields(@Body request: FieldExtractionRequest): FieldExtractionResponse
}
