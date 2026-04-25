package com.theartofsound.hellhound.data.cerebras

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class CerebrasClient(
    private val apiKeyProvider: () -> String?,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val http: OkHttpClient = defaultHttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    suspend fun complete(request: ChatCompletionRequest): ChatCompletionResponse {
        val key = requireKey()
        val body = json.encodeToString(ChatCompletionRequest.serializer(), request)
            .toRequestBody(JSON)
        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .post(body)
            .build()
        http.newCall(httpRequest).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw CerebrasException(response.code, text)
            }
            return json.decodeFromString(ChatCompletionResponse.serializer(), text)
        }
    }

    fun stream(request: ChatCompletionRequest): Flow<String> = flow {
        val key = requireKey()
        val streaming = request.copy(stream = true)
        val body = json.encodeToString(ChatCompletionRequest.serializer(), streaming)
            .toRequestBody(JSON)
        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(body)
            .build()
        http.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                throw CerebrasException(response.code, text)
            }
            val source = response.body?.source() ?: return@flow
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty() || payload == "[DONE]") continue
                val chunk = runCatching {
                    json.decodeFromString(ChatCompletionResponse.serializer(), payload)
                }.getOrNull() ?: continue
                val delta = chunk.choices.firstOrNull()?.delta?.content
                if (!delta.isNullOrEmpty()) emit(delta)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun requireKey(): String =
        apiKeyProvider() ?: throw CerebrasException(401, "Missing Cerebras API key")

    companion object {
        const val DEFAULT_BASE_URL = "https://api.cerebras.ai/v1"
        private val JSON = "application/json".toMediaType()

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

class CerebrasException(val status: Int, message: String) : RuntimeException(
    "Cerebras API error $status: $message"
)
