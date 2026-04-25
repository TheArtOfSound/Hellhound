package com.theartofsound.hellhound

import android.app.Application
import com.theartofsound.hellhound.audio.Speaker
import com.theartofsound.hellhound.data.ChatRepository
import com.theartofsound.hellhound.data.MemoryStore
import com.theartofsound.hellhound.data.SettingsStore
import com.theartofsound.hellhound.data.cerebras.CerebrasClient
import com.theartofsound.hellhound.tools.ToolDispatcher
import java.util.concurrent.atomic.AtomicReference

class HellhoundApp : Application() {

    lateinit var settings: SettingsStore
        private set
    lateinit var repository: ChatRepository
        private set
    lateinit var speaker: Speaker
        private set
    lateinit var memory: MemoryStore
        private set
    lateinit var tools: ToolDispatcher
        private set

    private val cachedKey = AtomicReference<String?>(null)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        val client = CerebrasClient(apiKeyProvider = { cachedKey.get() })
        repository = ChatRepository(client)
        speaker = Speaker(this)
        memory = MemoryStore(this)
        tools = ToolDispatcher(this, memory = memory)
    }

    override fun onTerminate() {
        runCatching { speaker.shutdown() }
        super.onTerminate()
    }

    fun updateCachedKey(value: String?) {
        cachedKey.set(value)
    }
}
