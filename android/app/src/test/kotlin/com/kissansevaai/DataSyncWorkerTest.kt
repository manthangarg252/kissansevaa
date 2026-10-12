package com.kissansevaai

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.database.*
import com.kissansevaai.core.model.MarketPrice
import com.kissansevaai.core.model.SensorData
import com.kissansevaai.core.model.Trader
import com.kissansevaai.core.network.KissanApi
import com.kissansevaai.core.repository.IoTRepository
import com.kissansevaai.core.repository.PricesRepository
import com.kissansevaai.core.repository.TradersRepository
import com.kissansevaai.core.sync.DataSyncWorker
import com.kissansevaai.core.sync.SyncWorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import io.mockk.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class DataSyncWorkerTest {

    private lateinit var worker: DataSyncWorker
    private val context: Context = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val entryPoint: SyncWorkerEntryPoint = mockk()

    private val api: KissanApi = mockk()
    private val marketPriceDao: MarketPriceDao = mockk()
    private val traderDao: TraderDao = mockk()
    private val ioTDao: IoTDao = mockk()

    // In-memory collections representing Room tables
    private val roomPrices = mutableListOf<MarketPriceEntity>()
    private val roomTraders = mutableListOf<TraderEntity>()
    private val roomIoT = mutableListOf<IoTReadingEntity>()

    private lateinit var fakePricesRepo: PricesRepository
    private lateinit var fakeTradersRepo: TradersRepository
    private lateinit var fakeIoTRepo: IoTRepository

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockkStatic(EntryPointAccessors::class)
        every { EntryPointAccessors.fromApplication(any(), SyncWorkerEntryPoint::class.java) } returns entryPoint

        roomPrices.clear()
        roomTraders.clear()
        roomIoT.clear()

        // Wire Room DAOs to update our in-memory collections
        every { marketPriceDao.getAllMarketPrices() } answers { flowOf(roomPrices.toList()) }
        coEvery { marketPriceDao.insertAll(any()) } answers {
            val list = firstArg<List<MarketPriceEntity>>()
            roomPrices.addAll(list)
        }

        every { traderDao.getAllTraders() } answers { flowOf(roomTraders.toList()) }
        coEvery { traderDao.insertAll(any()) } answers {
            val list = firstArg<List<TraderEntity>>()
            roomTraders.addAll(list)
        }

        every { ioTDao.getLatest() } answers { flowOf(roomIoT.lastOrNull()) }
        coEvery { ioTDao.insert(any()) } answers {
            val entity = firstArg<IoTReadingEntity>()
            roomIoT.add(entity)
        }

        fakePricesRepo = PricesRepository(api, marketPriceDao)
        fakeTradersRepo = TradersRepository(api, traderDao)
        fakeIoTRepo = IoTRepository(api, ioTDao)

        every { entryPoint.ioTRepository() } returns fakeIoTRepo
        every { entryPoint.pricesRepository() } returns fakePricesRepo
        every { entryPoint.tradersRepository() } returns fakeTradersRepo

        worker = DataSyncWorker(context, workerParams)
    }

    @Test
    fun `doWork syncs data with fake repositories, updates Room contents, and returns Success`() = runTest {
        // Assert Room is initially empty
        assertTrue("Room market_prices should be empty before sync", roomPrices.isEmpty())
        assertTrue("Room traders should be empty before sync", roomTraders.isEmpty())
        assertTrue("Room iot_readings should be empty before sync", roomIoT.isEmpty())

        // Setup network responses returned to fake repositories
        val mockPrices = listOf(
            MarketPrice(
                id = "mp_1", crop = "Wheat", price = 2300.0, modalPrice = 2300.0,
                minPrice = 2100.0, maxPrice = 2500.0, unit = "quintal",
                market = "Khanna", state = "Punjab", trend = "up",
                updatedAt = null, lastUpdated = null
            )
        )
        val mockTraders = listOf(
            Trader(
                id = "tr_1", name = "Punjab Agro Commodities",
                location = "Khanna Mandi, Punjab", dealsIn = listOf("Wheat"),
                phone = "+91 98123 45678", rating = 4.8, reviewCount = 28
            )
        )
        val mockSensor = SensorData(
            temperature = 28.5, humidity = 65.0,
            soilMoisture = 42.0, phLevel = 6.8, timestamp = "2026-10-12"
        )

        coEvery { api.getMarketPrices(any(), any()) } returns mockPrices
        coEvery { api.getTraders(any(), any()) } returns mockTraders
        coEvery { api.getIoTLive() } returns mockSensor

        // Execute background sync worker
        val result = worker.doWork()

        // Assert work succeeds
        assertEquals(Result.success(), result)

        // Assert Room contents changed
        assertEquals(1, roomPrices.size)
        assertEquals("Wheat", roomPrices.first().crop)
        assertEquals(2300.0, roomPrices.first().price, 0.01)

        assertEquals(1, roomTraders.size)
        assertEquals("Punjab Agro Commodities", roomTraders.first().name)

        assertEquals(1, roomIoT.size)
        assertEquals(28.5, roomIoT.first().temperature!!, 0.01)
        assertEquals(65.0, roomIoT.first().humidity!!, 0.01)
    }

    @Test
    fun `doWork returns Retry when network failure occurs`() = runTest {
        val failingIoTRepo = mockk<IoTRepository>()
        coEvery { failingIoTRepo.getLiveSensorData() } returns flow { throw IOException("Network error") }
        every { entryPoint.ioTRepository() } returns failingIoTRepo

        val result = worker.doWork()
        assertEquals(Result.retry(), result)
    }
}
