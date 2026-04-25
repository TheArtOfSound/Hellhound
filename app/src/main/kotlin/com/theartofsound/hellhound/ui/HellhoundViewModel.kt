package com.theartofsound.hellhound.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theartofsound.hellhound.HellhoundApp
import com.theartofsound.hellhound.data.cerebras.ChatMessage
import com.theartofsound.hellhound.services.HellhoundAccessibilityService
import com.theartofsound.hellhound.services.HellhoundNotificationListener
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

    init {
        viewModelScope.launch {
            val key = hellhound.settings.apiKey.first()
            hellhound.updateCachedKey(key)
            val model = hellhound.settings.model.first()
            _uiState.value = _uiState.value.copy(apiKey = key.orEmpty(), model = model)
        }
        refreshPermissions()
    }

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(input = text)
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
        _uiState.value = _uiState.value.copy(messages = emptyList(), error = null)
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
        viewModelScope.launch {
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
                val state = _uiState.value
                val updated = state.messages.toMutableList()
                val last = updated.last()
                updated[updated.lastIndex] = last.copy(streaming = false)
                _uiState.value = state.copy(messages = updated, sending = false)
            } catch (t: Throwable) {
                val state = _uiState.value
                val updated = state.messages.toMutableList().apply { removeLast() }
                _uiState.value = state.copy(
                    messages = updated,
                    sending = false,
                    error = t.message ?: "Request failed"
                )
            }
        }
    }

    private fun buildSystemPrompt(): String {
        val state = _uiState.value
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
            appendLine("You are Hellhound, a personal Android assistant powered by Cerebras inference.")
            appendLine("Be concise. Cite which context you used (screen / notifications) when relevant.")
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
