package com.theartofsound.hellhound.services

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicReference

/**
 * Reads on-screen text from the foreground app so the assistant can answer
 * "what does this say?" questions. The user must enable this manually in
 * Settings > Accessibility; Android shows a confirmation dialog on enable.
 *
 * No data leaves the device through this service. The latest snapshot is held
 * in memory and consumed by the chat ViewModel only when a message is sent.
 */
class HellhoundAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        val text = collectText(root)
        if (text.isNotBlank()) lastSnapshot.set(text)
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected.set(true)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        connected.set(false)
        lastSnapshot.set(null)
        return super.onUnbind(intent)
    }

    private fun collectText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val builder = StringBuilder()
        traverse(node, builder, depth = 0)
        return builder.toString().trim()
    }

    private fun traverse(node: AccessibilityNodeInfo, out: StringBuilder, depth: Int) {
        if (depth > MAX_DEPTH) return
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) out.append(text).append('\n')
        else if (desc.isNotEmpty()) out.append(desc).append('\n')
        for (i in 0 until node.childCount) {
            traverse(node.getChild(i) ?: continue, out, depth + 1)
        }
    }

    companion object {
        private const val MAX_DEPTH = 32
        private val connected = java.util.concurrent.atomic.AtomicBoolean(false)
        private val lastSnapshot = AtomicReference<String?>(null)

        fun lastScreenSnapshot(): String? = lastSnapshot.get()

        /** Mirrors Android's accessibility_enabled check. */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(
                context.packageName,
                HellhoundAccessibilityService::class.java.name
            ).flattenToString()
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':').apply {
                setString(enabledServices)
            }
            for (component in splitter) {
                if (component.equals(expected, ignoreCase = true)) return true
            }
            return false
        }
    }
}
