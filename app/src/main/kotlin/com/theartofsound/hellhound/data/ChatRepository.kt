package com.theartofsound.hellhound.data

import com.theartofsound.hellhound.data.cerebras.CerebrasClient
import com.theartofsound.hellhound.data.cerebras.ChatCompletionRequest
import com.theartofsound.hellhound.data.cerebras.ChatMessage
import com.theartofsound.hellhound.data.cerebras.ToolSpec
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

    suspend fun runAgent(
        model: String,
        history: List<ChatMessage>,
        tools: List<ToolSpec>,
        executeTool: suspend (name: String, argsJson: String) -> String,
        systemPrompt: String? = null,
        onTrace: (String) -> Unit = {}
    ): String {
        val messages = buildList {
            if (!systemPrompt.isNullOrBlank()) add(ChatMessage("system", systemPrompt))
            addAll(history)
        }
        return client.runAgent(model, messages, tools, executeTool, onTrace = onTrace)
    }

    suspend fun streamAgent(
        model: String,
        history: List<ChatMessage>,
        tools: List<ToolSpec>,
        executeTool: suspend (name: String, argsJson: String) -> String,
        systemPrompt: String? = null,
        emitter: suspend (CerebrasClient.StreamEvent) -> Unit
    ) {
        val messages = buildList {
            if (!systemPrompt.isNullOrBlank()) add(ChatMessage("system", systemPrompt))
            addAll(history)
        }
        client.streamAgent(model, messages, tools, executeTool, emitter = emitter)
    }

    suspend fun listModels(): List<String> = client.listModels()
}
