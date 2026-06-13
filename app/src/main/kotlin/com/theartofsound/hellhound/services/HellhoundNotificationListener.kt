package com.theartofsound.hellhound.services

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.ArrayDeque

/**
 * Surfaces incoming notifications so the assistant can summarize or act on
 * them when asked. The user must grant "Notification access" in system
 * Settings; Android prompts on enable.
 *
 * Only the most recent N notification headlines are kept in memory, and only
 * read when a chat message is sent.
 */
class HellhoundNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        val extras = n.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val pkg = sbn.packageName.orEmpty()
        if (title.isBlank() && text.isBlank()) return
        synchronized(buffer) {
            buffer.addFirst("$pkg: $title — $text".take(MAX_LENGTH))
            while (buffer.size > MAX_BUFFER) buffer.removeLast()
        }
    }

    override fun onListenerDisconnected() {
        synchronized(buffer) { buffer.clear() }
        super.onListenerDisconnected()
    }

    companion object {
        private const val MAX_BUFFER = 20
        private const val MAX_LENGTH = 240
        private val buffer = ArrayDeque<String>()

        fun recentNotifications(): List<String> = synchronized(buffer) { buffer.toList() }

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(
                context.packageName,
                HellhoundNotificationListener::class.java.name
            ).flattenToString()
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return flat.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
