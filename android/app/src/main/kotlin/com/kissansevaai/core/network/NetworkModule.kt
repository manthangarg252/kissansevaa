package com.kissansevaai.core.network

import com.kissansevaai.BuildConfig
import com.kissansevaai.core.data.DataStoreManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideJson(): Json = json

    /**
     * Fast fallback interceptor for emulator (10.0.2.2) and USB-tethered physical device (127.0.0.1)
     * plus active LAN fallback with bounded 3s connect timeout per retry candidate.
     */
    @Volatile
    private var lastWorkingFallbackHost: String? = null

    private val hostFallbackInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url
        val currentHost = originalUrl.host
        val preferredHost = lastWorkingFallbackHost

        if (preferredHost != null && preferredHost != currentHost) {
            val fastUrl = originalUrl.newBuilder()
                .host(preferredHost)
                .port(originalUrl.port)
                .build()
            try {
                return@Interceptor chain
                    .withConnectTimeout(2500, TimeUnit.MILLISECONDS)
                    .proceed(originalRequest.newBuilder().url(fastUrl).build())
            } catch (_: IOException) {
                lastWorkingFallbackHost = null
            }
        }

        try {
            chain.proceed(originalRequest)
        } catch (e: IOException) {
            val fallbackCandidates = listOf("127.0.0.1", "10.0.2.2", "192.168.1.7")
            for (targetHost in fallbackCandidates) {
                if (targetHost == currentHost) continue
                val fallbackUrl = originalUrl.newBuilder()
                    .host(targetHost)
                    .port(originalUrl.port)
                    .build()
                val retryRequest = originalRequest.newBuilder()
                    .url(fallbackUrl)
                    .build()
                try {
                    val resp = chain
                        .withConnectTimeout(2500, TimeUnit.MILLISECONDS)
                        .proceed(retryRequest)
                    lastWorkingFallbackHost = targetHost
                    return@Interceptor resp
                } catch (_: IOException) {
                    // Try next candidate
                }
            }
            throw e
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        unauthorizedInterceptor: UnauthorizedInterceptor,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        val builder = OkHttpClient.Builder()
            // AuthInterceptor MUST run before hostFallbackInterceptor so DataStore baseUrl rewrite does not overwrite fallback retry hosts
            .addInterceptor(authInterceptor)
        if (CloudUrlValidator.shouldAttachDevHostFallback(BuildConfig.DEBUG)) {
            builder.addInterceptor(hostFallbackInterceptor)
        }
        return builder
            .addInterceptor(unauthorizedInterceptor)
            .addInterceptor(logging)
            .connectTimeout(if (BuildConfig.DEBUG) 4L else 12L, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun isProbablyEmulator(): Boolean {
        val f = android.os.Build.FINGERPRINT
        val m = android.os.Build.MODEL
        val h = android.os.Build.HARDWARE
        return f.startsWith("generic") || f.startsWith("unknown") ||
            m.contains("google_sdk") || m.contains("Emulator") || m.contains("Android SDK built for x86") ||
            h.contains("goldfish") || h.contains("ranchu")
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, dataStore: DataStoreManager): Retrofit {
        val configuredUrl = if (BuildConfig.DEBUG) {
            runBlocking { dataStore.baseUrl.first() }
        } else {
            null
        }
        val url = CloudUrlValidator.resolveEffectiveBaseUrl(
            isDebug = BuildConfig.DEBUG,
            buildConfigBaseUrl = BuildConfig.BASE_URL,
            storedCustomUrl = configuredUrl,
            isEmulator = isProbablyEmulator(),
        )
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideKissanApi(retrofit: Retrofit): KissanApi {
        return retrofit.create(KissanApi::class.java)
    }
}
