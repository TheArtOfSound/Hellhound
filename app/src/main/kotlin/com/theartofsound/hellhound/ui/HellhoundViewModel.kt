package com.theartofsound.hellhound.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theartofsound.hellhound.HellhoundApp
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

data class UiMessage(val role: String, val content: String, val streaming: Boolean = false)

data class HellhoundUiState(
    val messages: List<UiMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val error: String? = null,
    val apiKey: String = "",
    val model: String = "",
    val systemPrompt: String = "",
    val availableModels: List<String> = emptyList(),
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
            val stored = hellhound.settings.history.first()
            _uiState.value = _uiState.value.copy(
                apiKey = key.orEmpty(),
                model = model,
                systemPrompt = systemPrompt,
                messages = stored.map { UiMessage(it.role, it.content) }
            )
        }
        refreshPermissions()
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
        }
    }

    fun selectModel(model: String) {
        viewModelScope.launch {
            hellhound.settings.setModel(model)
            _uiState.value = _uiState.value.copy(model = model)
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
            val history = (_uiState.value.messages.dropLast(1)).map {
                ChatMessage(role = it.role, content = it.content)
            }
            val systemPrompt = buildSystemPrompt()
            try {
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
        if (last != null && last.streaming) {
            if (error != null && last.content.isEmpty()) {
                updated.removeAt(updated.lastIndex)
            } else {
                updated[updated.lastIndex] = last.copy(streaming = false)
            }
        }
        _uiState.value = state.copy(messages = updated, sending = false, error = error)
        persistHistory()
    }

    private fun buildSystemPrompt(): String {
        val state = _uiState.value
        val basePrompt = state.systemPrompt.ifBlank {
            com.theartofsound.hellhound.data.SettingsStore.DEFAULT_SYSTEM_PROMPT
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
    }
}
