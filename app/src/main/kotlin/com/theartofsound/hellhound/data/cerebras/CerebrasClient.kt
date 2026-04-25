package com.theartofsound.hellhound.data.cerebras

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

    suspend fun listModels(): List<String> {
        val key = requireKey()
        val httpRequest = Request.Builder()
            .url("$baseUrl/models")
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .get()
            .build()
        http.newCall(httpRequest).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw cerebrasException(response.code, text)
            val parsed = runCatching {
                json.decodeFromString(ModelListResponse.serializer(), text)
            }.getOrNull()
            val ids = parsed?.data?.mapNotNull { it.id.takeIf { id -> id.isNotBlank() } }
            return ids?.sorted()
                ?: throw CerebrasException(
                    response.code,
                    "Unexpected /v1/models response shape. First 200 chars: ${text.take(200)}"
                )
        }
    }

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
                throw cerebrasException(response.code, text)
            }
            return json.decodeFromString(ChatCompletionResponse.serializer(), text)
        }
    }

    private fun cerebrasException(status: Int, body: String): CerebrasException {
        val parsed = runCatching {
            json.decodeFromString(ApiErrorEnvelope.serializer(), body)
        }.getOrNull()
        // Some endpoints wrap in {"error": {...}}; flatten if so.
        val effective = parsed?.nested ?: parsed
        val message = effective?.message?.takeIf { it.isNotBlank() } ?: body
        val code = effective?.code
        val friendly = when (code) {
            "model_not_found" ->
                "$message Open Settings and pick another model (llama-3.3-70b is a safe default)."
            "invalid_api_key", "authentication_error" ->
                "$message Check your Cerebras API key in Settings."
            else -> message
        }
        return CerebrasException(status, friendly)
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
                throw cerebrasException(response.code, text)
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

class CerebrasException(val status: Int, message: String) : RuntimeException(message)

// The API sometimes returns flat {message, code} and sometimes wraps in
// {error: {...}}. We tolerate either by making the nested field optional.
@Serializable
private data class ApiErrorEnvelope(
    val message: String? = null,
    val type: String? = null,
    val param: String? = null,
    val code: String? = null,
    @SerialName("error") val nested: ApiErrorEnvelope? = null
)

@Serializable
private data class ModelListResponse(val data: List<ModelEntry> = emptyList())

@Serializable
private data class ModelEntry(val id: String = "")
