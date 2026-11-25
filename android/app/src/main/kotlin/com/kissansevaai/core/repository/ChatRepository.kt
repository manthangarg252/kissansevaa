package com.kissansevaai.core.repository

import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.common.toUserFriendlyMessage
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.database.ChatDao
import com.kissansevaai.core.database.ChatMessageEntity
import com.kissansevaai.core.model.ChatRequest
import com.kissansevaai.core.network.KissanApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val api: KissanApi,
    private val chatDao: ChatDao,
    private val dataStore: DataStoreManager? = null,
) {
    private suspend fun activeUserId(): String = dataStore?.userId?.first().orEmpty()

    fun getMessages(): Flow<List<ChatMessageEntity>> = chatDao.getAllMessages()

    fun getSessionMessages(sessionId: String = "default"): Flow<List<ChatMessageEntity>> = flow {
        val userId = activeUserId()
        chatDao.getMessagesForSession(userId, sessionId).collect { emit(it) }
    }.flowOn(Dispatchers.IO)

    suspend fun syncServerHistory(sessionId: String = "default") {
        withContext(Dispatchers.IO) {
            try {
                val userId = activeUserId()
                val remote = api.getChatHistory(sessionId = sessionId)
                val entities = mutableListOf<ChatMessageEntity>()
                remote.turns.forEachIndexed { idx, turn ->
                    val baseTs = System.currentTimeMillis() - (remote.turns.size - idx) * 2000L
                    val tid = turn.id ?: "${sessionId}_$idx"
                    entities.add(
                        ChatMessageEntity(
                            id = "${tid}_u",
                            sender = "user",
                            text = turn.message,
                            timestamp = baseTs,
                            userId = userId,
                            sessionId = turn.sessionId,
                            category = turn.category,
                        )
                    )
                    entities.add(
                        ChatMessageEntity(
                            id = "${tid}_a",
                            sender = "assistant",
                            text = turn.response,
                            timestamp = baseTs + 1,
                            userId = userId,
                            sessionId = turn.sessionId,
                            category = turn.category,
                            groundedSources = turn.groundedSources,
                        )
                    )
                }
                if (entities.isNotEmpty()) {
                    chatDao.insertAll(entities)
                }
            } catch (_: Exception) {
                // Keep local Room chat messages intact if offline
            }
        }
    }

    fun sendMessage(
        text: String,
        language: String,
        mode: String? = null,
        sessionId: String = "default",
    ): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        val userId = activeUserId()
        val reqId = "chat_${UUID.randomUUID()}"

        val userEntity = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sender = "user",
            text = text,
            timestamp = System.currentTimeMillis(),
            userId = userId,
            sessionId = sessionId,
            category = mode?.lowercase() ?: "general",
        )
        chatDao.insert(userEntity)

        try {
            val response = api.chat(
                ChatRequest(
                    message = text,
                    language = language,
                    mode = mode,
                    category = mode,
                    sessionId = sessionId,
                    clientRequestId = reqId,
                )
            )
            val assistantEntity = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                sender = "assistant",
                text = response.response,
                timestamp = System.currentTimeMillis(),
                userId = userId,
                sessionId = response.sessionId,
                category = response.category,
                groundedSources = response.groundedSources,
                aiAvailable = response.aiAvailable,
            )
            chatDao.insert(assistantEntity)
            emit(Resource.Success(response.response))
        } catch (e: Exception) {
            val friendlyError = e.toUserFriendlyMessage()
            val errorEntity = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                sender = "assistant",
                text = friendlyError,
                timestamp = System.currentTimeMillis(),
                userId = userId,
                sessionId = sessionId,
                category = mode?.lowercase() ?: "general",
                aiAvailable = false,
            )
            chatDao.insert(errorEntity)
            emit(Resource.Error(friendlyError, e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun clearHistory(sessionId: String = "default") {
        withContext(Dispatchers.IO) {
            val userId = activeUserId()
            if (userId.isNotBlank()) {
                chatDao.clearSession(userId, sessionId)
            } else {
                chatDao.clearAll()
            }
            try {
                api.clearChatHistory(sessionId = sessionId)
            } catch (_: Exception) {
            }
        }
    }
}
