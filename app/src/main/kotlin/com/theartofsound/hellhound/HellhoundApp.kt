package com.theartofsound.hellhound

import android.app.Application
import com.theartofsound.hellhound.data.ChatRepository
import com.theartofsound.hellhound.data.SettingsStore
import com.theartofsound.hellhound.data.cerebras.CerebrasClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HellhoundApp : Application() {

    lateinit var settings: SettingsStore
        private set
    lateinit var repository: ChatRepository
        private set

    private val cachedKey = MutableStateFlow<String?>(null)
    val apiKeyState: StateFlow<String?> = cachedKey

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        val client = CerebrasClient(apiKeyProvider = { cachedKey.value })
        repository = ChatRepository(client)
    }

    fun updateCachedKey(value: String?) {
        cachedKey.value = value
    }
}
