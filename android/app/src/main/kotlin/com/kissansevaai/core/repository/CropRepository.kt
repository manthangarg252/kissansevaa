package com.kissansevaai.core.repository

import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.common.toUserFriendlyMessage
import com.kissansevaai.core.database.CropAnalysisDao
import com.kissansevaai.core.database.toEntity
import com.kissansevaai.core.model.CropAnalysisResult
import com.kissansevaai.core.network.KissanApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MultipartBody
import okhttp3.RequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CropRepository @Inject constructor(
    private val api: KissanApi,
    private val cropDao: CropAnalysisDao,
) {
    /**
     * Emits cached history from Room immediately, then refreshes from backend and updates Room.
     */
    fun getHistory(): Flow<Resource<List<CropAnalysisResult>>> = flow {
        emit(Resource.Loading)

        val cachedEntities = cropDao.getAllHistory().first()
        val cached = cachedEntities.map { it.toDomain() }
        if (cached.isNotEmpty()) {
            emit(Resource.Success(cached))
        }

        try {
            val networkHistory = api.getCropHistory()
            if (networkHistory.isNotEmpty()) {
                cropDao.insertAll(networkHistory.map { it.toEntity() })
                val updated = cropDao.getAllHistory().first().map { it.toDomain() }
                emit(Resource.Success(updated))
            } else if (cached.isEmpty()) {
                emit(Resource.Success(emptyList()))
            }
        } catch (e: Exception) {
            if (cached.isEmpty()) {
                emit(Resource.Error(e.toUserFriendlyMessage(), e))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Uploads crop image for disease analysis, caches the resulting diagnosis in Room.
     */
    fun analyzeCrop(
        image: MultipartBody.Part,
        location: RequestBody,
        soilType: RequestBody,
        language: RequestBody,
    ): Flow<Resource<CropAnalysisResult>> = flow {
        emit(Resource.Loading)
        try {
            val result = api.analyzeCrop(
                image = image,
                location = location,
                soilType = soilType,
                language = language,
            )
            cropDao.insert(result.toEntity())
            emit(Resource.Success(result))
        } catch (e: Exception) {
            emit(Resource.Error(e.toUserFriendlyMessage(), e))
        }
    }.flowOn(Dispatchers.IO)
}
