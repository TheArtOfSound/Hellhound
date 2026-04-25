package com.theartofsound.hellhound

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
        setContent {
            HellhoundTheme {
                val state by viewModel.uiState.collectAsState()
                Surface(modifier = Modifier.fillMaxSize()) {
                    HellhoundRoot(
                        state = state,
                        onSendMessage = viewModel::send,
                        onUpdateInput = viewModel::updateInput,
                        onSaveApiKey = viewModel::saveApiKey,
                        onSelectModel = viewModel::selectModel,
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
}
