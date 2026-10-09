package com.kissansevaai.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.kissansevaai.core.model.CropAnalysisResult
import com.kissansevaai.core.model.CropTreatment
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
) {
    fun toDomain(): CropAnalysisResult = CropAnalysisResult(
        id = id,
        source = source,
        cropName = cropName,
        diseaseName = diseaseName,
        confidence = confidence,
        severity = severity,
        summary = summary,
        treatment = CropTreatment(organic = organicTreatment, chemical = chemicalTreatment),
        prevention = prevention,
        language = language,
        imagePath = imagePath,
        createdAt = createdAt,
    )
}

fun CropAnalysisResult.toEntity(): CropAnalysisEntity = CropAnalysisEntity(
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
)

@Dao
interface CropAnalysisDao {
    @Query("SELECT * FROM crop_history ORDER BY createdAt DESC")
    fun getAllHistory(): Flow<List<CropAnalysisEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CropAnalysisEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CropAnalysisEntity>)

    @Query("DELETE FROM crop_history")
    suspend fun clearAll()
}
