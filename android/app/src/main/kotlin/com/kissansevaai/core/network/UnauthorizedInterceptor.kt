package com.kissansevaai.core.network

import com.kissansevaai.core.data.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnauthorizedInterceptor @Inject constructor(
    private val dataStore: DataStoreManager,
) : Interceptor {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 401) {
            val path = request.url.encodedPath
            // Ignore 401 on login or register (invalid user credentials, not an expired session)
            if (!path.endsWith("/login") && !path.endsWith("/register")) {
                scope.launch {
                    dataStore.clearAuth()
                }
            }
        }

        return response
    }
}
