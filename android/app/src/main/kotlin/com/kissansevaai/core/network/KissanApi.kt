package com.kissansevaai.core.network

import com.kissansevaai.core.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface KissanApi {

    // ── Auth & Profile ──
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @GET("api/auth/me")
    suspend fun getMe(): UserProfile

    @PUT("api/auth/profile")
    suspend fun updateProfile(@Body request: FarmProfileUpdateRequest): UserProfile

    // ── Crop Disease ──
    @Multipart
    @POST("api/crop/analyze")
    suspend fun analyzeCrop(
        @Part image: MultipartBody.Part,
        @Part("location") location: RequestBody,
        @Part("soilType") soilType: RequestBody,
        @Part("language") language: RequestBody,
    ): CropAnalysisResult

    @Multipart
    @POST("api/crop/second-opinion")
    suspend fun requestCropSecondOpinion(
        @Part image: MultipartBody.Part?,
        @Part("analysisId") analysisId: RequestBody?,
        @Part("farmerConsented") farmerConsented: RequestBody,
        @Part("location") location: RequestBody,
        @Part("soilType") soilType: RequestBody,
        @Part("language") language: RequestBody,
        @Part("primaryCrop") primaryCrop: RequestBody,
        @Part("primaryDisease") primaryDisease: RequestBody,
    ): SecondOpinionResponse

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

    @Multipart
    @POST("api/livestock/analyze")
    suspend fun analyzeLivestockDetailed(
        @Part image: MultipartBody.Part?,
        @Part("animalType") animalType: RequestBody,
        @Part("animalAge") animalAge: RequestBody,
        @Part("symptomDuration") symptomDuration: RequestBody,
        @Part("feverStatus") feverStatus: RequestBody,
        @Part("appetiteStatus") appetiteStatus: RequestBody,
        @Part("milkYieldChange") milkYieldChange: RequestBody,
        @Part("lesionsOrUlcers") lesionsOrUlcers: RequestBody,
        @Part("isolatedFromHerd") isolatedFromHerd: RequestBody,
        @Part("symptomsNotes") symptomsNotes: RequestBody,
        @Part("language") language: RequestBody,
    ): LivestockResult

    @GET("api/livestock/history")
    suspend fun getLivestockHistory(): List<LivestockResult>

    // ── Weather ──
    @GET("api/weather/forecast")
    suspend fun getWeatherForecast(
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null,
        @Query("location") location: String? = null,
        @Query("language") language: String = "en",
        @Query("refresh") refresh: Boolean? = null,
        @Query("includeAi") includeAi: Boolean? = false,
    ): WeatherForecast

    @POST("api/weather/alerts")
    suspend fun postWeatherAlerts(@Body request: WeatherAlertRequest): WeatherForecast

    // ── Smart Crop Planner ──
    @POST("api/planner/plans")
    suspend fun createCropPlan(@Body request: CreatePlanRequest): CropPlanBundleResponse

    @GET("api/planner/plans")
    suspend fun getCropPlans(): PlannerListResponse

    @PATCH("api/planner/tasks/{taskId}")
    suspend fun updatePlannerTask(
        @Path("taskId") taskId: String,
        @Body request: TaskStatusUpdateRequest,
    ): PlannerTaskDto

    @POST("api/planner/plans/{id}/regenerate")
    suspend fun regeneratePlanTasks(
        @Path("id") planId: String,
        @Body request: RegeneratePlanRequest = RegeneratePlanRequest(),
    ): CropPlanBundleResponse

    // ── Chat ──
    @POST("api/chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse

    @GET("api/chat/history")
    suspend fun getChatHistory(
        @Query("sessionId") sessionId: String = "default",
        @Query("limit") limit: Int = 40,
    ): ChatHistoryResponse

    @DELETE("api/chat/history")
    suspend fun clearChatHistory(@Query("sessionId") sessionId: String = "default"): kotlinx.serialization.json.JsonObject

    // ── Market ──
    @GET("api/market")
    suspend fun getMarketPrices(
        @Query("crop") crop: String? = null,
        @Query("state") state: String? = null,
        @Query("category") category: String? = null,
        @Query("refresh") refresh: Boolean? = null,
    ): List<MarketPrice>

    @GET("api/market/stats")
    suspend fun getMarketStats(): MarketStatsDto

    @GET("api/market/compare")
    suspend fun compareMarketPrices(
        @Query("crop") crop: String? = null,
        @Query("category") category: String? = null,
    ): MarketCompareResponse

    @GET("api/market/favorites")
    suspend fun getMarketFavorites(): List<MarketFavoriteDto>

    @POST("api/market/favorites")
    suspend fun addMarketFavorite(@Body request: MarketFavoriteRequest): MarketFavoriteDto

    @DELETE("api/market/favorites")
    suspend fun removeMarketFavorite(
        @Query("crop") crop: String,
        @Query("market") market: String = "",
    ): kotlinx.serialization.json.JsonObject

    @POST("api/market/recommendations")
    suspend fun getMarketRecommendations(@Body request: MarketRecommendationRequest): TextResponse

    // ── Schemes ──
    @GET("api/schemes")
    suspend fun getSchemesCatalog(
        @Query("state") state: String? = null,
        @Query("category") category: String? = null,
        @Query("q") query: String? = null,
    ): SchemeCatalogResponse

    @POST("api/schemes/eligibility")
    suspend fun checkSchemeEligibility(@Body request: SchemeRecommendationRequest): SchemeEligibilityResponse

    @POST("api/schemes/recommendations")
    suspend fun getSchemeRecommendations(@Body request: SchemeRecommendationRequest): TextResponse

    @POST("api/schemes/explain")
    suspend fun explainScheme(@Body request: SchemeExplainRequest): SchemeExplainResponse

    @GET("api/schemes/saved")
    suspend fun getSavedSchemes(): List<SavedSchemeDto>

    @POST("api/schemes/saved")
    suspend fun saveScheme(@Body request: SaveSchemeRequest): SavedSchemeDto

    @DELETE("api/schemes/saved/{schemeId}")
    suspend fun removeSavedScheme(@Path("schemeId") schemeId: String): kotlinx.serialization.json.JsonObject

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

    // ── Offline Sync & AI Status ──
    @POST("api/sync/batch")
    suspend fun syncBatch(@Body request: SyncBatchRequest): SyncBatchResponse

    @GET("api/ai/status")
    suspend fun getAiStatus(): kotlinx.serialization.json.JsonObject
}
