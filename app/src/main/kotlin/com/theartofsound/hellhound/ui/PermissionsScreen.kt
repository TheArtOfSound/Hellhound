package com.theartofsound.hellhound.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.theartofsound.hellhound.R

@Composable
fun PermissionsScreen(
    state: HellhoundUiState,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
    ) {
        Text(stringResource(R.string.permissions_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.permissions_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        PermissionCard(
            title = stringResource(R.string.permission_accessibility_title),
            body = stringResource(R.string.permission_accessibility_body),
            enabled = state.accessibilityEnabled,
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                onRefresh()
            }
        )

        PermissionCard(
            title = stringResource(R.string.permission_notifications_title),
            body = stringResource(R.string.permission_notifications_body),
            enabled = state.notificationAccessEnabled,
            onOpenSettings = {
                context.startActivity(
                    Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                onRefresh()
            }
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    enabled: Boolean,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                body,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            val statusRes = if (enabled) R.string.permission_enabled else R.string.permission_disabled
            Text(
                stringResource(statusRes),
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.permission_open_settings))
            }
        }
    }
}
