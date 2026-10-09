package com.kissansevaai.core.repository

import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.common.toUserFriendlyMessage
import com.kissansevaai.core.database.IoTDao
import com.kissansevaai.core.database.toEntity
import com.kissansevaai.core.model.IoTInsightsRequest
import com.kissansevaai.core.model.SensorData
import com.kissansevaai.core.network.KissanApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IoTRepository @Inject constructor(
    private val api: KissanApi,
    private val ioTDao: IoTDao,
) {
    /**
     * Emits cached sensor reading immediately, then refreshes from backend and updates Room.
     */
    fun getLiveSensorData(): Flow<Resource<SensorData>> = flow {
        emit(Resource.Loading)

        val cachedEntity = ioTDao.getLatest().first()
        if (cachedEntity != null) {
            emit(Resource.Success(cachedEntity.toDomain()))
        }

        try {
            val liveData = api.getIoTLive()
            ioTDao.insert(liveData.toEntity())
            emit(Resource.Success(liveData))
        } catch (e: Exception) {
            if (cachedEntity == null) {
                emit(Resource.Error(e.toUserFriendlyMessage(), e))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * AI analysis of current IoT sensor readings.
     */
    fun getInsights(request: IoTInsightsRequest): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = api.getIoTInsights(request)
            emit(Resource.Success(response.response))
        } catch (e: Exception) {
            emit(Resource.Error(e.toUserFriendlyMessage(), e))
        }
    }.flowOn(Dispatchers.IO)
}
