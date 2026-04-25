package com.theartofsound.hellhound.tools

import android.content.ClipboardManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import com.theartofsound.hellhound.data.cerebras.ToolFunction
import com.theartofsound.hellhound.data.cerebras.ToolSpec
import com.theartofsound.hellhound.services.HellhoundAccessibilityService
import com.theartofsound.hellhound.services.HellhoundNotificationListener
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Tools the LLM can call when "agent mode" is enabled. Each tool is a
 * thin, deterministic wrapper around an Android API or already-collected
 * context. None of them can write to the device — read-only by design,
 * so an over-eager LLM can't cause damage.
 */
class ToolDispatcher(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    fun dispatch(name: String, argsJson: String): String = try {
        val args = if (argsJson.isBlank()) JsonObject(emptyMap())
        else json.parseToJsonElement(argsJson).jsonObject

        when (name) {
            "get_screen_text" -> getScreenText()
            "get_recent_notifications" -> getNotifications(
                args["limit"]?.jsonPrimitive?.intOrNull ?: 10
            )
            "get_device_info" -> getDeviceInfo()
            "get_clipboard" -> getClipboard()
            "now" -> nowIso8601()
            else -> "Unknown tool: $name"
        }
    } catch (t: Throwable) {
        "Tool $name failed: ${t.message ?: t::class.java.simpleName}"
    }

    private fun getScreenText(): String {
        if (!HellhoundAccessibilityService.isEnabled(context)) {
            return "Accessibility access is OFF. Ask the user to enable it under " +
                "Access tab in Hellhound, then retry."
        }
        return HellhoundAccessibilityService.lastScreenSnapshot()?.take(8000)
            ?: "(no screen snapshot yet — open the foreground app and retry)"
    }

    private fun getNotifications(limit: Int): String {
        if (!HellhoundNotificationListener.isEnabled(context)) {
            return "Notification access is OFF. Ask the user to enable it under " +
                "Access tab in Hellhound, then retry."
        }
        val notes = HellhoundNotificationListener.recentNotifications().take(limit.coerceIn(1, 50))
        return if (notes.isEmpty()) "(no recent notifications)"
        else notes.joinToString("\n") { "- $it" }
    }

    private fun getDeviceInfo(): String = buildString {
        appendLine("OS: Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Locale: ${Locale.getDefault()}")
        appendLine("Time: ${nowIso8601()}")
        runCatching {
            val bm = context.getSystemService(BatteryManager::class.java)
            val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level != null && level >= 0) appendLine("Battery: $level%")
        }
    }.trim()

    private fun getClipboard(): String {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return "(clipboard unavailable)"
        val item = cm.primaryClip?.getItemAt(0)
        val text = item?.coerceToText(context)?.toString().orEmpty()
        return text.ifBlank { "(clipboard empty)" }.take(4000)
    }

    private fun nowIso8601(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        fmt.timeZone = TimeZone.getDefault()
        return fmt.format(Date())
    }

    companion object {
        /** OpenAI-compatible JSON Schema describing each tool. */
        val SPECS: List<ToolSpec> = listOf(
            spec(
                "get_screen_text",
                "Return text currently visible on the user's foreground app. Useful when the user asks 'what does this say' or about anything they're looking at.",
                """{"type":"object","properties":{},"required":[]}"""
            ),
            spec(
                "get_recent_notifications",
                "Return the most recent notifications Android has delivered (newest first). Useful when the user asks about messages, reminders, alerts.",
                """{"type":"object","properties":{"limit":{"type":"integer","description":"Max notifications to return","default":10}},"required":[]}"""
            ),
            spec(
                "get_device_info",
                "Return basic device info: OS version, manufacturer, model, locale, current local time, battery level.",
                """{"type":"object","properties":{},"required":[]}"""
            ),
            spec(
                "get_clipboard",
                "Return the current text on the system clipboard. Useful when the user says 'this' referring to something they just copied.",
                """{"type":"object","properties":{},"required":[]}"""
            ),
            spec(
                "now",
                "Return the current local date and time in ISO-8601 format.",
                """{"type":"object","properties":{},"required":[]}"""
            )
        )

        private fun spec(name: String, description: String, paramsJson: String): ToolSpec {
            val parser = Json { ignoreUnknownKeys = true }
            return ToolSpec(
                function = ToolFunction(
                    name = name,
                    description = description,
                    parameters = parser.parseToJsonElement(paramsJson)
                )
            )
        }
    }
}
