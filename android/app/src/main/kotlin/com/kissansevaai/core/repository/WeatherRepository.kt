package com.kissansevaai.core.repository

import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.common.toUserFriendlyMessage
import com.kissansevaai.core.database.WeatherCacheEntity
import com.kissansevaai.core.database.WeatherDao
import com.kissansevaai.core.model.WeatherAdvisory
import com.kissansevaai.core.model.WeatherAlertRequest
import com.kissansevaai.core.model.WeatherCurrent
import com.kissansevaai.core.model.WeatherDaily
import com.kissansevaai.core.model.WeatherForecast
import com.kissansevaai.core.network.KissanApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepository @Inject constructor(
    private val api: KissanApi,
    private val weatherDao: WeatherDao,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun getForecast(
        location: String? = null,
        language: String = "en",
        forceRefresh: Boolean = false,
        includeAi: Boolean = false,
    ): Flow<Resource<WeatherForecast>> = flow {
        emit(Resource.Loading)

        val cachedEntity = weatherDao.getLatest().first()
        if (cachedEntity != null && !forceRefresh) {
            emit(Resource.Success(cachedEntity.toDomainModel(isStaleHint = true)))
        }

        try {
            val fresh = api.getWeatherForecast(
                location = location?.takeIf { it.isNotBlank() },
                language = language,
                refresh = if (forceRefresh) true else null,
                includeAi = includeAi,
            )
            weatherDao.insert(fresh.toCacheEntity())
            emit(Resource.Success(fresh))
        } catch (e: Exception) {
            if (cachedEntity != null) {
                emit(
                    Resource.Success(
                        cachedEntity.toDomainModel(
                            isStaleHint = true,
                            staleMessage = "Offline/provider unreachable; showing cached weather from ${cachedEntity.fetchedAt}.",
                        ),
                    ),
                )
            } else {
                emit(Resource.Error(e.toUserFriendlyMessage(), e))
            }
        }
    }.flowOn(Dispatchers.IO)

    fun evaluateFarmAlerts(request: WeatherAlertRequest): Flow<Resource<WeatherForecast>> = flow {
        emit(Resource.Loading)
        try {
            val fresh = api.postWeatherAlerts(request)
            weatherDao.insert(fresh.toCacheEntity())
            emit(Resource.Success(fresh))
        } catch (e: Exception) {
            val cachedEntity = weatherDao.getLatest().first()
            if (cachedEntity != null) {
                emit(Resource.Success(cachedEntity.toDomainModel(isStaleHint = true)))
            } else {
                emit(Resource.Error(e.toUserFriendlyMessage(), e))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun WeatherForecast.toCacheEntity(): WeatherCacheEntity = WeatherCacheEntity(
        locationKey = locationKey.ifBlank { locationName.ifBlank { "default" } },
        locationName = locationName,
        latitude = latitude,
        longitude = longitude,
        provider = provider,
        temperatureC = current.temperatureC,
        humidityPct = current.humidityPct,
        windSpeedKmh = current.windSpeedKmh,
        precipitationMm = current.precipitationMm,
        conditionLabel = current.conditionLabel,
        advisoriesJson = json.encodeToString(advisories),
        dailyJson = json.encodeToString(daily),
        aiSummary = aiSummary,
        fetchedAt = fetchedAt,
        expiresAt = expiresAt,
        isStale = isStale,
    )

    private fun WeatherCacheEntity.toDomainModel(
        isStaleHint: Boolean = false,
        staleMessage: String? = null,
    ): WeatherForecast {
        val parsedAdv = try {
            json.decodeFromString<List<WeatherAdvisory>>(advisoriesJson)
        } catch (_: Exception) {
            emptyList()
        }
        val parsedDaily = try {
            json.decodeFromString<List<WeatherDaily>>(dailyJson)
        } catch (_: Exception) {
            emptyList()
        }
        val sprayOk = parsedAdv.firstOrNull { it.sprayWindowSuitable != null }?.sprayWindowSuitable ?: true
        return WeatherForecast(
            locationKey = locationKey,
            locationName = locationName,
            latitude = latitude,
            longitude = longitude,
            provider = provider,
            current = WeatherCurrent(
                temperatureC = temperatureC,
                humidityPct = humidityPct,
                windSpeedKmh = windSpeedKmh,
                precipitationMm = precipitationMm,
                conditionLabel = conditionLabel,
            ),
            daily = parsedDaily,
            advisories = parsedAdv,
            sprayWindowSuitable = sprayOk,
            aiSummary = aiSummary,
            fetchedAt = fetchedAt,
            expiresAt = expiresAt,
            source = if (isStaleHint) "cached" else "live",
            isStale = isStale || isStaleHint,
            staleReason = staleMessage,
        )
    }
}
