package com.theartofsound.hellhound

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.theartofsound.hellhound.ui.HellhoundRoot
import com.theartofsound.hellhound.ui.HellhoundViewModel
import com.theartofsound.hellhound.ui.theme.HellhoundTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HellhoundViewModel by viewModels {
        val app = application as HellhoundApp
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HellhoundViewModel(app) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeSharedText(intent)
        setContent {
            HellhoundTheme {
                val state by viewModel.uiState.collectAsState()
                Surface(modifier = Modifier.fillMaxSize()) {
                    HellhoundRoot(
                        state = state,
                        onSendMessage = viewModel::send,
                        onCancelStream = viewModel::cancelStream,
                        onUpdateInput = viewModel::updateInput,
                        onSaveApiKey = viewModel::saveApiKey,
                        onSelectModel = viewModel::selectModel,
                        onSaveSystemPrompt = viewModel::saveSystemPrompt,
                        onSetAutoSendVoice = viewModel::setAutoSendVoice,
                        onSetAgentMode = viewModel::setAgentMode,
                        onSetAutoSpeak = viewModel::setAutoSpeak,
                        onSetVoiceFirstMode = viewModel::setVoiceFirstMode,
                        onSetDailyBriefing = viewModel::setDailyBriefingEnabled,
                        onRefreshModels = viewModel::refreshModels,
                        onClearHistory = viewModel::clearHistory,
                        onRefreshPermissions = viewModel::refreshPermissions
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeSharedText(intent)
    }

    private fun consumeSharedText(intent: Intent?) {
        intent ?: return
        val shared: String? = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        val trimmed = shared?.trim().orEmpty()
        if (trimmed.isNotEmpty()) viewModel.appendInputFromShare(trimmed)
    }
}
