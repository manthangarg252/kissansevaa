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
import okhttp3.Response
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
     * Fallback interceptor for physical and emulator environments.
     * If the configured host fails to connect (e.g. phone on Wi-Fi vs ADB reverse over USB),
     * it automatically tries the active LAN IP (10.201.214.248) or localhost (127.0.0.1).
     */
    private val hostFallbackInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url
        val currentHost = originalUrl.host

        try {
            chain.proceed(originalRequest)
        } catch (e: IOException) {
            val fallbackCandidates = listOf("10.201.214.248", "127.0.0.1", "10.0.2.2")
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
                    return@Interceptor chain.proceed(retryRequest)
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
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS) // AI endpoints can be slow
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
                    "http://10.201.214.248:5050"
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
