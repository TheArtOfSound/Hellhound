package com.theartofsound.hellhound.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theartofsound.hellhound.HellhoundApp
import com.theartofsound.hellhound.data.ArchivedMessage
import com.theartofsound.hellhound.data.StoredMessage
import com.theartofsound.hellhound.data.cerebras.ChatMessage
import com.theartofsound.hellhound.services.HellhoundAccessibilityService
import com.theartofsound.hellhound.services.HellhoundNotificationListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class UiMessage(
    val role: String,
    val content: String,
    val streaming: Boolean = false,
    val traces: List<String> = emptyList()
)

data class HellhoundUiState(
    val messages: List<UiMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val error: String? = null,
    val apiKey: String = "",
    val model: String = "",
    val systemPrompt: String = "",
    val autoSendVoice: Boolean = false,
    val agentMode: Boolean = true,
    val autoSpeak: Boolean = false,
    val voiceFirstMode: Boolean = false,
    val dailyBriefingEnabled: Boolean = false,
    val availableModels: List<String> = emptyList(),
    val refreshingModels: Boolean = false,
    val modelRefreshError: String? = null,
    val accessibilityEnabled: Boolean = false,
    val notificationAccessEnabled: Boolean = false
)

class HellhoundViewModel(app: Application) : AndroidViewModel(app) {

    private val hellhound: HellhoundApp = app as HellhoundApp
    private val _uiState = MutableStateFlow(HellhoundUiState(
        availableModels = com.theartofsound.hellhound.data.cerebras.CerebrasModelIds.all
    ))
    val uiState: StateFlow<HellhoundUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            val key = hellhound.settings.apiKey.first()
            hellhound.updateCachedKey(key)
            val model = hellhound.settings.model.first()
            val systemPrompt = hellhound.settings.systemPrompt.first()
            val autoSend = hellhound.settings.autoSendVoice.first()
            val agent = hellhound.settings.agentMode.first()
            val autoSpeak = hellhound.settings.autoSpeak.first()
            val voiceFirst = hellhound.settings.voiceFirstMode.first()
            val briefing = hellhound.settings.dailyBriefingEnabled.first()
            val lastBriefingDate = hellhound.settings.lastBriefingDate.first()
            val stored = hellhound.settings.history.first()
            _uiState.value = _uiState.value.copy(
                apiKey = key.orEmpty(),
                model = model,
                systemPrompt = systemPrompt,
                autoSendVoice = autoSend,
                agentMode = agent,
                autoSpeak = autoSpeak,
                voiceFirstMode = voiceFirst,
                dailyBriefingEnabled = briefing,
                messages = stored.map { UiMessage(it.role, it.content) }
            )
            // Pull the real model list as soon as we have a key; avoids the
            // user staring at stale chips.
            if (!key.isNullOrBlank()) refreshModels()

