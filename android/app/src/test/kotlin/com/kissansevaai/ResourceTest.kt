package com.kissansevaai

import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.common.toUserFriendlyMessage
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ResourceTest {

    @Test
    fun `socket timeout produces actionable message`() {
        val exception = SocketTimeoutException("timeout")
        val message = exception.toUserFriendlyMessage()
        assertTrue(message.contains("timed out", ignoreCase = true))
    }

    @Test
    fun `unknown host produces network error message`() {
        val exception = UnknownHostException("Unable to resolve host")
        val message = exception.toUserFriendlyMessage()
        assertTrue(message.contains("internet connection", ignoreCase = true))
    }

    @Test
    fun `connect exception produces server reachability message`() {
        val exception = ConnectException("Connection refused")
        val message = exception.toUserFriendlyMessage()
        assertTrue(message.contains("server", ignoreCase = true))
    }

    @Test
    fun `http 401 produces session expired message`() {
        val response = Response.error<String>(401, "".toResponseBody("application/json".toMediaType()))
        val exception = HttpException(response)
        val message = exception.toUserFriendlyMessage()
        assertEquals("Session expired. Please log in again.", message)
    }

    @Test
    fun `http 500 produces server error message`() {
        val response = Response.error<String>(500, "".toResponseBody("application/json".toMediaType()))
        val exception = HttpException(response)
        val message = exception.toUserFriendlyMessage()
        assertTrue(message.contains("Server is currently experiencing issues", ignoreCase = true))
    }

    @Test
    fun `serialization exception produces friendly message`() {
        val exception = SerializationException("Failed to decode JSON")
        val message = exception.toUserFriendlyMessage()
        assertTrue(message.contains("Unexpected response format", ignoreCase = true))
    }

    @Test
    fun `resource sealed hierarchy states`() {
        val loading: Resource<String> = Resource.Loading
        val success: Resource<String> = Resource.Success("Data")
        val error: Resource<String> = Resource.Error("Failed")

        assertTrue(loading is Resource.Loading)
        assertEquals("Data", (success as Resource.Success).data)
        assertEquals("Failed", (error as Resource.Error).message)
    }
}
