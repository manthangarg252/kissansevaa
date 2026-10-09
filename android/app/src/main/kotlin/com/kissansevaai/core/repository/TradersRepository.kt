package com.kissansevaai.core.repository

import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.common.toUserFriendlyMessage
import com.kissansevaai.core.database.TraderDao
import com.kissansevaai.core.database.toEntity
import com.kissansevaai.core.model.Trader
import com.kissansevaai.core.model.TraderAdvisoryRequest
import com.kissansevaai.core.network.KissanApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TradersRepository @Inject constructor(
    private val api: KissanApi,
    private val traderDao: TraderDao,
) {
    /**
     * Emits cached traders from Room immediately, then fetches fresh traders from network
     * and writes through to Room cache.
     */
    fun getTraders(crop: String? = null, state: String? = null): Flow<Resource<List<Trader>>> = flow {
        emit(Resource.Loading)

        val cachedEntities = traderDao.getAllTraders().first()
        val cached = cachedEntities.map { it.toDomain() }
        if (cached.isNotEmpty()) {
            val filteredCached = filterTraders(cached, crop, state)
            emit(Resource.Success(filteredCached))
        }

        try {
            val networkTraders = api.getTraders(search = crop, location = state)
            if (networkTraders.isNotEmpty()) {
                traderDao.insertAll(networkTraders.map { it.toEntity() })
                val allUpdated = traderDao.getAllTraders().first().map { it.toDomain() }
                emit(Resource.Success(filterTraders(allUpdated, crop, state)))
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
     * AI advisory chat for trader and mandi queries.
     */
    fun getTraderAdvisory(query: String, language: String): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = api.getTraderAdvisory(TraderAdvisoryRequest(query, language))
            emit(Resource.Success(response.response))
        } catch (e: Exception) {
            emit(Resource.Error(e.toUserFriendlyMessage(), e))
        }
    }.flowOn(Dispatchers.IO)

    private fun filterTraders(traders: List<Trader>, crop: String?, state: String?): List<Trader> {
        return traders.filter { trader ->
            val matchCrop = crop.isNullOrBlank() || trader.dealsIn.any { it.contains(crop, ignoreCase = true) }
            val matchState = state.isNullOrBlank() || trader.location.contains(state, ignoreCase = true)
            matchCrop && matchState
        }
    }
}
