package com.kissansevaai.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.kissansevaai.core.model.LivestockResult
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "livestock_history")
data class LivestockAnalysisEntity(
    @PrimaryKey val id: String,
    val userId: String = "",
    val animalType: String,
    val conditionName: String? = null,
    val confidence: Double? = null,
    val source: String? = null,
    val severity: String? = null,
    val urgentVetEscalation: Boolean = false,
    val escalationReason: String? = null,
    val prescriptionsWithheld: Boolean = true,
    val safetyNotice: String? = null,
    val symptoms: List<String> = emptyList(),
    val immediateAction: String? = null,
    val longTermCare: String? = null,
    val vetConsultationAdvice: String? = null,
    val imagePath: String? = null,
    val aiAvailable: Boolean = true,
    val serviceStatus: String? = null,
    val createdAt: String? = null,
) {
    fun toDomain(): LivestockResult = LivestockResult(
        id = id,
        animalType = animalType,
        conditionName = conditionName,
        confidence = confidence,
        source = source,
        severity = severity,
        urgentVetEscalation = urgentVetEscalation,
        escalationReason = escalationReason,
        prescriptionsWithheld = prescriptionsWithheld,
        safetyNotice = safetyNotice,
        symptoms = symptoms,
        immediateAction = immediateAction,
        longTermCare = longTermCare,
        vetConsultationAdvice = vetConsultationAdvice,
        imageUrl = imagePath,
        aiAvailable = aiAvailable,
        serviceStatus = serviceStatus,
        createdAt = createdAt,
    )
}

fun LivestockResult.toEntity(userId: String = ""): LivestockAnalysisEntity = LivestockAnalysisEntity(
    id = id ?: UUID.randomUUID().toString(),
    userId = userId,
    animalType = animalType ?: "Livestock",
    conditionName = conditionName,
    confidence = confidence,
    source = source,
    severity = severity,
    urgentVetEscalation = urgentVetEscalation,
    escalationReason = escalationReason,
    prescriptionsWithheld = prescriptionsWithheld,
    safetyNotice = safetyNotice,
    symptoms = symptoms,
    immediateAction = immediateAction,
    longTermCare = longTermCare,
    vetConsultationAdvice = vetConsultationAdvice,
    imagePath = imageUrl,
    aiAvailable = aiAvailable,
    serviceStatus = serviceStatus,
    createdAt = createdAt,
)

@Dao
interface LivestockDao {
    @Query("SELECT * FROM livestock_history WHERE userId = :userId OR userId = '' ORDER BY createdAt DESC")
    fun getHistory(userId: String): Flow<List<LivestockAnalysisEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: LivestockAnalysisEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LivestockAnalysisEntity>)

    @Query("DELETE FROM livestock_history WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
