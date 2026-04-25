package com.theartofsound.hellhound.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.theartofsound.hellhound.data.cerebras.CerebrasModelIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "hellhound_settings")

class SettingsStore(private val context: Context) {

    val apiKey: Flow<String?> = context.dataStore.data.map { it[KEY_API] }
    val model: Flow<String> = context.dataStore.data.map {
        it[KEY_MODEL] ?: CerebrasModelIds.LLAMA_3_3_70B
    }

    suspend fun setApiKey(value: String?) {
        context.dataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(KEY_API) else prefs[KEY_API] = value
        }
    }

    suspend fun setModel(value: String) {
        context.dataStore.edit { it[KEY_MODEL] = value }
    }

    private companion object {
        val KEY_API = stringPreferencesKey("cerebras_api_key")
        val KEY_MODEL = stringPreferencesKey("cerebras_model")
    }
}
