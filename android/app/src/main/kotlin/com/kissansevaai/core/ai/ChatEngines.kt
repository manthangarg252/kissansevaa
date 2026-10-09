package com.kissansevaai.core.ai

import com.kissansevaai.core.model.ChatRequest
import com.kissansevaai.core.model.ChatResponse
import com.kissansevaai.core.network.KissanApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote chat engine — delegates to /api/chat on the backend.
 * Will be complemented by an on-device LiteRT-LM engine in Phase 5.
 */
@Singleton
class RemoteChatEngine @Inject constructor(
    private val api: KissanApi,
) : AiEngine<ChatRequest, ChatResponse> {
    override val engineId = "remote_chat"
    override suspend fun infer(input: ChatRequest): ChatResponse = api.chat(input)
    override suspend fun isAvailable(): Boolean = true // Always available if network is up
}

/**
 * Placeholder on-device chat engine.
 * In Phase 5, this will load a LiteRT-LM model and run inference locally.
 */
@Singleton
class OnDeviceChatEngine @Inject constructor() : AiEngine<ChatRequest, ChatResponse> {
    override val engineId = "ondevice_chat"
    override suspend fun infer(input: ChatRequest): ChatResponse {
        // Phase 5: Load LiteRT-LM model and run inference
        throw UnsupportedOperationException("On-device LLM not yet available. Coming in Phase 5.")
    }
    override suspend fun isAvailable(): Boolean = false // Not yet implemented
}
