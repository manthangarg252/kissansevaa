package com.kissansevaai.core.common

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>
    data object Loading : Resource<Nothing>
}

private val errorJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun extractHttpErrorBodyMessage(err: HttpException): String? {
    val raw = try {
        err.response()?.errorBody()?.string()
    } catch (_: Exception) {
        null
    } ?: return null

    return try {
        val obj = errorJson.parseToJsonElement(raw).jsonObject
        obj["error"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: obj["message"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

/**
 * Translates low-level exceptions into farmer-friendly, actionable messages
 * while preserving explicit server API error explanations (e.g., AI quota exhausted).
 */
fun Throwable.toUserFriendlyMessage(): String {
    return when (this) {
        is SocketTimeoutException -> "Request timed out. Please check your internet connection and try again."
        is UnknownHostException -> "Unable to connect to the server. Please check your internet connection."
        is ConnectException -> "Cannot connect to server. Please verify the server is running."
        is HttpException -> {
            extractHttpErrorBodyMessage(this) ?: when (code()) {
                401 -> "Session expired. Please log in again."
                403 -> "Access denied."
                404 -> "Requested information was not found."
                429 -> "Rate or AI quota limit reached. Please try again later."
                in 500..599 -> "Server is currently experiencing issues. Please try again later."
                else -> "Server error (${code()}). Please try again."
            }
        }
        is SerializationException -> "Unexpected response format from server. Please update the app or try again later."
        is IOException -> localizedMessage?.takeIf { it.isNotBlank() } ?: "Network communication error. Please try again."
        else -> localizedMessage?.takeIf { it.isNotBlank() } ?: "An unexpected error occurred. Please try again."
    }
}
