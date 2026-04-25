package com.theartofsound.hellhound.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.theartofsound.hellhound.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: HellhoundUiState,
    onSaveApiKey: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onSaveSystemPrompt: (String) -> Unit,
    onSetAutoSendVoice: (Boolean) -> Unit,
    onRefreshModels: () -> Unit,
    contentPadding: PaddingValues
) {
    var draft by rememberSaveable(state.apiKey) { mutableStateOf(state.apiKey) }
    var promptDraft by rememberSaveable(state.systemPrompt) { mutableStateOf(state.systemPrompt) }
    var modelDraft by rememberSaveable(state.model) { mutableStateOf(state.model) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(stringResource(R.string.chat_settings), style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(stringResource(R.string.settings_api_key_label)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onSaveApiKey(draft) }) {
                Text(stringResource(R.string.settings_save))
            }
            TextButton(onClick = { draft = ""; onSaveApiKey("") }) {
                Text(stringResource(R.string.settings_clear))
            }
        }

        Text(
            stringResource(R.string.settings_model_label),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)
        )
        Text(
            stringResource(R.string.settings_model_active, state.model.ifBlank { "—" }),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            state.availableModels.forEach { model ->
                FilterChip(
                    selected = state.model == model,
                    onClick = { modelDraft = model; onSelectModel(model) },
                    label = { Text(model) }
                )
            }
        }
        OutlinedTextField(
            value = modelDraft,
            onValueChange = { modelDraft = it },
            label = { Text(stringResource(R.string.settings_model_custom_label)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { onSelectModel(modelDraft) },
                enabled = modelDraft.isNotBlank()
            ) {
                Text(stringResource(R.string.settings_model_use))
            }
            OutlinedButton(
                onClick = onRefreshModels,
                enabled = !state.refreshingModels,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                if (state.refreshingModels) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(end = 8.dp).size(16.dp)
                    )
                }
                Text(stringResource(R.string.settings_model_refresh))
            }
        }
        state.modelRefreshError?.let { err ->
            Text(
                err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Text(
            stringResource(R.string.settings_system_prompt_label),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = promptDraft,
            onValueChange = { promptDraft = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            minLines = 3,
            maxLines = 8
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onSaveSystemPrompt(promptDraft) }) {
                Text(stringResource(R.string.settings_save))
            }
            TextButton(onClick = { promptDraft = ""; onSaveSystemPrompt("") }) {
                Text(stringResource(R.string.settings_system_prompt_reset))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.settings_auto_send_voice),
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.autoSendVoice,
                onCheckedChange = onSetAutoSendVoice
            )
        }
    }
}
