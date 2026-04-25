package com.theartofsound.hellhound.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private enum class Tab { Chat, Permissions, Settings }

@Composable
fun HellhoundRoot(
    state: HellhoundUiState,
    onSendMessage: () -> Unit,
    onCancelStream: () -> Unit,
    onUpdateInput: (String) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onClearHistory: () -> Unit,
    onRefreshPermissions: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Chat) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.Chat,
                    onClick = { tab = Tab.Chat },
                    icon = { Icon(Icons.Filled.Chat, contentDescription = null) },
                    label = { Text("Chat") }
                )
                NavigationBarItem(
                    selected = tab == Tab.Permissions,
                    onClick = { tab = Tab.Permissions; onRefreshPermissions() },
                    icon = { Icon(Icons.Filled.Shield, contentDescription = null) },
                    label = { Text("Access") }
                )
                NavigationBarItem(
                    selected = tab == Tab.Settings,
                    onClick = { tab = Tab.Settings },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (tab) {
                Tab.Chat -> ChatScreen(
                    state = state,
                    onSendMessage = onSendMessage,
                    onCancelStream = onCancelStream,
                    onUpdateInput = onUpdateInput,
                    onClearHistory = onClearHistory,
                    contentPadding = padding
                )
                Tab.Permissions -> PermissionsScreen(
                    state = state,
                    contentPadding = padding,
                    onRefresh = onRefreshPermissions
                )
                Tab.Settings -> SettingsScreen(
                    state = state,
                    onSaveApiKey = onSaveApiKey,
                    onSelectModel = onSelectModel,
                    contentPadding = padding
                )
            }
        }
    }
}
