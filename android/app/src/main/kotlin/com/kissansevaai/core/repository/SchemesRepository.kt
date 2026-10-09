package com.kissansevaai.core.repository

import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.common.toUserFriendlyMessage
import com.kissansevaai.core.data.CatalogData
import com.kissansevaai.core.database.SchemeDao
import com.kissansevaai.core.database.toEntity
import com.kissansevaai.core.model.Scheme
import com.kissansevaai.core.model.SchemeRecommendationRequest
import com.kissansevaai.core.network.KissanApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchemesRepository @Inject constructor(
    private val api: KissanApi,
    private val schemeDao: SchemeDao,
) {
    /**
     * Emits cached schemes from Room immediately; seeds from catalog if empty.
     */
    fun getSchemes(): Flow<Resource<List<Scheme>>> = flow {
        emit(Resource.Loading)
        val count = schemeDao.getCount()
        if (count == 0) {
            schemeDao.insertAll(CatalogData.SCHEMES.map { it.toEntity() })
        }
        val cached = schemeDao.getAllSchemes().first().map { it.toDomain() }
        emit(Resource.Success(cached))
    }.flowOn(Dispatchers.IO)

    /**
     * Gets personalized AI scheme recommendations from backend.
     */
    fun getRecommendations(request: SchemeRecommendationRequest): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = api.getSchemeRecommendations(request)
            emit(Resource.Success(response.response))
        } catch (e: Exception) {
            emit(Resource.Error(e.toUserFriendlyMessage(), e))
        }
    }.flowOn(Dispatchers.IO)
}
