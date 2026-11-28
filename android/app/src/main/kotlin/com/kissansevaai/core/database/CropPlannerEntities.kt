package com.kissansevaai.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.kissansevaai.core.model.CropPlanDto
import com.kissansevaai.core.model.PlannerTaskDto
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "crop_plans")
data class CropPlanEntity(
    @PrimaryKey val id: String,
    val userId: String = "",
    val cropName: String,
    val variety: String = "",
    val stage: String = "vegetative",
    val soilType: String = "Loamy",
    val irrigationType: String = "Drip / Canal",
    val farmSizeAcres: Double = 1.0,
    val locationName: String = "India",
    val sowingDate: String = "",
    val customTask: String = "",
    val startDate: String = "",
    val status: String = "active",
    val generationSource: String = "rules_only",
    val aiAdvisoryNote: String? = null,
    val updatedAt: String = "",
    val pendingSync: Boolean = false,
) {
    fun toDomain(): CropPlanDto = CropPlanDto(
        id = id,
        cropName = cropName,
        variety = variety,
        stage = stage,
        soilType = soilType,
        irrigationType = irrigationType,
        farmSizeAcres = farmSizeAcres,
        locationName = locationName,
        sowingDate = sowingDate,
        customTask = customTask,
        startDate = startDate,
        status = status,
        generationSource = generationSource,
        aiAdvisoryNote = aiAdvisoryNote,
        updatedAt = updatedAt,
    )
}

fun CropPlanDto.toEntity(userId: String = "", pendingSync: Boolean = false): CropPlanEntity = CropPlanEntity(
    id = id ?: UUID.randomUUID().toString(),
    userId = userId,
    cropName = cropName,
    variety = variety,
    stage = stage,
    soilType = soilType,
    irrigationType = irrigationType,
    farmSizeAcres = farmSizeAcres,
    locationName = locationName,
    sowingDate = sowingDate,
    customTask = customTask,
    startDate = startDate,
    status = status,
    generationSource = generationSource,
    aiAdvisoryNote = aiAdvisoryNote,
    updatedAt = updatedAt ?: "",
    pendingSync = pendingSync,
)

@Entity(tableName = "planner_tasks")
data class PlannerTaskEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val userId: String = "",
    val dayOffset: Int = 0,
    val scheduledDate: String = "",
    val title: String,
    val description: String = "",
    val category: String = "inspection",
    val priority: String = "medium",
    val status: String = "pending",
    val completedAt: String? = null,
    val weatherAdjusted: Boolean = false,
    val weatherReason: String = "",
    val reminderEnabled: Boolean = true,
    val pendingSync: Boolean = false,
) {
    fun toDomain(): PlannerTaskDto = PlannerTaskDto(
        id = id,
        planId = planId,
        dayOffset = dayOffset,
        scheduledDate = scheduledDate,
        title = title,
        description = description,
        category = category,
        priority = priority,
        status = status,
        completedAt = completedAt,
        weatherAdjusted = weatherAdjusted,
        weatherReason = weatherReason,
        reminderEnabled = reminderEnabled,
    )
}

fun PlannerTaskDto.toEntity(userId: String = "", pendingSync: Boolean = false): PlannerTaskEntity = PlannerTaskEntity(
    id = id ?: UUID.randomUUID().toString(),
    planId = planId,
    userId = userId,
    dayOffset = dayOffset,
    scheduledDate = scheduledDate,
    title = title,
    description = description,
    category = category,
    priority = priority,
    status = status,
    completedAt = completedAt,
    weatherAdjusted = weatherAdjusted,
    weatherReason = weatherReason,
    reminderEnabled = reminderEnabled,
    pendingSync = pendingSync,
)

@Dao
interface CropPlanDao {
    @Query("SELECT * FROM crop_plans WHERE userId = :userId OR userId = '' ORDER BY updatedAt DESC")
    fun getPlans(userId: String): Flow<List<CropPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plans: List<CropPlanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: CropPlanEntity)

    @Query("DELETE FROM crop_plans WHERE id = :planId")
    suspend fun deleteById(planId: String)

    @Query("DELETE FROM crop_plans WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}

@Dao
interface PlannerTaskDao {
    @Query("SELECT * FROM planner_tasks WHERE planId = :planId ORDER BY dayOffset ASC")
    fun getTasksForPlan(planId: String): Flow<List<PlannerTaskEntity>>

    @Query("SELECT * FROM planner_tasks WHERE id = :taskId LIMIT 1")
    suspend fun getById(taskId: String): PlannerTaskEntity?

    @Query("SELECT * FROM planner_tasks WHERE (userId = :userId OR userId = '') AND status = 'pending' ORDER BY scheduledDate ASC")
    fun getPendingTasks(userId: String): Flow<List<PlannerTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<PlannerTaskEntity>)

    @Query("UPDATE planner_tasks SET status = :status, completedAt = :completedAt, pendingSync = :pendingSync WHERE id = :taskId")
    suspend fun updateStatus(taskId: String, status: String, completedAt: String?, pendingSync: Boolean)

    @Query("UPDATE planner_tasks SET title = :title, description = :description, scheduledDate = :scheduledDate, reminderEnabled = :reminderEnabled, pendingSync = :pendingSync WHERE id = :taskId")
    suspend fun updateDetails(taskId: String, title: String, description: String, scheduledDate: String, reminderEnabled: Boolean, pendingSync: Boolean)

    @Query("DELETE FROM planner_tasks WHERE planId = :planId")
    suspend fun deleteForPlan(planId: String)

    @Query("DELETE FROM planner_tasks WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
