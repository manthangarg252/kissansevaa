package com.kissansevaai.core.network

import com.kissansevaai.core.data.DataStoreManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
        val customBaseUrl = runBlocking { dataStore.baseUrl.first() }

        val reqBuilder = original.newBuilder()

        if (!token.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $token")
        }

        if (!customBaseUrl.isNullOrBlank()) {
            val target = customBaseUrl.trim().toHttpUrlOrNull()
            if (target != null) {
                val newUrl = original.url.newBuilder()
                    .scheme(target.scheme)
                    .host(target.host)
                    .port(target.port)
                    .build()
                reqBuilder.url(newUrl)
            }
        }

        return chain.proceed(reqBuilder.build())
    }
}
