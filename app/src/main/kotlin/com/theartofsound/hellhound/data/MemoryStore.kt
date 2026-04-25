package com.theartofsound.hellhound.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ArchivedMessage(
    val role: String,
    val content: String,
    val timestamp: Long
)

/**
 * Append-only conversation archive. Never cleared by the chat's "Clear"
 * button — that only wipes what's on screen. The recall/recent_history
 * tools read from this so the agent can answer "what did we talk about
 * yesterday" without bloating the prompt.
 *
 * Stored as JSONL at filesDir/memory.jsonl. Reads scan the whole file —
 * fine up to tens of thousands of turns; if it ever balloons we can
 * switch to Room-backed FTS, but the file format is forward-compatible.
 */
class MemoryStore(context: Context) {

    private val file: File = File(context.filesDir, "memory.jsonl")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()

    suspend fun append(messages: List<ArchivedMessage>) {
        if (messages.isEmpty()) return
        withContext(Dispatchers.IO) {
            lock.withLock {
                if (!file.exists()) file.createNewFile()
                val out = StringBuilder()
                for (m in messages) {
                    out.append(json.encodeToString(ArchivedMessage.serializer(), m)).append('\n')
                }
                file.appendText(out.toString())
            }
        }
    }

    suspend fun readAll(): List<ArchivedMessage> = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!file.exists()) return@withLock emptyList()
            file.bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    if (line.isBlank()) null
                    else runCatching {
                        json.decodeFromString(ArchivedMessage.serializer(), line)
                    }.getOrNull()
                }.toList()
            }
        }
    }

    suspend fun search(query: String, limit: Int = 20): List<ArchivedMessage> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        // Newest-first, simple substring match. Good enough for v1 — vector
        // recall is a future cycle once we have an embedding endpoint.
        return readAll().asReversed().filter { it.content.lowercase().contains(q) }.take(limit)
    }

    suspend fun recent(limit: Int): List<ArchivedMessage> =
        readAll().takeLast(limit.coerceAtLeast(1))

    suspend fun stats(): Stats {
        val all = readAll()
        return Stats(
            totalTurns = all.size,
            firstTimestamp = all.firstOrNull()?.timestamp,
            lastTimestamp = all.lastOrNull()?.timestamp
        )
    }

    data class Stats(val totalTurns: Int, val firstTimestamp: Long?, val lastTimestamp: Long?)
}
