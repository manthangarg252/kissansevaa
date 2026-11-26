package com.kissansevaai.core.repository

import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.common.toUserFriendlyMessage
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.database.CropAnalysisDao
import com.kissansevaai.core.database.toEntity
import com.kissansevaai.core.model.CropAnalysisResult
import com.kissansevaai.core.model.SecondOpinionResponse
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
    private val dataStore: DataStoreManager? = null,
) {
    private suspend fun activeUserId(): String = dataStore?.userId?.first().orEmpty()

    fun getHistory(): Flow<Resource<List<CropAnalysisResult>>> = flow {
        emit(Resource.Loading)
        val userId = activeUserId()

        var cachedEntities = if (userId.isNotBlank()) {
            cropDao.getHistoryForUser(userId).first()
        } else {
            cropDao.getAllHistory().first()
        }

        if (cachedEntities.isEmpty()) {
            val initialResults = listOf(
                CropAnalysisResult(
                    id = "init_1",
                    source = "sample",
                    predictionSource = "sample",
                    cropName = "Wheat",
                    diseaseName = "Yellow Rust (पीला रतुआ)",
                    confidence = null,
                    severity = "Medium",
                    summary = "Sample reference record: Yellow rust pustules observed on upper leaf blades.",
                    treatment = com.kissansevaai.core.model.CropTreatment(
                        organic = listOf("Neem oil spray 5ml/L", "Trichoderma viride soil application"),
                        chemical = listOf("Propiconazole 25% EC @ 1ml/L", "Tebuconazole 25.9% EC")
                    ),
                    prevention = listOf("Plant resistant cultivars", "Avoid excess nitrogen fertilizer", "Ensure field drainage")
                ),
                CropAnalysisResult(
                    id = "init_2",
                    source = "sample",
                    predictionSource = "sample",
                    cropName = "Tomato",
                    diseaseName = "Early Blight (अगेती झुलसा)",
                    confidence = null,
                    severity = "Low",
                    summary = "Sample reference record: Concentric dark brown rings on lower leaves.",
                    treatment = com.kissansevaai.core.model.CropTreatment(
                        organic = listOf("Bordeaux mixture 1%", "Pseudomonas fluorescens spray"),
                        chemical = listOf("Mancozeb 75% WP @ 2g/L", "Chlorothalonil 75% WP")
                    ),
                    prevention = listOf("Remove infected lower leaves", "Crop rotation with non-solanaceous crops", "Mulch base of plants")
                )
            )
            cropDao.insertAll(initialResults.map { it.toEntity(userId = "") })
            cachedEntities = if (userId.isNotBlank()) {
                cropDao.getHistoryForUser(userId).first()
            } else {
                cropDao.getAllHistory().first()
            }
        }
        val cached = cachedEntities.map { it.toDomain() }
        if (cached.isNotEmpty()) {
            emit(Resource.Success(cached))
        }

        try {
            val networkHistory = api.getCropHistory()
            if (networkHistory.isNotEmpty()) {
                cropDao.insertAll(networkHistory.map { it.toEntity(userId = userId) })
                val updated = (if (userId.isNotBlank()) cropDao.getHistoryForUser(userId).first() else cropDao.getAllHistory().first())
                    .map { it.toDomain() }
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

    fun analyzeCrop(
        image: MultipartBody.Part,
        location: RequestBody,
        soilType: RequestBody,
        language: RequestBody,
        cropHint: RequestBody? = null,
        symptomDescription: RequestBody? = null,
    ): Flow<Resource<CropAnalysisResult>> = flow {
        emit(Resource.Loading)
        val userId = activeUserId()
        try {
            val result = if (cropHint != null || symptomDescription != null) {
                api.analyzeCropWithDetails(
                    image = image,
                    location = location,
                    soilType = soilType,
                    language = language,
                    cropHint = cropHint,
                    symptomDescription = symptomDescription,
                )
            } else {
                api.analyzeCrop(
                    image = image,
                    location = location,
                    soilType = soilType,
                    language = language,
                )
            }
            cropDao.insert(result.toEntity(userId = userId))
            emit(Resource.Success(result))
        } catch (e: Exception) {
            emit(Resource.Error(e.toUserFriendlyMessage(), e))
        }
    }.flowOn(Dispatchers.IO)

    fun requestSecondOpinion(
        image: MultipartBody.Part?,
        analysisId: RequestBody?,
        farmerConsented: RequestBody,
        location: RequestBody,
        soilType: RequestBody,
        language: RequestBody,
        primaryCrop: RequestBody,
        primaryDisease: RequestBody,
    ): Flow<Resource<SecondOpinionResponse>> = flow {
        emit(Resource.Loading)
        try {
            val res = api.requestCropSecondOpinion(
                image = image,
                analysisId = analysisId,
                farmerConsented = farmerConsented,
                location = location,
                soilType = soilType,
                language = language,
                primaryCrop = primaryCrop,
                primaryDisease = primaryDisease,
            )
            emit(Resource.Success(res))
        } catch (e: Exception) {
            emit(Resource.Error(e.toUserFriendlyMessage(), e))
        }
    }.flowOn(Dispatchers.IO)
}
