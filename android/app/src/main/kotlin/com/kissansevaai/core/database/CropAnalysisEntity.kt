package com.kissansevaai.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.kissansevaai.core.model.CropAnalysisResult
import com.kissansevaai.core.model.CropTreatment
import com.kissansevaai.core.model.SecondOpinionResult
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "crop_history")
data class CropAnalysisEntity(
    @PrimaryKey val id: String,
    val source: String?,
    val cropName: String?,
    val diseaseName: String,
    val confidence: Double?,
    val severity: String?,
    val summary: String?,
    val organicTreatment: List<String>,
    val chemicalTreatment: List<String>,
    val prevention: List<String>,
    val language: String?,
    val imagePath: String?,
    val createdAt: String?,
    val topCosineSimilarity: Double? = null,
    val neighborAgreement: Double? = null,
    val needsReview: Boolean = false,
    val treatmentWithheld: Boolean = false,
    val warning: String? = null,
    val confidenceType: String? = null,
    val predictionSource: String? = null,
    val advisorySource: String? = null,
    val userId: String = "",
    val secondOpinionSource: String? = null,
    val secondOpinionCropName: String? = null,
    val secondOpinionDiseaseName: String? = null,
    val secondOpinionConfidence: Double? = null,
    val secondOpinionSummary: String? = null,
    val reconciliationStatus: String? = null,
    val reconciliationNote: String? = null,
) {
    fun toDomain(): CropAnalysisResult {
        val sec = if (!secondOpinionSource.isNullOrBlank() || !secondOpinionDiseaseName.isNullOrBlank()) {
            SecondOpinionResult(
                requested = true,
                farmerConsented = true,
                source = secondOpinionSource,
                cropName = secondOpinionCropName,
                diseaseName = secondOpinionDiseaseName,
                confidence = secondOpinionConfidence,
                summary = secondOpinionSummary,
            )
        } else null

        return CropAnalysisResult(
            id = id,
            source = source,
            predictionSource = predictionSource,
            advisorySource = advisorySource,
            cropName = cropName,
            diseaseName = diseaseName,
            confidence = confidence,
            top_cosine_similarity = topCosineSimilarity,
            neighbor_agreement = neighborAgreement,
            needs_review = needsReview,
            treatmentWithheld = treatmentWithheld,
            warning = warning,
            confidence_type = confidenceType,
            severity = severity,
            summary = summary,
            treatment = CropTreatment(organic = organicTreatment, chemical = chemicalTreatment),
            prevention = prevention,
            language = language,
            imagePath = imagePath,
            createdAt = createdAt,
            secondOpinion = sec,
            reconciliationStatus = reconciliationStatus,
            reconciliationNote = reconciliationNote,
        )
    }
}

fun CropAnalysisResult.toEntity(userId: String = ""): CropAnalysisEntity = CropAnalysisEntity(
    id = id ?: UUID.randomUUID().toString(),
    source = source,
    cropName = cropName,
    diseaseName = diseaseName,
    confidence = confidence,
    severity = severity,
    summary = summary,
    organicTreatment = treatment.organic,
    chemicalTreatment = treatment.chemical,
    prevention = prevention,
    language = language,
    imagePath = imagePath,
    createdAt = createdAt,
    topCosineSimilarity = top_cosine_similarity,
    neighborAgreement = neighbor_agreement,
    needsReview = needs_review,
    treatmentWithheld = treatmentWithheld,
    warning = warning,
    confidenceType = confidence_type,
    predictionSource = predictionSource,
    advisorySource = advisorySource,
    userId = userId,
    secondOpinionSource = secondOpinion?.source,
    secondOpinionCropName = secondOpinion?.cropName,
    secondOpinionDiseaseName = secondOpinion?.diseaseName,
    secondOpinionConfidence = secondOpinion?.confidence,
    secondOpinionSummary = secondOpinion?.summary,
    reconciliationStatus = reconciliationStatus,
    reconciliationNote = reconciliationNote,
)

@Dao
interface CropAnalysisDao {
    @Query("SELECT * FROM crop_history ORDER BY createdAt DESC")
    fun getAllHistory(): Flow<List<CropAnalysisEntity>>

    @Query("SELECT * FROM crop_history WHERE userId = :userId OR userId = '' ORDER BY createdAt DESC")
    fun getHistoryForUser(userId: String): Flow<List<CropAnalysisEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CropAnalysisEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CropAnalysisEntity>)

    @Query("DELETE FROM crop_history")
    suspend fun clearAll()

    @Query("DELETE FROM crop_history WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
