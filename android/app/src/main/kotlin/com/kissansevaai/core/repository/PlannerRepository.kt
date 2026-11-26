package com.kissansevaai.core.repository

import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.common.toUserFriendlyMessage
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlannerRepository @Inject constructor(
    private val api: KissanApi,
    private val planDao: CropPlanDao,
    private val taskDao: PlannerTaskDao,
    private val syncDao: SyncOperationDao,
    private val dataStore: DataStoreManager,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun getPlansAndTasks(): Flow<Resource<PlannerListResponse>> = flow {
        emit(Resource.Loading)
        val userId = dataStore.userId.first().orEmpty()
        val cachedPlans = planDao.getPlans(userId).first().map { it.toDomain() }
        val cachedTasks = if (cachedPlans.isNotEmpty()) {
            val list = mutableListOf<PlannerTaskDto>()
            for (p in cachedPlans) {
                val pId = p.id ?: continue
                list += taskDao.getTasksForPlan(pId).first().map { it.toDomain() }
            }
            list
        } else emptyList()

        if (cachedPlans.isNotEmpty()) {
            emit(Resource.Success(PlannerListResponse(plans = cachedPlans, tasks = cachedTasks)))
        }

        try {
            val remote = api.getCropPlans()
            planDao.insertAll(remote.plans.map { it.toEntity(userId = userId) })
            taskDao.insertAll(remote.tasks.map { it.toEntity(userId = userId) })
            val mergedPlans = planDao.getPlans(userId).first().map { it.toDomain() }
            val mergedTasks = mutableListOf<PlannerTaskDto>()
            for (p in mergedPlans) {
                val pId = p.id ?: continue
                mergedTasks += taskDao.getTasksForPlan(pId).first().map { it.toDomain() }
            }
            emit(Resource.Success(PlannerListResponse(plans = mergedPlans, tasks = mergedTasks)))
        } catch (e: Exception) {
            if (cachedPlans.isEmpty()) {
                emit(Resource.Error(e.toUserFriendlyMessage(), e))
            }
        }
    }.flowOn(Dispatchers.IO)

    fun createPlan(request: CreatePlanRequest): Flow<Resource<CropPlanBundleResponse>> = flow {
        emit(Resource.Loading)
        val userId = dataStore.userId.first().orEmpty()
        val reqId = request.clientRequestId ?: "plan_${UUID.randomUUID()}"
        val reqWithId = request.copy(clientRequestId = reqId)

        try {
            val bundle = api.createCropPlan(reqWithId)
            planDao.insert(bundle.plan.toEntity(userId = userId))
            taskDao.insertAll(bundle.tasks.map { it.toEntity(userId = userId) })
            emit(Resource.Success(bundle))
        } catch (e: Exception) {
            val localPlanId = "local_$reqId"
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val localPlan = CropPlanDto(
                id = localPlanId,
                cropName = request.cropName,
                variety = request.variety,
                stage = request.stage,
                soilType = request.soilType,
                irrigationType = request.irrigationType,
                farmSizeAcres = request.farmSizeAcres,
                locationName = request.locationName,
                startDate = today,
                status = "active",
                generationSource = "rules_only",
                updatedAt = today,
            )
            val localTasks = listOf(
                PlannerTaskDto(
                    id = "localtask_${reqId}_0",
                    planId = localPlanId,
                    dayOffset = 0,
                    scheduledDate = today,
                    title = "${request.cropName}: Moisture & Pest Check (Offline Plan)",
                    description = "Saved locally in Room. Will synchronize full 7-day weather plan when online.",
                    category = "inspection",
                    priority = "high",
                    status = "pending",
                )
            )
            planDao.insert(localPlan.toEntity(userId = userId, pendingSync = true))
            taskDao.insertAll(localTasks.map { it.toEntity(userId = userId, pendingSync = true) })

            val payloadMap = mapOf(
                "cropName" to JsonPrimitive(request.cropName),
                "variety" to JsonPrimitive(request.variety),
                "stage" to JsonPrimitive(request.stage),
                "soilType" to JsonPrimitive(request.soilType),
                "irrigationType" to JsonPrimitive(request.irrigationType),
                "farmSizeAcres" to JsonPrimitive(request.farmSizeAcres),
                "locationName" to JsonPrimitive(request.locationName),
                "language" to JsonPrimitive(request.language),
                "clientRequestId" to JsonPrimitive(reqId),
            )
            syncDao.enqueue(
                SyncOperationEntity(
                    operationId = reqId,
                    userId = userId,
                    opType = "planner_plan_create",
                    payloadJson = json.encodeToString(payloadMap),
                ),
            )
            emit(Resource.Success(CropPlanBundleResponse(plan = localPlan, tasks = localTasks, weatherSource = "offline_queued")))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun toggleTaskStatus(taskId: String, nextStatus: String): Resource<Unit> {
        val userId = dataStore.userId.first().orEmpty()
        val nowIso = if (nextStatus == "completed") SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()) else null
        val localTask = taskDao.getById(taskId)
        taskDao.updateStatus(taskId = taskId, status = nextStatus, completedAt = nowIso, pendingSync = true)

        if (taskId.startsWith("localtask_") || localTask?.planId?.startsWith("local_") == true) {
            val planClientReqId = (localTask?.planId ?: "").removePrefix("local_")
            val payloadMap = buildMap<String, JsonPrimitive> {
                put("taskId", JsonPrimitive(taskId))
                put("status", JsonPrimitive(nextStatus))
                put("dayOffset", JsonPrimitive(localTask?.dayOffset ?: 0))
                if (planClientReqId.isNotBlank()) put("planClientRequestId", JsonPrimitive(planClientReqId))
            }
            syncDao.enqueue(
                SyncOperationEntity(
                    operationId = "task_${taskId}_${System.currentTimeMillis()}",
                    userId = userId,
                    opType = "planner_task_update",
                    payloadJson = json.encodeToString(payloadMap),
                ),
            )
            return Resource.Success(Unit)
        }

        return try {
            api.updatePlannerTask(taskId, TaskStatusUpdateRequest(status = nextStatus))
            taskDao.updateStatus(taskId = taskId, status = nextStatus, completedAt = nowIso, pendingSync = false)
            Resource.Success(Unit)
        } catch (e: Exception) {
            val payloadMap = mapOf(
                "taskId" to JsonPrimitive(taskId),
                "status" to JsonPrimitive(nextStatus),
            )
            syncDao.enqueue(
                SyncOperationEntity(
                    operationId = "task_${taskId}_${System.currentTimeMillis()}",
                    userId = userId,
                    opType = "planner_task_update",
                    payloadJson = json.encodeToString(payloadMap),
                ),
            )
            Resource.Success(Unit)
        }
    }

    fun regenerateTasks(planId: String, stage: String? = null): Flow<Resource<CropPlanBundleResponse>> = flow {
        emit(Resource.Loading)
        val userId = dataStore.userId.first().orEmpty()
        try {
            val bundle = api.regeneratePlanTasks(planId, RegeneratePlanRequest(stage = stage))
            planDao.insert(bundle.plan.toEntity(userId = userId))
            taskDao.insertAll(bundle.tasks.map { it.toEntity(userId = userId) })
            emit(Resource.Success(bundle))
        } catch (e: Exception) {
            emit(Resource.Error(e.toUserFriendlyMessage(), e))
        }
    }.flowOn(Dispatchers.IO)
}
