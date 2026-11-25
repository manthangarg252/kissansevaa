package com.kissansevaai.core.repository

import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.common.toUserFriendlyMessage
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.database.LivestockDao
import com.kissansevaai.core.database.toEntity
import com.kissansevaai.core.model.LivestockResult
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
class LivestockRepository @Inject constructor(
    private val api: KissanApi,
    private val livestockDao: LivestockDao? = null,
    private val dataStore: DataStoreManager? = null,
) {
    private suspend fun activeUserId(): String = dataStore?.userId?.first().orEmpty()

    fun getHistory(): Flow<Resource<List<LivestockResult>>> = flow {
        emit(Resource.Loading)
        val userId = activeUserId()
        val cached = livestockDao?.getHistory(userId)?.first()?.map { it.toDomain() }.orEmpty()
        if (cached.isNotEmpty()) {
            emit(Resource.Success(cached))
        }

        try {
            val remote = api.getLivestockHistory()
            if (remote.isNotEmpty() && livestockDao != null) {
                livestockDao.insertAll(remote.map { it.toEntity(userId = userId) })
                emit(Resource.Success(livestockDao.getHistory(userId).first().map { it.toDomain() }))
            } else if (cached.isEmpty()) {
                emit(Resource.Success(remote))
            }
        } catch (e: Exception) {
            if (cached.isEmpty()) {
                emit(Resource.Error(e.toUserFriendlyMessage(), e))
            }
        }
    }.flowOn(Dispatchers.IO)

    fun analyzeLivestock(
        image: MultipartBody.Part,
        animalType: RequestBody,
        language: RequestBody,
    ): Flow<Resource<LivestockResult>> = flow {
        emit(Resource.Loading)
        val userId = activeUserId()
        try {
            val result = api.analyzeLivestock(image, animalType, language)
            livestockDao?.insert(result.toEntity(userId = userId))
            emit(Resource.Success(result))
        } catch (e: Exception) {
            emit(Resource.Error(e.toUserFriendlyMessage(), e))
        }
    }.flowOn(Dispatchers.IO)

    fun analyzeLivestockDetailed(
        image: MultipartBody.Part?,
        animalType: RequestBody,
        animalAge: RequestBody,
        symptomDuration: RequestBody,
        feverStatus: RequestBody,
        appetiteStatus: RequestBody,
        milkYieldChange: RequestBody,
        lesionsOrUlcers: RequestBody,
        isolatedFromHerd: RequestBody,
        symptomsNotes: RequestBody,
        language: RequestBody,
    ): Flow<Resource<LivestockResult>> = flow {
        emit(Resource.Loading)
        val userId = activeUserId()
        try {
            val result = api.analyzeLivestockDetailed(
                image = image,
                animalType = animalType,
                animalAge = animalAge,
                symptomDuration = symptomDuration,
                feverStatus = feverStatus,
                appetiteStatus = appetiteStatus,
                milkYieldChange = milkYieldChange,
                lesionsOrUlcers = lesionsOrUlcers,
                isolatedFromHerd = isolatedFromHerd,
                symptomsNotes = symptomsNotes,
                language = language,
            )
            livestockDao?.insert(result.toEntity(userId = userId))
            emit(Resource.Success(result))
        } catch (e: Exception) {
            emit(Resource.Error(e.toUserFriendlyMessage(), e))
        }
    }.flowOn(Dispatchers.IO)
}
