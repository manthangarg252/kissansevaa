package com.kissansevaai.core.network

import com.kissansevaai.BuildConfig
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
        val reqBuilder = original.newBuilder()

        if (!token.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $token")
        }

        if (BuildConfig.DEBUG) {
            val customBaseUrl = runBlocking { dataStore.baseUrl.first() }
            val newUrl = CloudUrlValidator.resolveInterceptorOverrideUrl(
                isDebug = true,
                storedCustomUrl = customBaseUrl,
                currentRequestUrl = original.url,
            )
            if (newUrl != null) {
                reqBuilder.url(newUrl)
            }
        }

        return chain.proceed(reqBuilder.build())
    }
}
