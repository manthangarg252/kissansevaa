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
    private val hostFallbackInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url
        val currentHost = originalUrl.host

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
                    return@Interceptor chain
                        .withConnectTimeout(3000, TimeUnit.MILLISECONDS)
                        .proceed(retryRequest)
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
        return OkHttpClient.Builder()
            .addInterceptor(hostFallbackInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(unauthorizedInterceptor)
            .addInterceptor(logging)
            .connectTimeout(5, TimeUnit.SECONDS)
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
        val configuredUrl = runBlocking { dataStore.baseUrl.first() }
        val baseUrl = configuredUrl ?: run {
            if (BuildConfig.DEBUG) {
                if (isProbablyEmulator()) {
                    "http://10.0.2.2:5050"
                } else {
                    // Default physical USB debugging via adb reverse tcp:5050 tcp:5050; falls back fast to LAN
                    "http://127.0.0.1:5050"
                }
            } else {
                BuildConfig.BASE_URL
            }
        }
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
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
