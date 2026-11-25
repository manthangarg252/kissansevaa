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

        var cachedEntities = cropDao.getAllHistory().first()
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
            cropDao.insertAll(initialResults.map { it.toEntity() })
            cachedEntities = cropDao.getAllHistory().first()
        }
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
            // Never fabricate a fake diagnosis when the backend/ML service fails or device is offline.
            emit(Resource.Error(e.toUserFriendlyMessage(), e))
        }
    }.flowOn(Dispatchers.IO)
}
