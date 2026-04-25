package com.theartofsound.hellhound.data

import android.content.Context
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
        it[KEY_MODEL] ?: CerebrasModelIds.LLAMA_3_3_70B
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

    suspend fun setHistory(messages: List<StoredMessage>) {
        val payload = storeJson.encodeToString(
            ListSerializer(StoredMessage.serializer()), messages
        )
        context.dataStore.edit { prefs ->
            if (messages.isEmpty()) prefs.remove(KEY_HISTORY) else prefs[KEY_HISTORY] = payload
        }
    }

    private companion object {
        val KEY_API = stringPreferencesKey("cerebras_api_key")
        val KEY_MODEL = stringPreferencesKey("cerebras_model")
        val KEY_HISTORY = stringPreferencesKey("chat_history_v1")
    }
}
