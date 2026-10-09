package com.kissansevaai.core.repository

import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.common.toUserFriendlyMessage
import com.kissansevaai.core.database.ChatDao
import com.kissansevaai.core.database.ChatMessageEntity
import com.kissansevaai.core.model.ChatRequest
import com.kissansevaai.core.network.KissanApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
) {
    /**
     * Flow of chat messages ordered by timestamp ascending from Room.
     */
    fun getMessages(): Flow<List<ChatMessageEntity>> = chatDao.getAllMessages()

    /**
     * Sends message to backend chatbot, saving both prompt and response into Room.
     */
    fun sendMessage(text: String, language: String, mode: String? = null): Flow<Resource<String>> = flow {
        emit(Resource.Loading)

        val userEntity = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sender = "user",
            text = text,
            timestamp = System.currentTimeMillis()
        )
        chatDao.insert(userEntity)

        try {
            val response = api.chat(ChatRequest(message = text, language = language, mode = mode))
            val assistantEntity = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                sender = "assistant",
                text = response.response,
                timestamp = System.currentTimeMillis()
            )
            chatDao.insert(assistantEntity)
            emit(Resource.Success(response.response))
        } catch (e: Exception) {
            val friendlyError = e.toUserFriendlyMessage()
            val errorEntity = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                sender = "assistant",
                text = friendlyError,
                timestamp = System.currentTimeMillis()
            )
            chatDao.insert(errorEntity)
            emit(Resource.Error(friendlyError, e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            chatDao.clearAll()
        }
    }
}
