package com.kissansevaai

import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.network.UnauthorizedInterceptor
import io.mockk.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class UnauthorizedInterceptorTest {

    private val dataStore = mockk<DataStoreManager>(relaxed = true)
    private val interceptor = UnauthorizedInterceptor(dataStore)

    @Test
    fun `clears auth when authenticated endpoint returns 401`() {
        val request = Request.Builder()
            .url("https://api.kissansevaai.com/api/crop/history")
            .build()

        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("".toResponseBody())
            .build()

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns response

        val result = interceptor.intercept(chain)
        assertEquals(401, result.code)

        // Give coroutine a moment to execute
        Thread.sleep(100)
        coVerify(timeout = 1000) { dataStore.clearAuth() }
    }

    @Test
    fun `does not clear auth when login endpoint returns 401 for bad credentials`() {
        val request = Request.Builder()
            .url("https://api.kissansevaai.com/api/auth/login")
            .build()

        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("".toResponseBody())
            .build()

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns response

        val result = interceptor.intercept(chain)
        assertEquals(401, result.code)

        Thread.sleep(100)
        coVerify(exactly = 0) { dataStore.clearAuth() }
    }
}
