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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
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
            .addInterceptor(authInterceptor)
            .addInterceptor(unauthorizedInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS) // AI endpoints can be slow
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, dataStore: DataStoreManager): Retrofit {
        val baseUrl = runBlocking { dataStore.baseUrl.first() } ?: BuildConfig.BASE_URL
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