            // Daily briefing: if opt-in is on, key is set, agent mode is on,
            // it's a new calendar day, and the chat is empty (don't interrupt
            // an in-flight conversation), kick off a briefing turn.
            val today = todayLocalDate()
            if (briefing && !key.isNullOrBlank() && agent &&
                lastBriefingDate != today && stored.isEmpty()
            ) {
                hellhound.settings.setLastBriefingDate(today)
                triggerBriefing()
            }
        }
        refreshPermissions()
    }

    private fun triggerBriefing() {
        val seed = "Daily briefing — tell me the time, the battery level, and " +
            "summarize my recent notifications. Then ask if there's anything I want to do today."
        _uiState.value = _uiState.value.copy(input = seed)
        send()
    }

    fun setAutoSendVoice(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoSendVoice = enabled)
        viewModelScope.launch { hellhound.settings.setAutoSendVoice(enabled) }
    }

    fun setAgentMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(agentMode = enabled)
        viewModelScope.launch { hellhound.settings.setAgentMode(enabled) }
    }

    fun setAutoSpeak(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoSpeak = enabled)
        viewModelScope.launch { hellhound.settings.setAutoSpeak(enabled) }
    }

    fun setVoiceFirstMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(voiceFirstMode = enabled)
        viewModelScope.launch { hellhound.settings.setVoiceFirstMode(enabled) }
    }

    fun setDailyBriefingEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(dailyBriefingEnabled = enabled)
        viewModelScope.launch { hellhound.settings.setDailyBriefingEnabled(enabled) }
    }

    private fun todayLocalDate(): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getDefault()
        return fmt.format(java.util.Date())
    }

    fun saveSystemPrompt(prompt: String) {
        viewModelScope.launch {
            hellhound.settings.setSystemPrompt(prompt)
            val effective = if (prompt.trim().isEmpty()) {
                com.theartofsound.hellhound.data.SettingsStore.DEFAULT_SYSTEM_PROMPT
            } else prompt.trim()
            _uiState.value = _uiState.value.copy(systemPrompt = effective)
        }
    }

    private fun persistHistory() {
        val toSave = _uiState.value.messages
            .filter { !it.streaming && it.content.isNotBlank() }
            .map { StoredMessage(role = it.role, content = it.content) }
        viewModelScope.launch { hellhound.settings.setHistory(toSave) }
    }

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(input = text)
    }

    fun appendInputFromShare(shared: String) {
        val current = _uiState.value.input
        val merged = if (current.isBlank()) shared else "$current\n\n$shared"
        _uiState.value = _uiState.value.copy(input = merged)
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            val trimmed = key.trim().ifBlank { null }
            hellhound.settings.setApiKey(trimmed)
            hellhound.updateCachedKey(trimmed)
            _uiState.value = _uiState.value.copy(apiKey = trimmed.orEmpty(), error = null)
            if (!trimmed.isNullOrBlank()) refreshModels()
        }
    }

    fun selectModel(model: String) {
        val trimmed = model.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            hellhound.settings.setModel(trimmed)
            _uiState.value = _uiState.value.copy(model = trimmed)
        }
    }

    fun refreshModels() {
        if (_uiState.value.refreshingModels) return
        if (_uiState.value.apiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                modelRefreshError = "Save your Cerebras API key first."
            )
            return
        }
        _uiState.value = _uiState.value.copy(refreshingModels = true, modelRefreshError = null)
        viewModelScope.launch {
            try {
                val models = hellhound.repository.listModels()
                val effectiveList = models.ifEmpty { _uiState.value.availableModels }
                val currentModel = _uiState.value.model
                val agent = _uiState.value.agentMode
                val newModel = pickModel(currentModel, effectiveList, preferToolCapable = agent)
                if (newModel != currentModel && newModel.isNotBlank()) {
                    hellhound.settings.setModel(newModel)
                }
                _uiState.value = _uiState.value.copy(
                    availableModels = effectiveList,
                    model = newModel,
                    refreshingModels = false,
                    modelRefreshError = if (models.isEmpty()) "No models returned." else null
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    refreshingModels = false,
                    modelRefreshError = t.message?.takeIf { it.isNotBlank() }
                        ?: t::class.java.simpleName
                )
            }
        }
    }

    fun clearHistory() {
        cancelStream()
        _uiState.value = _uiState.value.copy(messages = emptyList(), error = null)
        persistHistory()
    }

    fun cancelStream() {
        streamJob?.cancel()
    }

    /**
     * Re-send the last user message, useful after an error. We pop the
     * last user message off the on-screen list (and any dangling
     * assistant bubble), put it back in the input, and call send().
     */
    fun retryLast() {
        val state = _uiState.value
        if (state.sending) return
        val lastUser = state.messages.lastOrNull { it.role == "user" } ?: return
        val cleaned = state.messages.dropLastWhile { it.role != "user" || it == lastUser }
            .let { list ->
                if (list.isEmpty()) emptyList()
                else if (list.last() == lastUser) list.dropLast(1) else list
            }
        _uiState.value = state.copy(messages = cleaned, input = lastUser.content, error = null)
        send()
    }

    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        _uiState.value = _uiState.value.copy(
            accessibilityEnabled = HellhoundAccessibilityService.isEnabled(ctx),
            notificationAccessEnabled = HellhoundNotificationListener.isEnabled(ctx)
        )
    }

    fun send() {
        val current = _uiState.value
        val prompt = current.input.trim()
        if (prompt.isEmpty() || current.sending) return
        if (current.apiKey.isBlank()) {
            _uiState.value = current.copy(error = "Add your Cerebras API key in Settings.")
            return
        }
        val userMessage = UiMessage("user", prompt)
        _uiState.value = current.copy(
            messages = current.messages + userMessage + UiMessage("assistant", "", streaming = true),
            input = "",
            sending = true,
            error = null
        )
        streamJob = viewModelScope.launch {
            val rawHistory = _uiState.value.messages.dropLast(1)
            val truncated = rawHistory.size > API_HISTORY_LIMIT
            val historySlice = if (truncated) rawHistory.takeLast(API_HISTORY_LIMIT) else rawHistory
            val history = historySlice.map {
                ChatMessage(role = it.role, content = it.content)
            }
            val systemPrompt = buildSystemPrompt(historyTruncated = truncated)
            try {
                if (_uiState.value.agentMode) {
                    hellhound.repository.streamAgent(
                        model = _uiState.value.model,
                        history = history,
                        tools = com.theartofsound.hellhound.tools.ToolDispatcher.SPECS,
                        executeTool = { name, argsJson -> hellhound.tools.dispatch(name, argsJson) },
                        systemPrompt = systemPrompt
                    ) { event ->
                        val state = _uiState.value
                        val updated = state.messages.toMutableList()
                        val last = updated.last()
                        when (event) {
                            is com.theartofsound.hellhound.data.cerebras.CerebrasClient.StreamEvent.Content -> {
                                updated[updated.lastIndex] = last.copy(
                                    content = last.content + event.text
                                )
                            }
                            is com.theartofsound.hellhound.data.cerebras.CerebrasClient.StreamEvent.Trace -> {
                                updated[updated.lastIndex] = last.copy(
                                    traces = last.traces + event.name
                                )
                            }
                        }
                        _uiState.value = state.copy(messages = updated)
                    }
                    finalizeStream(error = null)
                } else {
                    hellhound.repository.streamReply(
                        model = _uiState.value.model,
                        history = history,
                        systemPrompt = systemPrompt
                    ).collect { chunk ->
                        val state = _uiState.value
                        val updated = state.messages.toMutableList()
                        val last = updated.last()
                        updated[updated.lastIndex] = last.copy(content = last.content + chunk)
                        _uiState.value = state.copy(messages = updated)
                    }
                    finalizeStream(error = null)
                }
            } catch (cancel: CancellationException) {
                finalizeStream(error = null)
                throw cancel
            } catch (t: Throwable) {
                finalizeStream(error = t.message ?: "Request failed")
            }
        }
    }

    private fun finalizeStream(error: String?) {
        val state = _uiState.value
        val updated = state.messages.toMutableList()
        val last = updated.lastOrNull()
        var spokenText: String? = null
        var archiveTurn: List<ArchivedMessage> = emptyList()
        if (last != null && last.streaming) {
            if (error != null && last.content.isEmpty()) {
                updated.removeAt(updated.lastIndex)
            } else {
                updated[updated.lastIndex] = last.copy(streaming = false)
                if (error == null) {
                    spokenText = last.content
                    val now = System.currentTimeMillis()
                    val penultimate = updated.getOrNull(updated.lastIndex - 1)
                    archiveTurn = listOfNotNull(
                        penultimate?.takeIf { it.role == "user" }?.let {
                            ArchivedMessage("user", it.content, now)
                        },
                        ArchivedMessage("assistant", last.content, now)
                    )
                }
            }
        }
        _uiState.value = state.copy(messages = updated, sending = false, error = error)
        persistHistory()
        if (archiveTurn.isNotEmpty()) {
            viewModelScope.launch { hellhound.memory.append(archiveTurn) }
        }
        if ((state.autoSpeak || state.voiceFirstMode) && !spokenText.isNullOrBlank()) {
            hellhound.speaker.speak(spokenText)
        }
    }

    private fun buildSystemPrompt(historyTruncated: Boolean = false): String {
        val state = _uiState.value
        val basePrompt = state.systemPrompt.ifBlank {
            com.theartofsound.hellhound.data.SettingsStore.DEFAULT_SYSTEM_PROMPT
        }
        // In agent mode the model has tools and can fetch context itself,
        // so we skip the manual dump to save tokens. In plain mode we
        // pre-load whatever is already cached so it's available without
        // an extra round-trip.
        if (state.agentMode) {
            return if (historyTruncated) {
                "$basePrompt\n\nNote: only the last $API_HISTORY_LIMIT messages of " +
                    "this session are in your context window. Earlier turns are " +
                    "archived — call recall(query) or recent_history(limit) to " +
                    "look them up when the user references something older."
            } else basePrompt
        }
        val context = buildString {
            if (state.accessibilityEnabled) {
                val screen = HellhoundAccessibilityService.lastScreenSnapshot()
                if (!screen.isNullOrBlank()) {
                    appendLine("[Foreground screen text — read at user request]")
                    appendLine(screen.take(SCREEN_LIMIT))
                }
            }
            if (state.notificationAccessEnabled) {
                val notes = HellhoundNotificationListener.recentNotifications()
                if (notes.isNotEmpty()) {
                    appendLine("[Recent notifications]")
                    notes.take(NOTIFICATION_LIMIT).forEach { appendLine("- $it") }
                }
            }
        }.trim()
        return buildString {
            appendLine(basePrompt)
            if (context.isNotBlank()) {
                appendLine()
                append(context)
            }
        }
    }

    private companion object {
        const val SCREEN_LIMIT = 4000
        const val NOTIFICATION_LIMIT = 10
        const val API_HISTORY_LIMIT = 20

        // Cerebras model prefixes empirically observed to emit proper
        // OpenAI tool_calls. llama3.x-8b inlines the call as plain text
        // and is unusable in agent mode.
        private val TOOL_CAPABLE_PREFIXES = listOf(
            "qwen-3-235b",
            "gpt-oss-",
            "qwen-3-32b",
            "llama-3.3-",
            "llama-4-",
            "deepseek-r1"
        )

        private fun isToolCapable(id: String): Boolean =
            TOOL_CAPABLE_PREFIXES.any { id.startsWith(it) }

        private fun pickModel(
            current: String,
            available: List<String>,
            preferToolCapable: Boolean
        ): String {
            if (available.isEmpty()) return current
            val firstToolCapable = available.firstOrNull(::isToolCapable)
            return when {
                preferToolCapable && firstToolCapable != null && !isToolCapable(current) ->
                    firstToolCapable
                current.isBlank() || current !in available ->
                    firstToolCapable ?: available.first()
                else -> current
            }
        }
    }
}
