package com.theartofsound.hellhound.data

import com.theartofsound.hellhound.data.cerebras.CerebrasClient
import com.theartofsound.hellhound.data.cerebras.ChatCompletionRequest
import com.theartofsound.hellhound.data.cerebras.ChatMessage
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val client: CerebrasClient) {

    fun streamReply(
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String? = null
    ): Flow<String> {
        val messages = buildList {
            if (!systemPrompt.isNullOrBlank()) add(ChatMessage("system", systemPrompt))
            addAll(history)
        }
        return client.stream(ChatCompletionRequest(model = model, messages = messages))
    }

    suspend fun listModels(): List<String> = client.listModels()
}
