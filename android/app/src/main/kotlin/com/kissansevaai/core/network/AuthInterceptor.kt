package com.kissansevaai.core.network

import com.kissansevaai.core.data.DataStoreManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val dataStore: DataStoreManager,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = runBlocking { dataStore.token.first() }
        val request = if (!token.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}
