package com.theartofsound.hellhound

import android.app.Application
import com.theartofsound.hellhound.data.ChatRepository
import com.theartofsound.hellhound.data.SettingsStore
import com.theartofsound.hellhound.data.cerebras.CerebrasClient
import java.util.concurrent.atomic.AtomicReference

class HellhoundApp : Application() {

    lateinit var settings: SettingsStore
        private set
    lateinit var repository: ChatRepository
        private set

    private val cachedKey = AtomicReference<String?>(null)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        val client = CerebrasClient(apiKeyProvider = { cachedKey.get() })
        repository = ChatRepository(client)
    }

    fun updateCachedKey(value: String?) {
        cachedKey.set(value)
    }
}
