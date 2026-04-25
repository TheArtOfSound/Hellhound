package com.theartofsound.hellhound.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.theartofsound.hellhound.data.cerebras.CerebrasModelIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class StoredMessage(val role: String, val content: String)

private val Context.dataStore by preferencesDataStore(name = "hellhound_settings")
private val storeJson = Json { ignoreUnknownKeys = true }

class SettingsStore(private val context: Context) {

    val apiKey: Flow<String?> = context.dataStore.data.map { it[KEY_API] }
    val model: Flow<String> = context.dataStore.data.map {
        it[KEY_MODEL] ?: CerebrasModelIds.DEFAULT
    }
    val systemPrompt: Flow<String> = context.dataStore.data.map {
        it[KEY_SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT
    }
    val autoSendVoice: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_AUTO_SEND_VOICE] ?: false
    }
    val agentMode: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_AGENT_MODE] ?: true
    }
    val autoSpeak: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_AUTO_SPEAK] ?: false
    }
    val voiceFirstMode: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_VOICE_FIRST] ?: false
    }
    val history: Flow<List<StoredMessage>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_HISTORY] ?: return@map emptyList()
        runCatching {
            storeJson.decodeFromString(ListSerializer(StoredMessage.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun setApiKey(value: String?) {
        context.dataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(KEY_API) else prefs[KEY_API] = value
        }
    }

    suspend fun setModel(value: String) {
        context.dataStore.edit { it[KEY_MODEL] = value }
    }

    suspend fun setSystemPrompt(value: String) {
        context.dataStore.edit { prefs ->
            val trimmed = value.trim()
            if (trimmed.isEmpty() || trimmed == DEFAULT_SYSTEM_PROMPT) {
                prefs.remove(KEY_SYSTEM_PROMPT)
            } else {
                prefs[KEY_SYSTEM_PROMPT] = trimmed
            }
        }
    }

    suspend fun setAutoSendVoice(value: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SEND_VOICE] = value }
    }

    suspend fun setAgentMode(value: Boolean) {
        context.dataStore.edit { it[KEY_AGENT_MODE] = value }
    }

    suspend fun setAutoSpeak(value: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SPEAK] = value }
    }

    suspend fun setVoiceFirstMode(value: Boolean) {
        context.dataStore.edit { it[KEY_VOICE_FIRST] = value }
    }

    suspend fun setHistory(messages: List<StoredMessage>) {
        val payload = storeJson.encodeToString(
            ListSerializer(StoredMessage.serializer()), messages
        )
        context.dataStore.edit { prefs ->
            if (messages.isEmpty()) prefs.remove(KEY_HISTORY) else prefs[KEY_HISTORY] = payload
        }
    }

    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "You are Hellhound, a personal Android assistant running on the user's phone. " +
            "Be direct and concise. When agent mode is on, you have tools to read the " +
            "foreground screen, recent notifications, the clipboard, current time, and " +
            "device info — call them whenever the answer depends on the user's current " +
            "context, then answer in plain language."

        private val KEY_API = stringPreferencesKey("cerebras_api_key")
        private val KEY_MODEL = stringPreferencesKey("cerebras_model")
        private val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        private val KEY_HISTORY = stringPreferencesKey("chat_history_v1")
        private val KEY_AUTO_SEND_VOICE = booleanPreferencesKey("auto_send_voice")
        private val KEY_AGENT_MODE = booleanPreferencesKey("agent_mode")
        private val KEY_AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        private val KEY_VOICE_FIRST = booleanPreferencesKey("voice_first_mode")
    }
}
