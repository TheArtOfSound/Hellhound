package com.theartofsound.hellhound.data.cerebras

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int? = null
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
    val content: String? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

object CerebrasModelIds {
    const val LLAMA_3_1_8B = "llama3.1-8b"
    const val LLAMA_3_3_70B = "llama-3.3-70b"
    const val QWEN_3_32B = "qwen-3-32b"
    val all = listOf(LLAMA_3_1_8B, LLAMA_3_3_70B, QWEN_3_32B)
}
