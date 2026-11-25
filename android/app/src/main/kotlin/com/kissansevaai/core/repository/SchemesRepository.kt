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
class SchemesRepository @Inject constructor(
    private val api: KissanApi,
    private val schemeDao: SchemeDao,
    private val savedSchemeDao: SavedSchemeDao? = null,
    private val syncDao: SyncOperationDao? = null,
    private val dataStore: DataStoreManager? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun getSchemes(): Flow<Resource<List<Scheme>>> = flow {
        emit(Resource.Loading)
        val count = schemeDao.getCount()
        if (count == 0) {
            schemeDao.insertAll(CatalogData.SCHEMES.map { it.toEntity() })
        }
        val cached = schemeDao.getAllSchemes().first().map { it.toDomain() }
        emit(Resource.Success(cached))

        try {
            val catalog = api.getSchemesCatalog()
            if (catalog.schemes.isNotEmpty()) {
                schemeDao.insertAll(catalog.schemes.map { it.toEntity() })
                emit(Resource.Success(catalog.schemes))
            }
        } catch (_: Exception) {
            // Preserve offline catalog cache silently
        }
    }.flowOn(Dispatchers.IO)

    fun checkEligibility(request: SchemeRecommendationRequest): Flow<Resource<SchemeEligibilityResponse>> = flow {
        emit(Resource.Loading)
        try {
            val response = api.checkSchemeEligibility(request)
            emit(Resource.Success(response))
        } catch (e: Exception) {
            emit(Resource.Error(e.toUserFriendlyMessage(), e))
        }
    }.flowOn(Dispatchers.IO)

    fun getRecommendations(request: SchemeRecommendationRequest): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = api.getSchemeRecommendations(request)
            emit(Resource.Success(response.response))
        } catch (e: Exception) {
            emit(Resource.Error(e.toUserFriendlyMessage(), e))
        }
    }.flowOn(Dispatchers.IO)

    fun explainScheme(schemeId: String, language: String): Flow<Resource<SchemeExplainResponse>> = flow {
        emit(Resource.Loading)
        try {
            val response = api.explainScheme(SchemeExplainRequest(schemeId = schemeId, language = language))
            emit(Resource.Success(response))
        } catch (e: Exception) {
            emit(Resource.Error(e.toUserFriendlyMessage(), e))
        }
    }.flowOn(Dispatchers.IO)

    fun getSavedSchemeIds(): Flow<Set<String>> = flow {
        val userId = dataStore?.userId?.first().orEmpty()
        val local = savedSchemeDao?.getSavedForUser(userId)?.first()?.map { it.schemeId }?.toSet().orEmpty()
        emit(local)
        try {
            val remote = api.getSavedSchemes()
            if (savedSchemeDao != null) {
                val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                savedSchemeDao.insertAll(
                    remote.map {
                        SavedSchemeEntity(
                            id = "$userId:${it.schemeId}",
                            userId = userId,
                            schemeId = it.schemeId,
                            status = it.status,
                            notes = it.notes,
                            savedAt = it.createdAt ?: now,
                        )
                    }
                )
            }
            emit(remote.map { it.schemeId }.toSet())
        } catch (_: Exception) {
            // Keep cached saved IDs
        }
    }.flowOn(Dispatchers.IO)

    suspend fun toggleSaveScheme(schemeId: String, currentlySaved: Boolean) {
        val userId = dataStore?.userId?.first().orEmpty()
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        if (currentlySaved) {
            savedSchemeDao?.remove(schemeId, userId)
            try {
                api.removeSavedScheme(schemeId)
            } catch (_: Exception) {
                syncDao?.enqueue(
                    SyncOperationEntity(
                        operationId = "scheme_unsave_${userId}_$schemeId",
                        userId = userId,
                        opType = "scheme_unsave",
                        payloadJson = json.encodeToString(mapOf("schemeId" to JsonPrimitive(schemeId))),
                    )
                )
            }
        } else {
            savedSchemeDao?.insert(
                SavedSchemeEntity(
                    id = "$userId:$schemeId",
                    userId = userId,
                    schemeId = schemeId,
                    savedAt = now,
                    pendingSync = true,
                )
            )
            try {
                api.saveScheme(SaveSchemeRequest(schemeId = schemeId))
            } catch (_: Exception) {
                syncDao?.enqueue(
                    SyncOperationEntity(
                        operationId = "scheme_save_${userId}_$schemeId",
                        userId = userId,
                        opType = "scheme_save",
                        payloadJson = json.encodeToString(mapOf("schemeId" to JsonPrimitive(schemeId))),
                    )
                )
            }
        }
    }
}
