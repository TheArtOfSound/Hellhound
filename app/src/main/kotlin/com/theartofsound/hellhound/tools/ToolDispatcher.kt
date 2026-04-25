package com.theartofsound.hellhound.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import com.theartofsound.hellhound.data.cerebras.ToolFunction
import com.theartofsound.hellhound.data.cerebras.ToolSpec
import com.theartofsound.hellhound.services.HellhoundAccessibilityService
import com.theartofsound.hellhound.services.HellhoundNotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Tools the LLM can call when "agent mode" is enabled.
 *
 * Read tools (no side effects on the device):
 *   get_screen_text, get_recent_notifications, get_clipboard,
 *   get_device_info, now, search_web
 *
 * Action tools (open a system Activity preconfigured; the user always
 * sees the system UI and confirms before anything actually happens):
 *   open_app, open_url, set_alarm, set_timer, compose_sms,
 *   compose_email, dial_number, set_clipboard
 *
 * No tool can send a text, place a call, or transmit anything without
 * the user tapping a system confirmation, so an over-eager LLM cannot
 * cause damage.
 */
class ToolDispatcher(
    private val context: Context,
    private val http: OkHttpClient = defaultHttp()
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun dispatch(name: String, argsJson: String): String = try {
        val args = parseArgs(argsJson)
        when (name) {
            // --- read tools -------------------------------------------------
            "get_screen_text" -> getScreenText()
            "get_recent_notifications" -> getNotifications(
                args["limit"]?.jsonPrimitive?.intOrNull ?: 10
            )
            "get_device_info" -> getDeviceInfo()
            "get_clipboard" -> getClipboard()
            "now" -> nowIso8601()
            "search_web" -> searchWeb(
                args["query"]?.jsonPrimitive?.contentOrNull.orEmpty()
            )
            // --- action tools ----------------------------------------------
            "open_app" -> openApp(
                args["package"]?.jsonPrimitive?.contentOrNull.orEmpty()
            )
            "open_url" -> openUrl(
                args["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
            )
            "set_alarm" -> setAlarm(
                args["hour"]?.jsonPrimitive?.intOrNull ?: -1,
                args["minute"]?.jsonPrimitive?.intOrNull ?: 0,
                args["label"]?.jsonPrimitive?.contentOrNull.orEmpty()
            )
            "set_timer" -> setTimer(
                args["seconds"]?.jsonPrimitive?.intOrNull ?: -1,
                args["label"]?.jsonPrimitive?.contentOrNull.orEmpty()
            )
            "compose_sms" -> composeSms(
                args["number"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                args["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
            )
            "compose_email" -> composeEmail(
                args["to"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                args["subject"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                args["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
            )
            "dial_number" -> dialNumber(
                args["number"]?.jsonPrimitive?.contentOrNull.orEmpty()
            )
            "set_clipboard" -> setClipboard(
                args["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            )
            else -> "Unknown tool: $name"
        }
    } catch (t: Throwable) {
        "Tool $name failed: ${t.message ?: t::class.java.simpleName}"
    }

    private fun parseArgs(argsJson: String): JsonObject =
        if (argsJson.isBlank()) JsonObject(emptyMap())
        else json.parseToJsonElement(argsJson).jsonObject

    // --- read tools -----------------------------------------------------------

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

    private suspend fun searchWeb(query: String): String {
        if (query.isBlank()) return "search_web: empty query"
        return withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val request = Request.Builder().url(url).get().build()
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use "search_web HTTP ${resp.code}"
                val raw = resp.body?.string().orEmpty()
                val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
                    ?: return@use "search_web: unparsable response"
                val abstract = obj["AbstractText"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val source = obj["AbstractSource"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val abstractUrl = obj["AbstractURL"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val answer = obj["Answer"]?.jsonPrimitive?.contentOrNull.orEmpty()
                buildString {
                    if (answer.isNotBlank()) {
                        appendLine("Answer: $answer")
                    }
                    if (abstract.isNotBlank()) {
                        appendLine(abstract)
                        if (source.isNotBlank()) appendLine("— $source ($abstractUrl)")
                    }
                    if (isEmpty()) {
                        append("DuckDuckGo had no instant answer. Suggest the user try a more specific query or visit https://duckduckgo.com/?q=$encoded directly.")
                    }
                }.trim()
            }
        }
    }

    // --- action tools ---------------------------------------------------------

    private fun openApp(packageName: String): String {
        val pkg = packageName.trim()
        if (pkg.isBlank()) return "open_app: missing 'package'."
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return "open_app: $pkg is not installed or not visible to Hellhound."
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return "Launched $pkg."
    }

    private fun openUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return "open_url: missing 'url'."
        val url = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val uri = runCatching { Uri.parse(url) }.getOrNull()
            ?: return "open_url: invalid URL."
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchOrFail(intent, "Opened $url in browser.")
    }

    private fun setAlarm(hour: Int, minute: Int, label: String): String {
        if (hour !in 0..23 || minute !in 0..59) {
            return "set_alarm: hour must be 0-23 and minute 0-59. Got $hour:$minute."
        }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launchOrFail(intent, "Opened alarm app for ${"%02d".format(hour)}:${"%02d".format(minute)}.")
    }

    private fun setTimer(seconds: Int, label: String): String {
        if (seconds <= 0) return "set_timer: seconds must be > 0."
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launchOrFail(intent, "Opened timer for ${seconds}s.")
    }

    private fun composeSms(number: String, body: String): String {
        if (number.isBlank()) return "compose_sms: missing 'number'."
        val uri = Uri.parse("smsto:" + Uri.encode(number))
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            if (body.isNotBlank()) putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launchOrFail(intent, "Opened SMS draft to $number.")
    }

    private fun composeEmail(to: String, subject: String, body: String): String {
        val uri = Uri.parse("mailto:" + Uri.encode(to))
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            if (body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launchOrFail(intent, "Opened email draft" + if (to.isBlank()) "." else " to $to.")
    }

    private fun dialNumber(number: String): String {
        if (number.isBlank()) return "dial_number: missing 'number'."
        val uri = Uri.parse("tel:" + Uri.encode(number))
        val intent = Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchOrFail(intent, "Opened dialer for $number.")
    }

    private fun setClipboard(text: String): String {
        if (text.isEmpty()) return "set_clipboard: missing 'text'."
        val cm = context.getSystemService(ClipboardManager::class.java)
            ?: return "set_clipboard: clipboard unavailable."
        cm.setPrimaryClip(ClipData.newPlainText("Hellhound", text))
        return "Copied ${text.length} characters to clipboard."
    }

    private fun launchOrFail(intent: Intent, success: String): String = try {
        context.startActivity(intent)
        success
    } catch (t: Throwable) {
        "Couldn't launch: ${t.message ?: t::class.java.simpleName}"
    }

    companion object {
        private fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        /** OpenAI-compatible JSON Schema describing each tool. */
        val SPECS: List<ToolSpec> = listOf(
            spec(
                "get_screen_text",
                "Read text currently visible on the user's foreground app. Useful when the user asks about what they're looking at.",
                """{"type":"object","properties":{},"required":[]}"""
            ),
            spec(
                "get_recent_notifications",
                "Return recent notifications Android has delivered (newest first).",
                """{"type":"object","properties":{"limit":{"type":"integer","description":"Max notifications to return","default":10}},"required":[]}"""
            ),
            spec(
                "get_device_info",
                "Return basic device info: OS version, manufacturer, model, locale, current local time, battery level.",
                """{"type":"object","properties":{},"required":[]}"""
            ),
            spec(
                "get_clipboard",
                "Return the current text on the system clipboard.",
                """{"type":"object","properties":{},"required":[]}"""
            ),
            spec(
                "now",
                "Return the current local date and time in ISO-8601 format.",
                """{"type":"object","properties":{},"required":[]}"""
            ),
            spec(
                "search_web",
                "Run a web search via DuckDuckGo Instant Answer. Best for factual / definitional queries (Wikipedia-style). Returns nothing for niche queries.",
                """{"type":"object","properties":{"query":{"type":"string","description":"Search query"}},"required":["query"]}"""
            ),
            spec(
                "open_app",
                "Launch another installed app by its full package name (e.g. com.spotify.music). Returns an error if the app is not installed.",
                """{"type":"object","properties":{"package":{"type":"string","description":"Full Android package name"}},"required":["package"]}"""
            ),
            spec(
                "open_url",
                "Open a URL in the user's default browser.",
                """{"type":"object","properties":{"url":{"type":"string","description":"Full URL including https://"}},"required":["url"]}"""
            ),
            spec(
                "set_alarm",
                "Open the system alarm app pre-filled with a time. The user must tap save in the alarm app to actually arm it.",
                """{"type":"object","properties":{"hour":{"type":"integer","description":"0-23"},"minute":{"type":"integer","description":"0-59"},"label":{"type":"string"}},"required":["hour","minute"]}"""
            ),
            spec(
                "set_timer",
                "Open the system timer app pre-filled with a duration in seconds.",
                """{"type":"object","properties":{"seconds":{"type":"integer","description":"Duration in seconds"},"label":{"type":"string"}},"required":["seconds"]}"""
            ),
            spec(
                "compose_sms",
                "Open the user's SMS composer pre-filled with a recipient and body. The user must tap Send.",
                """{"type":"object","properties":{"number":{"type":"string"},"body":{"type":"string"}},"required":["number"]}"""
            ),
            spec(
                "compose_email",
                "Open the user's email composer pre-filled. All fields optional. The user must tap Send.",
                """{"type":"object","properties":{"to":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"}},"required":[]}"""
            ),
            spec(
                "dial_number",
                "Open the dialer pre-filled with a phone number. Does NOT auto-call — the user must tap the call button.",
                """{"type":"object","properties":{"number":{"type":"string"}},"required":["number"]}"""
            ),
            spec(
                "set_clipboard",
                "Replace the system clipboard with the given text.",
                """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}"""
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
