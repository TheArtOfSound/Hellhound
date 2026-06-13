package com.theartofsound.hellhound.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lazily-initialised TextToSpeech wrapper. We hold one instance for the
 * application's lifetime; the OS reclaims it when the process dies. Calls
 * to [speak] before init completes are queued and flushed on init.
 *
 * Init callback fires from the TTS service connection — it always runs
 * after the TextToSpeech constructor returns, so the [tts] field is
 * guaranteed to be initialised by then.
 */
class Speaker(context: Context) {

    private val pending = ArrayDeque<String>()
    private val ready = AtomicBoolean(false)
    private val tts: TextToSpeech =
        TextToSpeech(context.applicationContext) { onInit(it) }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Locale.getDefault()
        ready.set(true)
        synchronized(pending) {
            while (pending.isNotEmpty()) speakNow(pending.removeFirst())
        }
    }

    fun speak(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (ready.get()) speakNow(trimmed)
        else synchronized(pending) { pending.addLast(trimmed) }
    }

    fun stop() {
        runCatching { tts.stop() }
    }

    fun shutdown() {
        runCatching { tts.shutdown() }
    }

    private fun speakNow(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "hellhound-${text.hashCode()}")
    }
}
