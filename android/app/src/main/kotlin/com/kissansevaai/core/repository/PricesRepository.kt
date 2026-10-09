package com.kissansevaai.core.repository

import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.common.toUserFriendlyMessage
import com.kissansevaai.core.database.MarketPriceDao
import com.kissansevaai.core.database.toEntity
import com.kissansevaai.core.model.MarketPrice
import com.kissansevaai.core.model.MarketRecommendationRequest
import com.kissansevaai.core.network.KissanApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PricesRepository @Inject constructor(
    private val api: KissanApi,
    private val marketPriceDao: MarketPriceDao,
) {
    /**
     * Emits cached prices from Room immediately, then refreshes from network and writes through to Room.
     * Fixes previously swallowed network errors by propagating Resource.Error.
     */
    fun getMarketPrices(crop: String? = null, state: String? = null): Flow<Resource<List<MarketPrice>>> = flow {
        emit(Resource.Loading)

        val cachedEntities = marketPriceDao.getAllMarketPrices().first()
        val cached = cachedEntities.map { it.toDomain() }
        if (cached.isNotEmpty()) {
            val filteredCached = filterPrices(cached, crop, state)
            emit(Resource.Success(filteredCached))
        }

        try {
            val networkPrices = api.getMarketPrices(crop = crop, state = state)
            if (networkPrices.isNotEmpty()) {
                marketPriceDao.insertAll(networkPrices.map { it.toEntity() })
                val allUpdated = marketPriceDao.getAllMarketPrices().first().map { it.toDomain() }
                emit(Resource.Success(filterPrices(allUpdated, crop, state)))
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
     * AI market strategy and crop recommendations.
     */
    fun getMarketRecommendations(request: MarketRecommendationRequest): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = api.getMarketRecommendations(request)
            emit(Resource.Success(response.response))
        } catch (e: Exception) {
            emit(Resource.Error(e.toUserFriendlyMessage(), e))
        }
    }.flowOn(Dispatchers.IO)

    private fun filterPrices(prices: List<MarketPrice>, crop: String?, state: String?): List<MarketPrice> {
        return prices.filter { p ->
            val matchCrop = crop.isNullOrBlank() || p.crop.contains(crop, ignoreCase = true)
            val matchState = state.isNullOrBlank() || p.state.contains(state, ignoreCase = true)
            matchCrop && matchState
        }
    }
}
