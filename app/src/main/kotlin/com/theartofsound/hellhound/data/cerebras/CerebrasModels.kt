package com.theartofsound.hellhound.data.cerebras

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ChatMessage(
    val role: String,
    val content: String? = null,
    val name: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val tools: List<ToolSpec>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null
)

@Serializable
data class ToolSpec(
    val type: String = "function",
    val function: ToolFunction
)

@Serializable
data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonElement
)

// Lenient by design: streaming deltas omit id on chunks after the first
// and split arguments across multiple chunks, so the parser tolerates
// partial values and the streaming-agent assembler stitches them
// together before either dispatching them or echoing the message back
// to the API.
@Serializable
data class ToolCall(
    val id: String? = null,
    val type: String = "function",
    val function: ToolCallFunction? = null,
    val index: Int? = null
)

@Serializable
data class ToolCallFunction(
    val name: String? = null,
    val arguments: String = ""
)

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ChatMessage? = null,
    val delta: Delta? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class Delta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

object CerebrasModelIds {
    const val LLAMA_3_1_8B = "llama3.1-8b"
    const val DEFAULT = LLAMA_3_1_8B
    // Single safe default; the real list is fetched from /v1/models on
    // launch and replaces this. Cerebras lineups vary per account.
    val all = listOf(LLAMA_3_1_8B)
}
