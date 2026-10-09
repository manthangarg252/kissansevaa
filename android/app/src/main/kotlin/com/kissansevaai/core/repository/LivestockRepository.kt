package com.kissansevaai.core.repository

import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.common.toUserFriendlyMessage
import com.kissansevaai.core.model.LivestockResult
import com.kissansevaai.core.network.KissanApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MultipartBody
import okhttp3.RequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LivestockRepository @Inject constructor(
    private val api: KissanApi,
) {
    /**
     * Uploads livestock image with animal type to diagnose symptoms and care instructions.
     */
    fun analyzeLivestock(
        image: MultipartBody.Part,
        animalType: RequestBody,
        language: RequestBody,
    ): Flow<Resource<LivestockResult>> = flow {
        emit(Resource.Loading)
        try {
            val result = api.analyzeLivestock(image, animalType, language)
            emit(Resource.Success(result))
        } catch (e: Exception) {
            emit(Resource.Error(e.toUserFriendlyMessage(), e))
        }
    }.flowOn(Dispatchers.IO)
}
