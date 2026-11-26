package com.kissansevaai

import com.kissansevaai.core.common.toUserFriendlyMessage
import com.kissansevaai.core.database.*
import com.kissansevaai.core.model.*
import com.kissansevaai.core.network.KissanApi
import com.kissansevaai.core.repository.LivestockRepository
import com.kissansevaai.core.repository.PlannerRepository
import com.kissansevaai.core.repository.WeatherRepository
import com.kissansevaai.core.sync.OfflineSyncManager
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class Stage1FeaturesTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun `Room CropAnalysisEntity preserves secondOpinion and reconciliation fields through toDomain`() {
        val orig = CropAnalysisResult(
            id = "crop_99",
            source = "dinov2",
            predictionSource = "dinov2",
            cropName = "Potato",
            diseaseName = "Late Blight",
            confidence = null,
            top_cosine_similarity = 0.51,
            neighbor_agreement = 0.40,
            needs_review = true,
            treatmentWithheld = true,
            secondOpinion = SecondOpinionResult(
                requested = true,
                farmerConsented = true,
                source = "gemini",
                cropName = "Potato",
                diseaseName = "Late Blight",
                confidence = 0.87,
                summary = "Confirmed late blight.",
            ),
            reconciliationStatus = "agreed",
            reconciliationNote = "Aligned on Potato Late Blight.",
        )

        val roundTrip = orig.toEntity(userId = "farmer_1").toDomain()
        assertEquals("dinov2", roundTrip.predictionSource)
        assertNull(roundTrip.confidence)
        assertEquals("agreed", roundTrip.reconciliationStatus)
        assertEquals("gemini", roundTrip.secondOpinion?.source)
        assertEquals(0.87, roundTrip.secondOpinion?.confidence ?: 0.0, 0.001)
    }

    @Test
    fun `WeatherRepository serves cached forecast gracefully with isStale flag when offline`() = runTest {
        val api = mockk<KissanApi>()
        val dao = mockk<WeatherDao>(relaxed = true)
        val cached = WeatherCacheEntity(
            locationKey = "21.15,79.09",
            locationName = "Nagpur, Maharashtra",
            latitude = 21.15,
            longitude = 79.09,
            temperatureC = 28.4,
            humidityPct = 62.0,
            windSpeedKmh = 9.0,
            precipitationMm = 0.0,
            conditionLabel = "Clear Sky",
            advisoriesJson = """[{"id":"spray_optimal","category":"spray","severity":"info","sprayWindowSuitable":true,"title":"Favorable Spray Window","message":"Calm winds"}]""",
            dailyJson = "[]",
            fetchedAt = "2026-11-26T04:00:00Z",
            expiresAt = "2026-11-26T04:30:00Z",
        )
        every { dao.getLatest() } returns flowOf(cached)
        coEvery { api.getWeatherForecast(any(), any(), any(), any(), any(), any()) } throws IOException("Offline")

        val repo = WeatherRepository(api, dao)
        val emissions = mutableListOf<com.kissansevaai.core.common.Resource<WeatherForecast>>()
        repo.getForecast(location = "Nagpur", forceRefresh = true).collect { emissions.add(it) }

        val lastSuccess = emissions.filterIsInstance<com.kissansevaai.core.common.Resource.Success<WeatherForecast>>().lastOrNull()
        assertNotNull(lastSuccess)
        assertEquals("Nagpur, Maharashtra", lastSuccess!!.data.locationName)
        assertTrue(lastSuccess.data.isStale)
        assertTrue(lastSuccess.data.sprayWindowSuitable)
    }

    @Test
    fun `PlannerRepository queues idempotent offline plan creation when network fails`() = runTest {
        val api = mockk<KissanApi>()
        val planDao = mockk<CropPlanDao>(relaxed = true)
        val taskDao = mockk<PlannerTaskDao>(relaxed = true)
        val syncDao = mockk<SyncOperationDao>(relaxed = true)
        val dataStore = mockk<com.kissansevaai.core.data.DataStoreManager>()

        every { dataStore.userId } returns flowOf("user_abc")
        coEvery { api.createCropPlan(any()) } throws IOException("No network")

        val repo = PlannerRepository(api, planDao, taskDao, syncDao, dataStore)
        val emissions = mutableListOf<com.kissansevaai.core.common.Resource<CropPlanBundleResponse>>()
        repo.createPlan(CreatePlanRequest(cropName = "Soybean", locationName = "Indore")).collect { emissions.add(it) }

        val success = emissions.filterIsInstance<com.kissansevaai.core.common.Resource.Success<CropPlanBundleResponse>>().single()
        assertEquals("offline_queued", success.data.weatherSource)
        coVerify(exactly = 1) { syncDao.enqueue(match { it.opType == "planner_plan_create" && it.userId == "user_abc" }) }
        coVerify(exactly = 1) { planDao.insert(match { it.cropName == "Soybean" && it.pendingSync }) }
    }

    @Test
    fun `OfflineSyncManager flushes queued operations and removes local placeholder plan to avoid duplicates`() = runTest {
        val api = mockk<KissanApi>()
        val syncDao = mockk<SyncOperationDao>(relaxed = true)
        val dataStore = mockk<com.kissansevaai.core.data.DataStoreManager>()
        val cropPlanDao = mockk<CropPlanDao>(relaxed = true)
        val plannerTaskDao = mockk<PlannerTaskDao>(relaxed = true)

        every { dataStore.userId } returns flowOf("user_abc")
        coEvery { syncDao.getPendingOperations("user_abc") } returns listOf(
            SyncOperationEntity(
                operationId = "plan_op_1",
                userId = "user_abc",
                opType = "planner_plan_create",
                payloadJson = """{"cropName":"Soybean"}""",
            )
        )
        coEvery { api.syncBatch(any()) } returns SyncBatchResponse(
            ok = true,
            applied = 1,
            results = listOf(SyncResultEntry(operationId = "plan_op_1", opType = "planner_plan_create", status = "applied")),
        )

        val mgr = OfflineSyncManager(api, syncDao, dataStore, cropPlanDao, plannerTaskDao)
        val synced = mgr.flushPendingOperations()
        assertEquals(1, synced)
        coVerify(exactly = 1) { plannerTaskDao.deleteForPlan("local_plan_op_1") }
        coVerify(exactly = 1) { cropPlanDao.deleteById("local_plan_op_1") }
        coVerify(exactly = 1) { syncDao.updateStatus("plan_op_1", "synced", 0, null, any()) }
        coVerify(exactly = 1) { syncDao.pruneSynced() }
    }

    @Test
    fun `Stage1 JSON contracts deserialize Livestock safety and Weather responses cleanly`() {
        val livestockPayload = """
            {
              "_id": "liv_1",
              "animalType": "Cattle",
              "conditionName": "Foot and Mouth Disease Suspect",
              "confidence": 0.72,
              "severity": "High",
              "urgentVetEscalation": true,
              "escalationReason": "Fever with mouth blisters",
              "prescriptionsWithheld": true,
              "safetyNotice": "Prescription drugs withheld",
              "aiAvailable": true
            }
        """.trimIndent()
        val decoded = json.decodeFromString<LivestockResult>(livestockPayload)
        assertTrue(decoded.urgentVetEscalation)
        assertTrue(decoded.prescriptionsWithheld)
        assertEquals(0.72, decoded.confidence ?: 0.0, 0.001)
    }

    @Test
    fun `toUserFriendlyMessage extracts server JSON error body on HTTP 429 instead of generic message`() {
        val body = """{"error":"AI daily API quota is exhausted on the configured key.","serviceStatus":"quota_exhausted"}"""
            .toResponseBody("application/json".toMediaType())
        val httpErr = HttpException(Response.error<Any>(429, body))
        assertEquals("AI daily API quota is exhausted on the configured key.", httpErr.toUserFriendlyMessage())
    }

    @Test
    fun `LivestockRepository preserves emergency vet escalation on HTTP 429 degraded response`() = runTest {
        val api = mockk<KissanApi>()
        val livestockDao = mockk<LivestockDao>(relaxed = true)
        val dataStore = mockk<com.kissansevaai.core.data.DataStoreManager>()
        every { dataStore.userId } returns flowOf("user_abc")

        val degradedBody = """
            {
              "error": "AI daily API quota is exhausted.",
              "aiAvailable": false,
              "serviceStatus": "quota_exhausted",
              "urgentVetEscalation": true,
              "escalationReason": "Fever combined with blisters/lesions/ulcers",
              "prescriptionsWithheld": true,
              "safetyNotice": "Veterinary Safety Notice: Prescription antibiotics withheld.",
              "immediateAction": "URGENT: Isolate affected animal and call 1962."
            }
        """.trimIndent().toResponseBody("application/json".toMediaType())

        coEvery {
            api.analyzeLivestockDetailed(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws HttpException(Response.error<LivestockResult>(429, degradedBody))

        val repo = LivestockRepository(api, livestockDao, dataStore)
        val txt = "Cattle".toRequestBody("text/plain".toMediaType())
        val results = mutableListOf<com.kissansevaai.core.common.Resource<LivestockResult>>()
        repo.analyzeLivestockDetailed(null, txt, txt, txt, txt, txt, txt, txt, txt, txt, txt).collect { results.add(it) }

        val res = results.filterIsInstance<com.kissansevaai.core.common.Resource.Success<LivestockResult>>().single().data
        assertFalse(res.aiAvailable)
        assertTrue(res.urgentVetEscalation)
        assertEquals("quota_exhausted", res.serviceStatus)
    }
}
