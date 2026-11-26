package com.kissansevaai.core.repository

import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.common.toUserFriendlyMessage
import com.kissansevaai.core.data.CatalogData
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.database.*
import com.kissansevaai.core.model.*
import com.kissansevaai.core.network.KissanApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PricesRepository @Inject constructor(
    private val api: KissanApi,
    private val marketPriceDao: MarketPriceDao,
    private val marketFavoriteDao: MarketFavoriteDao? = null,
    private val syncDao: SyncOperationDao? = null,
    private val dataStore: DataStoreManager? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun getMarketPrices(
        crop: String? = null,
        state: String? = null,
        category: String? = null,
        refresh: Boolean = false,
        districtOrMarket: String? = null,
    ): Flow<Resource<List<MarketPrice>>> = flow {
        emit(Resource.Loading)

        var cachedEntities = marketPriceDao.getAllMarketPrices().first()
        if (cachedEntities.isEmpty()) {
            marketPriceDao.insertAll(CatalogData.DEFAULT_PRICES.map { it.toEntity() })
            cachedEntities = marketPriceDao.getAllMarketPrices().first()
        }

        val cached = cachedEntities.map { it.toDomain() }
        var emittedCache = false
        if (cached.isNotEmpty() && !refresh) {
            val filteredCached = filterPrices(cached, crop, state, category, districtOrMarket)
            emit(Resource.Success(filteredCached))
            emittedCache = true
        }

        try {
            val networkPrices = api.getMarketPrices(
                crop = crop,
                state = state,
                category = if (category.isNullOrBlank() || category.equals("all", ignoreCase = true)) null else category,
                refresh = if (refresh) true else null,
            )
            if (networkPrices.isNotEmpty()) {
                marketPriceDao.insertAll(networkPrices.map { it.toEntity() })
                val allUpdated = marketPriceDao.getAllMarketPrices().first().map { it.toDomain() }
                emit(Resource.Success(filterPrices(allUpdated, crop, state, category, districtOrMarket)))
            } else if (!emittedCache) {
                emit(Resource.Success(filterPrices(cached, crop, state, category, districtOrMarket)))
            }
        } catch (e: Exception) {
            if (cached.isEmpty()) {
                emit(Resource.Error(e.toUserFriendlyMessage(), e))
            } else if (!emittedCache) {
                emit(Resource.Success(filterPrices(cached, crop, state, category, districtOrMarket)))
            }
        }
    }.flowOn(Dispatchers.IO)

    fun getMarketStats(): Flow<Resource<MarketStatsDto>> = flow {
        emit(Resource.Loading)
        try {
            emit(Resource.Success(api.getMarketStats()))
        } catch (e: Exception) {
            emit(Resource.Error(e.toUserFriendlyMessage(), e))
        }
    }.flowOn(Dispatchers.IO)

    fun compareMarkets(crop: String? = null, category: String? = null): Flow<Resource<MarketCompareResponse>> = flow {
        emit(Resource.Loading)
        try {
            emit(Resource.Success(api.compareMarketPrices(crop = crop, category = category)))
        } catch (e: Exception) {
            emit(Resource.Error(e.toUserFriendlyMessage(), e))
        }
    }.flowOn(Dispatchers.IO)

    fun getFavorites(): Flow<Set<String>> = flow {
        val userId = dataStore?.userId?.first().orEmpty()
        val local = marketFavoriteDao?.getFavoritesForUser(userId)?.first()
            ?.map { "${it.crop.lowercase()}|${it.market.lowercase()}" }?.toSet().orEmpty()
        emit(local)
        try {
            val remote = api.getMarketFavorites()
            if (marketFavoriteDao != null) {
                val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                marketFavoriteDao.insertAll(
                    remote.map {
                        MarketFavoriteEntity(
                            id = "$userId:${it.crop}:${it.market}",
                            userId = userId,
                            crop = it.crop,
                            market = it.market,
                            state = it.state,
                            category = it.category,
                            createdAt = now,
                        )
                    }
                )
            }
            emit(remote.map { "${it.crop.lowercase()}|${it.market.lowercase()}" }.toSet())
        } catch (_: Exception) {
            // Preserve local Room favorites offline
        }
    }.flowOn(Dispatchers.IO)

    suspend fun toggleFavorite(item: MarketPrice, currentlyFavorite: Boolean) {
        val userId = dataStore?.userId?.first().orEmpty()
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        if (currentlyFavorite) {
            marketFavoriteDao?.remove(item.crop, item.market, userId)
            try {
                api.removeMarketFavorite(item.crop, item.market)
            } catch (_: Exception) {
                syncDao?.enqueue(
                    SyncOperationEntity(
                        operationId = "fav_rm_${userId}_${item.crop}_${item.market}",
                        userId = userId,
                        opType = "market_favorite_remove",
                        payloadJson = json.encodeToString(
                            mapOf("crop" to JsonPrimitive(item.crop), "market" to JsonPrimitive(item.market))
                        ),
                    )
                )
            }
        } else {
            marketFavoriteDao?.insert(
                MarketFavoriteEntity(
                    id = "$userId:${item.crop}:${item.market}",
                    userId = userId,
                    crop = item.crop,
                    market = item.market,
                    state = item.state,
                    category = item.effectiveCategory,
                    createdAt = now,
                    pendingSync = true,
                )
            )
            try {
                api.addMarketFavorite(
                    MarketFavoriteRequest(
                        crop = item.crop,
                        market = item.market,
                        state = item.state,
                        category = item.effectiveCategory,
                    )
                )
            } catch (_: Exception) {
                syncDao?.enqueue(
                    SyncOperationEntity(
                        operationId = "fav_add_${userId}_${item.crop}_${item.market}",
                        userId = userId,
                        opType = "market_favorite_add",
                        payloadJson = json.encodeToString(
                            mapOf(
                                "crop" to JsonPrimitive(item.crop),
                                "market" to JsonPrimitive(item.market),
                                "state" to JsonPrimitive(item.state),
                                "category" to JsonPrimitive(item.effectiveCategory),
                            )
                        ),
                    )
                )
            }
        }
    }

    /**
     * AI market strategy and crop recommendations — never returns fake hardcoded Cotton advice on failure.
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

    private fun filterPrices(
        prices: List<MarketPrice>,
        crop: String?,
        state: String?,
        category: String? = null,
        districtOrMarket: String? = null,
    ): List<MarketPrice> {
        return prices.filter { p ->
            val matchCrop = crop.isNullOrBlank() ||
                p.crop.contains(crop, ignoreCase = true) ||
                (p.crop.equals("Rice", ignoreCase = true) && crop.equals("Paddy", ignoreCase = true))
            val matchState = state.isNullOrBlank() || p.state.contains(state, ignoreCase = true)
            val matchDistrictOrMarket = districtOrMarket.isNullOrBlank() ||
                p.market.contains(districtOrMarket, ignoreCase = true) ||
                p.district.contains(districtOrMarket, ignoreCase = true) ||
                p.state.contains(districtOrMarket, ignoreCase = true)
            val matchCategory = category.isNullOrBlank() || category.equals("all", ignoreCase = true) ||
                p.effectiveCategory.equals(category, ignoreCase = true)
            matchCrop && matchState && matchDistrictOrMarket && matchCategory
        }
    }
}
