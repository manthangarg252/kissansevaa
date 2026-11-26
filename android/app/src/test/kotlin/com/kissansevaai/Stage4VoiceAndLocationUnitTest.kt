package com.kissansevaai

import android.content.Context
import android.speech.SpeechRecognizer
import com.kissansevaai.core.ai.FieldExtractor
import com.kissansevaai.core.ai.SpeechToText
import com.kissansevaai.core.ai.ondevice.AndroidSpeechToText
import com.kissansevaai.core.ai.remote.RemoteFieldExtractor
import com.kissansevaai.core.ai.voice.AgriculturalVoiceParser
import com.kissansevaai.core.ai.voice.HybridFieldExtractor
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.database.*
import com.kissansevaai.core.location.*
import com.kissansevaai.core.model.*
import com.kissansevaai.core.network.KissanApi
import com.kissansevaai.core.repository.FarmLocationRepository
import com.kissansevaai.core.repository.PlannerRepository
import com.kissansevaai.core.repository.PricesRepository
import com.kissansevaai.core.repository.WeatherRepository
import com.kissansevaai.core.sync.OfflineSyncManager
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class Stage4VoiceAndLocationUnitTest {

    // =========================================================================
    // 1. MULTILINGUAL VOICE NORMALIZATION & PARSING TESTS
    // =========================================================================

    @Test
    fun `hindi, punjabi, marathi and english crop names normalize consistently`() {
        assertEquals("Wheat", AgriculturalVoiceParser.normalizeCropName("gehun"))
        assertEquals("Wheat", AgriculturalVoiceParser.normalizeCropName("गेहूं"))
        assertEquals("Wheat", AgriculturalVoiceParser.normalizeCropName("kanak"))
        assertEquals("Paddy", AgriculturalVoiceParser.normalizeCropName("Dhaan"))
        assertEquals("Paddy", AgriculturalVoiceParser.normalizeCropName("धान"))
        assertEquals("Maize", AgriculturalVoiceParser.normalizeCropName("makka"))
        assertEquals("Maize", AgriculturalVoiceParser.normalizeCropName("ਮੱਕੀ"))
        assertEquals("Cotton", AgriculturalVoiceParser.normalizeCropName("Kapas"))
        assertEquals("Potato", AgriculturalVoiceParser.normalizeCropName("Aloo"))
        assertEquals("Potato", AgriculturalVoiceParser.normalizeCropName("बटाटा"))
        assertEquals("Tomato", AgriculturalVoiceParser.normalizeCropName("tamatar"))
    }

    @Test
    fun `mixed hindi-english crop planner sentence parses crop, unambiguous date and acre area`() {
        val refDate = LocalDate.of(2026, 11, 26)
        val outcome = AgriculturalVoiceParser.parse(
            transcript = "Maine 10 November ko gehun boya hai, mere paas 2 acre zameen hai.",
            targetFields = listOf("cropName", "sowingDate", "farmSize", "soilType"),
            language = "hi",
            referenceDate = refDate,
        )

        assertEquals("Wheat", outcome.extractedFields["cropName"])
        assertEquals("2026-11-10", outcome.extractedFields["sowingDate"])
        assertEquals("2", outcome.extractedFields["farmSize"])
        assertFalse(outcome.ambiguousFields.containsKey("sowingDate"))
        assertFalse(outcome.ambiguousFields.containsKey("farmSize"))
    }

    @Test
    fun `mixed hindi-english livestock speech extracts animal, fever, milk change and duration without auto-guessing temperature or dosage`() {
        val outcome = AgriculturalVoiceParser.parse(
            transcript = "Meri bhains ko do din se bukhar hai aur doodh kam ho gaya hai.",
            targetFields = listOf("animalType", "symptomDuration", "feverStatus", "milkYieldChange", "symptomsNotes"),
            language = "hi",
        )

        assertEquals("Buffalo", outcome.extractedFields["animalType"])
        assertEquals("2 days", outcome.extractedFields["symptomDuration"])
        assertEquals("1-3 days", outcome.extractedFields["durationChip"])
        assertEquals("yes", outcome.extractedFields["feverStatus"])
        assertEquals("dropped", outcome.extractedFields["milkYieldChange"])
        val symp = outcome.extractedFields["symptomsNotes"].orEmpty().lowercase()
        assertTrue(symp.contains("fever"))
        assertTrue(symp.contains("reduced milk"))
    }

    @Test
    fun `area units convert hectares deterministically and flag regional bigha for confirmation`() {
        val haOutcome = AgriculturalVoiceParser.parse(
            transcript = "I planted makka on 2 hectare loamy field with drip irrigation",
            targetFields = listOf("cropName", "farmSize", "soilType", "irrigationType"),
            language = "en",
        )
        assertEquals("Maize", haOutcome.extractedFields["cropName"])
        assertEquals("4.94", haOutcome.extractedFields["farmSize"])
        assertEquals("Loamy", haOutcome.extractedFields["soilType"])
        assertEquals("Drip", haOutcome.extractedFields["irrigationType"])

        val bighaOutcome = AgriculturalVoiceParser.parse(
            transcript = "Mere paas 2 bigha khet hai aur aloo lagaya hai",
            targetFields = listOf("cropName", "farmSize"),
            language = "hi",
        )
        assertEquals("Potato", bighaOutcome.extractedFields["cropName"])
        assertNull(bighaOutcome.extractedFields["farmSize"])
        assertTrue(bighaOutcome.ambiguousFields.containsKey("farmSize"))
    }

    @Test
    fun `ambiguous or incomplete sowing dates never fabricate an ISO date and request confirmation`() {
        val refDate = LocalDate.of(2026, 11, 26)
        val vague = AgriculturalVoiceParser.parse(
            transcript = "Maine pichle hafte kapas boya tha",
            targetFields = listOf("cropName", "sowingDate"),
            referenceDate = refDate,
        )
        assertEquals("Cotton", vague.extractedFields["cropName"])
        assertNull(vague.extractedFields["sowingDate"])
        assertTrue(vague.ambiguousFields.containsKey("sowingDate"))

        val staleYearOmitted = AgriculturalVoiceParser.parse(
            transcript = "15 March ko dhaan boya",
            targetFields = listOf("cropName", "sowingDate"),
            referenceDate = refDate,
        )
        assertEquals("Paddy", staleYearOmitted.extractedFields["cropName"])
        assertNull(staleYearOmitted.extractedFields["sowingDate"])
        assertTrue(staleYearOmitted.ambiguousFields.containsKey("sowingDate"))
    }

    @Test
    fun `transcript editing re-parses cleanly and deterministic extractor works even when Gemini fails with 429`() = runTest {
        val remote = mockk<RemoteFieldExtractor>()
        coEvery { remote.extract(any(), any(), any()) } throws RuntimeException("HTTP 429 quota_exhausted")

        val hybrid = HybridFieldExtractor(remote)
        val initial = hybrid.extractDetailed(
            text = "Maine tamatar boya",
            fields = listOf("cropName", "soilType", "customTask"),
            language = "hi",
        )
        assertEquals("Tomato", initial.extractedFields["cropName"])
        assertFalse(initial.usedAiFallback)

        // Farmer edits transcript to add soil and custom task and re-parses before saving
        val edited = hybrid.extractDetailed(
            text = "Maine kali mitti mein tamatar boya task apply neem spray",
            fields = listOf("cropName", "soilType", "customTask"),
            language = "hi",
        )
        assertEquals("Tomato", edited.extractedFields["cropName"])
        assertEquals("Black", edited.extractedFields["soilType"])
        assertTrue(edited.extractedFields["customTask"]?.contains("neem spray", ignoreCase = true) == true)
    }

    @Test
    fun `AndroidSpeechToText handles missing recognition service, bhojpuri fallback, cancel and retry`() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        mockkStatic(SpeechRecognizer::class)

        val ctx = mockk<Context>(relaxed = true)
        every { SpeechRecognizer.isRecognitionAvailable(ctx) } returns false
        val sttUnavailable = AndroidSpeechToText(ctx)

        var receivedErr: String? = null
        sttUnavailable.startListening("hi", onResult = {}, onError = { receivedErr = it })
        assertTrue(receivedErr?.contains("not available", ignoreCase = true) == true)

        // Bhojpuri uses explicit Hindi acoustic fallback disclosure
        every { SpeechRecognizer.isRecognitionAvailable(ctx) } returns true
        val resolution = sttUnavailable.resolveRecognitionLocale("bho")
        assertTrue(resolution.usedFallback)
        assertEquals("hi-IN", resolution.localeTag)

        // Cancellation invokes onCancelled and suppresses error callback on subsequent retry
        val mockRec = mockk<SpeechRecognizer>(relaxed = true)
        every { SpeechRecognizer.createSpeechRecognizer(ctx) } returns mockRec
        var cancelledCalled = false
        sttUnavailable.startListening("en", onResult = {}, onError = { fail("Should not error after cancel") }, onCancelled = { cancelledCalled = true })
        sttUnavailable.cancelListening()
        assertTrue(cancelledCalled)
        verify { mockRec.cancel(); mockRec.destroy() }
    }

    // =========================================================================
    // 2. AUTOMATIC LOCATION, REVERSE GEOCODING, CONFIRMATION & ISOLATION TESTS
    // =========================================================================

    @Test
    fun `FarmLocationRepository handles permission denied, approximate, precise, null-stale, geocoder failure, confirm, change and offline sync`() = runTest {
        val api = mockk<KissanApi>()
        val syncDao = mockk<SyncOperationDao>(relaxed = true)
        val farmDao = mockk<FarmProfileDao>()
        val dataStore = mockk<DataStoreManager>()
        val userIdFlow = MutableStateFlow<String?>("farmer_u1")
        every { dataStore.userId } returns userIdFlow

        val storedProfiles = mutableMapOf<String, FarmProfileEntity>()
        every { farmDao.observeProfile(any()) } answers {
            val uid = firstArg<String>()
            flowOf(storedProfiles[uid])
        }
        coEvery { farmDao.getProfileOnce(any()) } answers {
            storedProfiles[firstArg()]
        }
        coEvery { farmDao.upsert(any()) } answers {
            val entity = firstArg<FarmProfileEntity>()
            storedProfiles[entity.userId] = entity
        }

        val detector = mockk<LocationDetector>()
        val repo = FarmLocationRepository(api, farmDao, syncDao, dataStore, detector)

        // 1) Permission denied does NOT crash or overwrite saved farm
        coEvery { detector.detectCurrentLocation() } returns LocationDetectionResult.PermissionDenied
        assertEquals(LocationDetectionResult.PermissionDenied, repo.detectCurrentPhoneLocation())
        assertNull(repo.currentPhoneLocation.value)

        // 2) Null / Stale cached GPS does NOT fabricate coordinates
        coEvery { detector.detectCurrentLocation() } returns LocationDetectionResult.NullOrStaleLocation("Stale 40m", 40)
        assertTrue(repo.detectCurrentPhoneLocation() is LocationDetectionResult.NullOrStaleLocation)

        // 3) Approximate location & geocoder failure keeps coordinates without inventing district, and does NOT auto-save until confirmed
        val approxDetected = DetectedLocation(
            latitude = 30.901,
            longitude = 75.8573,
            accuracyMeters = 650f,
            isPrecise = false,
            locationSource = "gps_approximate",
            district = "",
            state = "Punjab",
            geocoderFailed = true,
        )
        coEvery { detector.detectCurrentLocation() } returns LocationDetectionResult.Success(approxDetected)
        val approxRes = repo.detectCurrentPhoneLocation() as LocationDetectionResult.Success
        assertEquals("gps_approximate", approxRes.location.locationSource)
        assertEquals("", approxRes.location.district) // Never invented!
        assertNull(repo.getSavedFarmLocationOnce()) // Still NOT saved until farmer confirms!

        // 4) User confirms farm location -> persists to Room & syncs to MongoDB (or queues offline if network fails)
        coEvery { api.updateProfile(any()) } throws IOException("Offline")
        val confirmedOffline = repo.confirmAndSaveFarmProfile(
            SavedFarmLocation(
                userId = "farmer_u1",
                farmName = "Canal Plot",
                district = "Ludhiana",
                state = "Punjab",
                latitude = 30.901,
                longitude = 75.8573,
                locationSource = "gps_approximate",
            )
        ) as Resource.Success
        assertTrue(confirmedOffline.data.pendingSync)
        assertEquals("Ludhiana", storedProfiles["farmer_u1"]?.district)
        coVerify(exactly = 1) { syncDao.enqueue(match { it.opType == "profile_update" && it.userId == "farmer_u1" }) }

        // refreshFromBackend while pendingSync=true must NOT clobber local offline edit!
        coEvery { api.getMe() } returns UserProfile(id = "farmer_u1", name = "A", email = "a@k.com", district = "OldCity", state = "OldState")
        val nonClobbered = repo.refreshFromBackend()
        assertEquals("Ludhiana", nonClobbered?.district)

        // 5) OfflineSyncManager flushes profile_update & clears pendingSync
        coEvery { syncDao.getPendingOperations("farmer_u1") } returns listOf(
            SyncOperationEntity(operationId = "prof_op_1", userId = "farmer_u1", opType = "profile_update", payloadJson = """{"district":"Ludhiana"}""")
        )
        coEvery { api.syncBatch(any()) } returns SyncBatchResponse(
            ok = true, applied = 1,
            results = listOf(SyncResultEntry(operationId = "prof_op_1", opType = "profile_update", status = "applied")),
        )
        val syncMgr = OfflineSyncManager(api, syncDao, dataStore, mockk(relaxed = true), mockk(relaxed = true), farmDao)
        assertEquals(1, syncMgr.flushPendingOperations())
        assertFalse(storedProfiles["farmer_u1"]!!.pendingSync)

        // 6) User switches to precise location / changes farm location cleanly online
        val preciseDetected = DetectedLocation(
            latitude = 22.7196,
            longitude = 75.8577,
            accuracyMeters = 8f,
            isPrecise = true,
            locationSource = "gps_precise",
            district = "Indore",
            state = "Madhya Pradesh",
        )
        coEvery { detector.detectCurrentLocation() } returns LocationDetectionResult.Success(preciseDetected)
        val preciseRes = repo.detectCurrentPhoneLocation() as LocationDetectionResult.Success
        assertTrue(preciseRes.location.isPrecise)
        coEvery { api.updateProfile(any()) } returns UserProfile(
            id = "farmer_u1", name = "A", email = "a@k.com",
            district = "Indore", state = "Madhya Pradesh",
            latitude = 22.7196, longitude = 75.8577, locationSource = "gps_precise", locationUpdatedAt = "2026-11-26T11:00:00Z",
        )
        val updated = repo.confirmAndSaveFarmProfile(
            SavedFarmLocation(district = "Indore", state = "Madhya Pradesh", latitude = 22.7196, longitude = 75.8577, locationSource = "gps_precise")
        ) as Resource.Success
        assertFalse(updated.data.pendingSync)
        assertEquals("Indore", updated.data.district)

        // 7) Multiple-account isolation: logging in farmer_u2 must NOT expose farmer_u1's farm profile
        userIdFlow.value = "farmer_u2"
        assertNull(repo.getSavedFarmLocationOnce())
    }

    // =========================================================================
    // 3. END-TO-END FLOW: VOICE -> CONFIRM -> PLANNER -> LOCATION -> WEATHER & MANDI
    // =========================================================================

    @Test
    fun `end to end farmer voice planner + confirmed location drives weather coordinates and mandi filtering`() = runTest {
        // Step 1: Farmer speaks Hindi/English crop & land details
        val parsed = AgriculturalVoiceParser.parse(
            transcript = "Maine 10 November ko gehun boya hai, mere paas 2 acre zameen hai.",
            targetFields = listOf("cropName", "sowingDate", "farmSize", "soilType"),
            language = "hi",
            referenceDate = LocalDate.of(2026, 11, 26),
        )
        assertEquals("Wheat", parsed.extractedFields["cropName"])
        assertEquals("2026-11-10", parsed.extractedFields["sowingDate"])
        assertEquals("2", parsed.extractedFields["farmSize"])

        // Step 2: Farmer confirms and PlannerRepository saves with confirmed coordinates
        val api = mockk<KissanApi>()
        val dataStore = mockk<DataStoreManager>()
        every { dataStore.userId } returns flowOf("u_e2e")
        val planSlot = slot<CreatePlanRequest>()
        coEvery { api.createCropPlan(capture(planSlot)) } returns CropPlanBundleResponse(
            plan = CropPlanDto(
                id = "p_1", cropName = "Wheat", farmSizeAcres = 2.0,
                locationName = "Indore, Madhya Pradesh", sowingDate = "2026-11-10",
            ),
            tasks = emptyList(),
        )
        val plannerRepo = PlannerRepository(api, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), dataStore)
        val emissions = mutableListOf<Resource<CropPlanBundleResponse>>()
        plannerRepo.createPlan(
            CreatePlanRequest(
                cropName = parsed.extractedFields["cropName"]!!,
                farmSizeAcres = parsed.extractedFields["farmSize"]!!.toDouble(),
                sowingDate = parsed.extractedFields["sowingDate"]!!,
                locationName = "Indore, Madhya Pradesh",
                latitude = 22.7196,
                longitude = 75.8577,
            )
        ).collect { emissions.add(it) }

        assertEquals(22.7196, planSlot.captured.latitude!!, 0.001)
        assertEquals("2026-11-10", planSlot.captured.sowingDate)

        // Step 3: WeatherRepository uses confirmed farm lat/lon for Open-Meteo
        val weatherDao = mockk<WeatherDao>(relaxed = true)
        every { weatherDao.getLatest() } returns flowOf(null)
        coEvery {
            api.getWeatherForecast(lat = 22.7196, lon = 75.8577, location = "Indore, Madhya Pradesh", language = "en", refresh = null, includeAi = false)
        } returns WeatherForecast(
            locationName = "Indore, Madhya Pradesh",
            latitude = 22.7196,
            longitude = 75.8577,
            current = WeatherCurrent(temperatureC = 25.0),
        )
        val weatherRepo = WeatherRepository(api, weatherDao)
        val wOut = mutableListOf<Resource<WeatherForecast>>()
        weatherRepo.getForecast(lat = 22.7196, lon = 75.8577, location = "Indore, Madhya Pradesh").collect { wOut.add(it) }
        val wSuccess = wOut.filterIsInstance<Resource.Success<WeatherForecast>>().last()
        assertEquals("Indore, Madhya Pradesh", wSuccess.data.locationName)

        // Step 4: PricesRepository filters by saved farm state/district cleanly
        val marketDao = mockk<MarketPriceDao>(relaxed = true)
        every { marketDao.getAllMarketPrices() } returns flowOf(
            listOf(
                MarketPriceEntity("1", "Wheat", 2600.0, 2600.0, 2400.0, 2700.0, "quintal", "Indore APMC", "Madhya Pradesh", "up", null, null),
                MarketPriceEntity("2", "Wheat", 2400.0, 2400.0, 2300.0, 2500.0, "quintal", "Khanna Mandi", "Punjab", "stable", null, null),
            )
        )
        coEvery { api.getMarketPrices(any(), any(), any(), any()) } returns emptyList()
        val pricesRepo = PricesRepository(api, marketDao)
        val pOut = mutableListOf<Resource<List<MarketPrice>>>()
        pricesRepo.getMarketPrices(crop = "Wheat", state = "Madhya Pradesh").collect { pOut.add(it) }
        val pList = pOut.filterIsInstance<Resource.Success<List<MarketPrice>>>().last().data
        assertEquals(1, pList.size)
        assertEquals("Indore APMC", pList.first().market)
    }
}
