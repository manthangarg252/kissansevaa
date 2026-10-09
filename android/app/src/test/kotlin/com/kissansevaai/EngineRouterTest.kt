package com.kissansevaai

import com.kissansevaai.core.ai.AiEngine
import com.kissansevaai.core.ai.EngineMode
import com.kissansevaai.core.ai.EngineRouter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EngineRouterTest {

    private class FakeAiEngine(
        override val engineId: String,
        private val available: Boolean = true,
        private val failOnInfer: Boolean = false,
        private val output: String = "result_from_$engineId",
    ) : AiEngine<String, String> {
        var inferCalled = false

        override suspend fun isAvailable(): Boolean = available

        override suspend fun infer(input: String): String {
            inferCalled = true
            if (failOnInfer) throw RuntimeException("Inference failure in $engineId")
            return output
        }
    }

    @Test
    fun `REMOTE mode always uses remote engine`() = runBlocking {
        val remote = FakeAiEngine("remote")
        val onDevice = FakeAiEngine("ondevice")
        val router = EngineRouter(remoteEngine = remote, onDeviceEngine = onDevice)

        val result = router.infer("input", EngineMode.REMOTE)
        assertEquals("result_from_remote", result)
        assertTrue(remote.inferCalled)
        assertFalse(onDevice.inferCalled)
    }

    @Test
    fun `AUTO mode falls back to remote when onDeviceEngine is null`() = runBlocking {
        val remote = FakeAiEngine("remote")
        val router = EngineRouter<String, String>(remoteEngine = remote, onDeviceEngine = null)

        val result = router.infer("input", EngineMode.AUTO)
        assertEquals("result_from_remote", result)
        assertTrue(remote.inferCalled)
    }

    @Test
    fun `AUTO mode falls back to remote when onDeviceEngine is unavailable`() = runBlocking {
        val remote = FakeAiEngine("remote")
        val onDevice = FakeAiEngine("ondevice", available = false)
        val router = EngineRouter(remoteEngine = remote, onDeviceEngine = onDevice)

        val result = router.infer("input", EngineMode.AUTO)
        assertEquals("result_from_remote", result)
        assertTrue(remote.inferCalled)
        assertFalse(onDevice.inferCalled)
    }

    @Test
    fun `AUTO mode falls back to remote when onDeviceEngine infer throws exception`() = runBlocking {
        val remote = FakeAiEngine("remote")
        val onDevice = FakeAiEngine("ondevice", available = true, failOnInfer = true)
        val router = EngineRouter(remoteEngine = remote, onDeviceEngine = onDevice)

        val result = router.infer("input", EngineMode.AUTO)
        assertEquals("result_from_remote", result)
        assertTrue(remote.inferCalled)
        assertTrue(onDevice.inferCalled)
    }

    @Test
    fun `ON_DEVICE mode falls back to remote with logged warning when onDeviceEngine is null`() = runBlocking {
        val remote = FakeAiEngine("remote")
        val router = EngineRouter<String, String>(remoteEngine = remote, onDeviceEngine = null)

        val result = router.infer("input", EngineMode.ON_DEVICE)
        assertEquals("result_from_remote", result)
        assertTrue(remote.inferCalled)
    }

    @Test
    fun `ON_DEVICE mode falls back to remote when onDeviceEngine is unavailable`() = runBlocking {
        val remote = FakeAiEngine("remote")
        val onDevice = FakeAiEngine("ondevice", available = false)
        val router = EngineRouter(remoteEngine = remote, onDeviceEngine = onDevice)

        val result = router.infer("input", EngineMode.ON_DEVICE)
        assertEquals("result_from_remote", result)
        assertTrue(remote.inferCalled)
        assertFalse(onDevice.inferCalled)
    }

    @Test
    fun `ON_DEVICE mode falls back to remote when onDeviceEngine infer throws exception`() = runBlocking {
        val remote = FakeAiEngine("remote")
        val onDevice = FakeAiEngine("ondevice", available = true, failOnInfer = true)
        val router = EngineRouter(remoteEngine = remote, onDeviceEngine = onDevice)

        val result = router.infer("input", EngineMode.ON_DEVICE)
        assertEquals("result_from_remote", result)
        assertTrue(remote.inferCalled)
        assertTrue(onDevice.inferCalled)
    }
}
