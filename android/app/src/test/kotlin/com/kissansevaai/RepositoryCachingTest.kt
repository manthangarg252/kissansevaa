package com.kissansevaai

import app.cash.turbine.test
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.database.*
import com.kissansevaai.core.model.*
import com.kissansevaai.core.network.KissanApi
import com.kissansevaai.core.repository.*
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class RepositoryCachingTest {

    private val api = mockk<KissanApi>()
    private val marketPriceDao = mockk<MarketPriceDao>(relaxed = true)
    private val traderDao = mockk<TraderDao>(relaxed = true)
    private val cropDao = mockk<CropAnalysisDao>(relaxed = true)
    private val chatDao = mockk<ChatDao>(relaxed = true)
    private val ioTDao = mockk<IoTDao>(relaxed = true)
    private val schemeDao = mockk<SchemeDao>(relaxed = true)
    private val loanProductDao = mockk<LoanProductDao>(relaxed = true)

    // =========================================================================
    // 1. PricesRepository (Market Prices)
    // =========================================================================

    @Test
    fun `prices repository emits cached immediately on subscribe`() = runTest {
        val cached = MarketPriceEntity(
            id = "mp_1", crop = "Wheat", price = 2200.0, modalPrice = 2200.0,
            minPrice = 2000.0, maxPrice = 2400.0, unit = "quintal",
            market = "Nagpur APMC", state = "Maharashtra", trend = "stable",
            updatedAt = null, lastUpdated = null
        )
        every { marketPriceDao.getAllMarketPrices() } returns flowOf(listOf(cached))
        coEvery { api.getMarketPrices(any(), any()) } returns emptyList()

        val repo = PricesRepository(api, marketPriceDao)
        repo.getMarketPrices().test {
            assertTrue(awaitItem() is Resource.Loading)
            val cachedItem = awaitItem()
            assertTrue(cachedItem is Resource.Success)
            assertEquals("Wheat", (cachedItem as Resource.Success).data.first().crop)
            assertEquals(2200.0, cachedItem.data.first().price, 0.01)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `prices repository preserves cache when background refresh fails`() = runTest {
        val cached = MarketPriceEntity(
            id = "mp_1", crop = "Wheat", price = 2200.0, modalPrice = 2200.0,
            minPrice = 2000.0, maxPrice = 2400.0, unit = "quintal",
            market = "Nagpur APMC", state = "Maharashtra", trend = "stable",
            updatedAt = null, lastUpdated = null
        )
        every { marketPriceDao.getAllMarketPrices() } returns flowOf(listOf(cached))
        coEvery { api.getMarketPrices(any(), any()) } throws IOException("Network down")

        val repo = PricesRepository(api, marketPriceDao)
        repo.getMarketPrices().test {
            assertTrue(awaitItem() is Resource.Loading)
            val cachedItem = awaitItem()
            assertTrue(cachedItem is Resource.Success)
            assertEquals(2200.0, (cachedItem as Resource.Success).data.first().price, 0.01)
            // Failed background refresh completes without wiping cache or emitting Resource.Error
            awaitComplete()
        }
        coVerify(exactly = 0) { marketPriceDao.clearAll() }
    }

    @Test
    fun `prices repository writes through network data and re-emits on successful refresh`() = runTest {
        val cached = MarketPriceEntity(
            id = "mp_1", crop = "Wheat", price = 2200.0, modalPrice = 2200.0,
            minPrice = 2000.0, maxPrice = 2400.0, unit = "quintal",
            market = "Nagpur APMC", state = "Maharashtra", trend = "stable",
            updatedAt = null, lastUpdated = null
        )
        val fresh = MarketPrice(
            id = "mp_1", crop = "Wheat", price = 2450.0,
            market = "Nagpur APMC", state = "Maharashtra", trend = "up"
        )
        every { marketPriceDao.getAllMarketPrices() } returns flowOf(listOf(cached)) andThen flowOf(listOf(fresh.toEntity()))
        coEvery { api.getMarketPrices(any(), any()) } returns listOf(fresh)

        val repo = PricesRepository(api, marketPriceDao)
        repo.getMarketPrices().test {
            assertTrue(awaitItem() is Resource.Loading)
            val oldItem = awaitItem()
            assertTrue(oldItem is Resource.Success)
            assertEquals(2200.0, (oldItem as Resource.Success).data.first().price, 0.01)

            val freshItem = awaitItem()
            assertTrue(freshItem is Resource.Success)
            assertEquals(2450.0, (freshItem as Resource.Success).data.first().price, 0.01)
            awaitComplete()
        }
        coVerify { marketPriceDao.insertAll(any()) }
    }

    @Test
    fun `prices repository surfaces timeout error when cache is empty`() = runTest {
        every { marketPriceDao.getAllMarketPrices() } returns flowOf(emptyList())
        coEvery { api.getMarketPrices(any(), any()) } throws SocketTimeoutException("timeout")

        val repo = PricesRepository(api, marketPriceDao)
        repo.getMarketPrices().test {
            assertTrue(awaitItem() is Resource.Loading)
            val errorItem = awaitItem()
            assertTrue(errorItem is Resource.Error)
            val msg = (errorItem as Resource.Error).message
            assertTrue(msg.contains("timed out", ignoreCase = true))
            awaitComplete()
        }
    }

    // =========================================================================
    // 2. TradersRepository
    // =========================================================================

    @Test
    fun `traders repository emits cached immediately on subscribe`() = runTest {
        val cached = TraderEntity(
            id = "t_1", name = "Rajesh Traders", location = "Delhi",
            dealsIn = listOf("Wheat"), phone = "9876543210", rating = 4.5, reviewCount = 10
        )
        every { traderDao.getAllTraders() } returns flowOf(listOf(cached))
        coEvery { api.getTraders(any(), any()) } returns emptyList()

        val repo = TradersRepository(api, traderDao)
        repo.getTraders().test {
            assertTrue(awaitItem() is Resource.Loading)
            val cachedItem = awaitItem()
            assertTrue(cachedItem is Resource.Success)
            assertEquals("Rajesh Traders", (cachedItem as Resource.Success).data.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `traders repository preserves cache when background refresh fails`() = runTest {
        val cached = TraderEntity(
            id = "t_1", name = "Rajesh Traders", location = "Delhi",
            dealsIn = listOf("Wheat"), phone = "9876543210", rating = 4.5, reviewCount = 10
        )
        every { traderDao.getAllTraders() } returns flowOf(listOf(cached))
        coEvery { api.getTraders(any(), any()) } throws IOException("503 Service Unavailable")

        val repo = TradersRepository(api, traderDao)
        repo.getTraders().test {
            assertTrue(awaitItem() is Resource.Loading)
            val cachedItem = awaitItem()
            assertTrue(cachedItem is Resource.Success)
            assertEquals("Rajesh Traders", (cachedItem as Resource.Success).data.first().name)
            awaitComplete()
        }
        coVerify(exactly = 0) { traderDao.clearAll() }
    }

    @Test
    fun `traders repository writes through network data and re-emits on successful refresh`() = runTest {
        val cached = TraderEntity(
            id = "t_1", name = "Rajesh Traders", location = "Delhi",
            dealsIn = listOf("Wheat"), phone = "9876543210", rating = 4.5, reviewCount = 10
        )
        val fresh = Trader(
            id = "t_1", name = "Rajesh Kumar Mandi Traders", location = "Delhi APMC",
            dealsIn = listOf("Wheat", "Mustard"), phone = "9876543210", rating = 4.8, reviewCount = 15
        )
        every { traderDao.getAllTraders() } returns flowOf(listOf(cached)) andThen flowOf(listOf(fresh.toEntity()))
        coEvery { api.getTraders(any(), any()) } returns listOf(fresh)

        val repo = TradersRepository(api, traderDao)
        repo.getTraders().test {
            assertTrue(awaitItem() is Resource.Loading)
            val oldItem = awaitItem()
            assertTrue(oldItem is Resource.Success)
            assertEquals("Rajesh Traders", (oldItem as Resource.Success).data.first().name)

            val freshItem = awaitItem()
            assertTrue(freshItem is Resource.Success)
            assertEquals("Rajesh Kumar Mandi Traders", (freshItem as Resource.Success).data.first().name)
            awaitComplete()
        }
        coVerify { traderDao.insertAll(any()) }
    }

    // =========================================================================
    // 3. CropRepository (Crop History)
    // =========================================================================

    @Test
    fun `crop history repository emits cached immediately on subscribe`() = runTest {
        val cached = CropAnalysisEntity(
            id = "c_1", source = "model", cropName = "Wheat", diseaseName = "Yellow Rust",
            confidence = 0.92, severity = "Medium", summary = "Fungus detected",
            organicTreatment = listOf("Neem oil"), chemicalTreatment = listOf("Propiconazole"),
            prevention = listOf("Crop rotation"), language = "en", imagePath = "/path/leaf.jpg",
            createdAt = "2026-10-01"
        )
        every { cropDao.getAllHistory() } returns flowOf(listOf(cached))
        coEvery { api.getCropHistory() } returns emptyList()

        val repo = CropRepository(api, cropDao)
        repo.getHistory().test {
            assertTrue(awaitItem() is Resource.Loading)
            val cachedItem = awaitItem()
            assertTrue(cachedItem is Resource.Success)
            assertEquals("Yellow Rust", (cachedItem as Resource.Success).data.first().diseaseName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `crop history repository preserves cache when background refresh fails`() = runTest {
        val cached = CropAnalysisEntity(
            id = "c_1", source = "model", cropName = "Wheat", diseaseName = "Yellow Rust",
            confidence = 0.92, severity = "Medium", summary = "Fungus detected",
            organicTreatment = listOf("Neem oil"), chemicalTreatment = listOf("Propiconazole"),
            prevention = listOf("Crop rotation"), language = "en", imagePath = "/path/leaf.jpg",
            createdAt = "2026-10-01"
        )
        every { cropDao.getAllHistory() } returns flowOf(listOf(cached))
        coEvery { api.getCropHistory() } throws IOException("Timeout connecting to backend")

        val repo = CropRepository(api, cropDao)
        repo.getHistory().test {
            assertTrue(awaitItem() is Resource.Loading)
            val cachedItem = awaitItem()
            assertTrue(cachedItem is Resource.Success)
            assertEquals("Yellow Rust", (cachedItem as Resource.Success).data.first().diseaseName)
            awaitComplete()
        }
        coVerify(exactly = 0) { cropDao.clearAll() }
    }

    @Test
    fun `crop history repository writes through network data and re-emits on successful refresh`() = runTest {
        val cached = CropAnalysisEntity(
            id = "c_1", source = "model", cropName = "Wheat", diseaseName = "Yellow Rust",
            confidence = 0.92, severity = "Medium", summary = "Fungus detected",
            organicTreatment = listOf("Neem oil"), chemicalTreatment = listOf("Propiconazole"),
            prevention = listOf("Crop rotation"), language = "en", imagePath = "/path/leaf.jpg",
            createdAt = "2026-10-01"
        )
        val fresh = CropAnalysisResult(
            id = "c_2", source = "gemini", cropName = "Rice", diseaseName = "Blast",
            confidence = 0.88, severity = "High", summary = "Blast lesions",
            createdAt = "2026-10-05"
        )
        every { cropDao.getAllHistory() } returns flowOf(listOf(cached)) andThen flowOf(listOf(cached, fresh.toEntity()))
        coEvery { api.getCropHistory() } returns listOf(cached.toDomain(), fresh)

        val repo = CropRepository(api, cropDao)
        repo.getHistory().test {
            assertTrue(awaitItem() is Resource.Loading)
            val oldItem = awaitItem()
            assertTrue(oldItem is Resource.Success)
            assertEquals(1, (oldItem as Resource.Success).data.size)

            val freshItem = awaitItem()
            assertTrue(freshItem is Resource.Success)
            assertEquals(2, (freshItem as Resource.Success).data.size)
            awaitComplete()
        }
        coVerify { cropDao.insertAll(any()) }
    }

    // =========================================================================
    // 4. IoTRepository (Sensor Readings)
    // =========================================================================

    @Test
    fun `iot repository emits cached reading immediately on subscribe`() = runTest {
        val cached = IoTReadingEntity(
            id = "latest", temperature = 26.5, humidity = 60.0,
            soilMoisture = 40.0, phLevel = 6.5, timestamp = "2026-10-05T10:00:00Z"
        )
        every { ioTDao.getLatest() } returns flowOf(cached)
        coEvery { api.getIoTLive() } throws IOException("Offline")

        val repo = IoTRepository(api, ioTDao)
        repo.getLiveSensorData().test {
            assertTrue(awaitItem() is Resource.Loading)
            val cachedItem = awaitItem()
            assertTrue(cachedItem is Resource.Success)
            assertEquals(26.5, (cachedItem as Resource.Success).data.temperature ?: 0.0, 0.01)
            awaitComplete()
        }
    }

    @Test
    fun `iot repository preserves cache when background refresh fails`() = runTest {
        val cached = IoTReadingEntity(
            id = "latest", temperature = 27.0, humidity = 62.0,
            soilMoisture = 45.0, phLevel = 6.8, timestamp = "2026-10-05T12:00:00Z"
        )
        every { ioTDao.getLatest() } returns flowOf(cached)
        coEvery { api.getIoTLive() } throws SocketTimeoutException("Sensor gateway unreachable")

        val repo = IoTRepository(api, ioTDao)
        repo.getLiveSensorData().test {
            assertTrue(awaitItem() is Resource.Loading)
            val cachedItem = awaitItem()
            assertTrue(cachedItem is Resource.Success)
            assertEquals(27.0, (cachedItem as Resource.Success).data.temperature ?: 0.0, 0.01)
            awaitComplete()
        }
        coVerify(exactly = 0) { ioTDao.clearAll() }
    }

    @Test
    fun `iot repository writes through live reading and re-emits on successful refresh`() = runTest {
        val cached = IoTReadingEntity(
            id = "latest", temperature = 27.0, humidity = 62.0,
            soilMoisture = 45.0, phLevel = 6.8, timestamp = "2026-10-05T12:00:00Z"
        )
        val fresh = SensorData(
            temperature = 29.5, humidity = 68.0,
            soilMoisture = 50.0, phLevel = 7.0, timestamp = "2026-10-05T13:00:00Z"
        )
        every { ioTDao.getLatest() } returns flowOf(cached)
        coEvery { api.getIoTLive() } returns fresh

        val repo = IoTRepository(api, ioTDao)
        repo.getLiveSensorData().test {
            assertTrue(awaitItem() is Resource.Loading)
            val oldItem = awaitItem()
            assertTrue(oldItem is Resource.Success)
            assertEquals(27.0, (oldItem as Resource.Success).data.temperature ?: 0.0, 0.01)

            val freshItem = awaitItem()
            assertTrue(freshItem is Resource.Success)
            assertEquals(29.5, (freshItem as Resource.Success).data.temperature ?: 0.0, 0.01)
            awaitComplete()
        }
        coVerify { ioTDao.insert(match { it.temperature == 29.5 }) }
    }

    // =========================================================================
    // 5. ChatRepository
    // =========================================================================

    @Test
    fun `chat repository emits messages flow from Room cache immediately on subscribe`() = runTest {
        val message = ChatMessageEntity(
            id = "m_1", sender = "user", text = "How to treat yellow rust?", timestamp = 1000L
        )
        every { chatDao.getAllMessages() } returns flowOf(listOf(message))

        val repo = ChatRepository(api, chatDao)
        repo.getMessages().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("How to treat yellow rust?", list.first().text)
            awaitComplete()
        }
    }

    @Test
    fun `chat repository records user message and preserves cache when network fails`() = runTest {
        coEvery { api.chat(any()) } throws IOException("Network unreachable")

        val repo = ChatRepository(api, chatDao)
        repo.sendMessage("Hello bot", "en").test {
            assertTrue(awaitItem() is Resource.Loading)
            val errorItem = awaitItem()
            assertTrue(errorItem is Resource.Error)
            awaitComplete()
        }

        // Verify user message was written to cache before network call
        coVerify { chatDao.insert(match { it.sender == "user" && it.text == "Hello bot" }) }
        // Verify assistant error placeholder was also cached so history isn't lost
        coVerify { chatDao.insert(match { it.sender == "assistant" }) }
        coVerify(exactly = 0) { chatDao.clearAll() }
    }

    @Test
    fun `chat repository writes assistant response to cache and emits on success`() = runTest {
        coEvery { api.chat(any()) } returns ChatResponse("Use fungicide spray")

        val repo = ChatRepository(api, chatDao)
        repo.sendMessage("How to treat rust?", "en").test {
            assertTrue(awaitItem() is Resource.Loading)
            val successItem = awaitItem()
            assertTrue(successItem is Resource.Success)
            assertEquals("Use fungicide spray", (successItem as Resource.Success).data)
            awaitComplete()
        }

        coVerify { chatDao.insert(match { it.sender == "user" && it.text == "How to treat rust?" }) }
        coVerify { chatDao.insert(match { it.sender == "assistant" && it.text == "Use fungicide spray" }) }
    }

    // =========================================================================
    // Schemes & Loans Repositories (Static seeding)
    // =========================================================================

    @Test
    fun `schemes repository seeds from catalog when database is empty`() = runTest {
        coEvery { schemeDao.getCount() } returns 0
        every { schemeDao.getAllSchemes() } returns flowOf(emptyList())

        val repository = SchemesRepository(api, schemeDao)

        repository.getSchemes().test {
            assertTrue(awaitItem() is Resource.Loading)
            assertTrue(awaitItem() is Resource.Success)
            awaitComplete()
        }

        coVerify { schemeDao.insertAll(match { it.size >= 10 }) }
    }

    @Test
    fun `loans repository seeds from catalog when database is empty`() = runTest {
        coEvery { loanProductDao.getCount() } returns 0
        every { loanProductDao.getAllLoanProducts() } returns flowOf(emptyList())

        val repository = LoansRepository(api, loanProductDao)

        repository.getLoanProducts().test {
            assertTrue(awaitItem() is Resource.Loading)
            assertTrue(awaitItem() is Resource.Success)
            awaitComplete()
        }

        coVerify { loanProductDao.insertAll(match { it.size >= 5 }) }
    }
}
