package com.kissansevaai

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.kissansevaai.core.sync.DataSyncWorker
import com.kissansevaai.core.sync.SyncWorkerEntryPoint
import com.kissansevaai.core.repository.IoTRepository
import com.kissansevaai.core.repository.PricesRepository
import com.kissansevaai.core.repository.TradersRepository
import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.model.MarketPrice
import com.kissansevaai.core.model.Trader
import dagger.hilt.android.EntryPointAccessors
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class DataSyncWorkerTest {

    private lateinit var worker: DataSyncWorker
    private val context: Context = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val entryPoint: SyncWorkerEntryPoint = mockk()
    
    private val ioTRepo: IoTRepository = mockk()
    private val pricesRepo: PricesRepository = mockk()
    private val tradersRepo: TradersRepository = mockk()

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        
        mockkStatic(EntryPointAccessors::class)
        every { EntryPointAccessors.fromApplication(any(), SyncWorkerEntryPoint::class.java) } returns entryPoint
        
        every { entryPoint.ioTRepository() } returns ioTRepo
        every { entryPoint.pricesRepository() } returns pricesRepo
        every { entryPoint.tradersRepository() } returns tradersRepo
        
        worker = DataSyncWorker(context, workerParams)
    }

    @Test
    fun `doWork returns Success when all syncs complete successfully`() = runTest {
        coEvery { ioTRepo.getLiveSensorData() } returns flowOf(Resource.Success(mockk()))
        coEvery { pricesRepo.getMarketPrices(any(), any()) } returns flowOf(Resource.Success(emptyList<MarketPrice>()))
        coEvery { tradersRepo.getTraders(any(), any()) } returns flowOf(Resource.Success(emptyList<Trader>()))
        
        val result = worker.doWork()
        assertEquals(Result.success(), result)
    }

    @Test
    fun `doWork returns Retry when network failure occurs`() = runTest {
        coEvery { ioTRepo.getLiveSensorData() } returns flow { throw IOException("Network error") }
        coEvery { pricesRepo.getMarketPrices(any(), any()) } returns flowOf(Resource.Success(emptyList<MarketPrice>()))
        coEvery { tradersRepo.getTraders(any(), any()) } returns flowOf(Resource.Success(emptyList<Trader>()))
        
        val result = worker.doWork()
        assertEquals(Result.retry(), result)
    }
}
