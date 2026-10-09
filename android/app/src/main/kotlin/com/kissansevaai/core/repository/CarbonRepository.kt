package com.kissansevaai.core.repository

import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.common.toUserFriendlyMessage
import com.kissansevaai.core.model.CarbonEstimateRequest
import com.kissansevaai.core.network.KissanApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CarbonRepository @Inject constructor(
    private val api: KissanApi,
) {
    /**
     * Calculates carbon credit estimations and advisory based on farm profile.
     */
    fun estimateCarbon(request: CarbonEstimateRequest): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = api.estimateCarbon(request)
            emit(Resource.Success(response.response))
        } catch (e: Exception) {
            emit(Resource.Error(e.toUserFriendlyMessage(), e))
        }
    }.flowOn(Dispatchers.IO)
}
