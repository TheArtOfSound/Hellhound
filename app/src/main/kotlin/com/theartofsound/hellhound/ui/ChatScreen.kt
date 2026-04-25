package com.theartofsound.hellhound.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import com.theartofsound.hellhound.R

@Composable
fun ChatScreen(
    state: HellhoundUiState,
    onSendMessage: () -> Unit,
    onCancelStream: () -> Unit,
    onUpdateInput: (String) -> Unit,
    onClearHistory: () -> Unit,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    val voiceAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotEmpty()) {
                val merged = if (state.input.isBlank()) spoken else "${state.input} $spoken"
                onUpdateInput(merged)
                val shouldAutoSend = (state.autoSendVoice || state.voiceFirstMode) && state.apiKey.isNotBlank()
                if (shouldAutoSend) onSendMessage()
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hellhound",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClearHistory) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Clear")
            }
        }

        if (state.messages.isEmpty()) {
            EmptyState(
                hasApiKey = state.apiKey.isNotBlank(),
                onPromptTap = { prompt ->
                    onUpdateInput(prompt)
                    onSendMessage()
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        } else {
            val listState = rememberLazyListState()
            // Only auto-scroll to the latest message when the user is already
            // pinned at (or near) the bottom — never fight a deliberate scroll.
            val pinnedToBottom by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
                    last.index >= state.messages.lastIndex - 1
                }
            }
            LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content?.length) {
                if (pinnedToBottom) {
                    listState.animateScrollToItem(state.messages.lastIndex.coerceAtLeast(0))
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages) { message ->
                    MessageBubble(message)
                }
            }
        }

        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (state.voiceFirstMode) {
            VoiceFirstControls(
                state = state,
                voiceAvailable = voiceAvailable,
                onLaunchVoice = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Hellhound")
                    }
                    runCatching { voiceLauncher.launch(intent) }
                },
                onCancelStream = onCancelStream
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onUpdateInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.chat_hint)) },
                    enabled = !state.sending,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (state.input.isNotBlank()) onSendMessage()
                    })
                )
                if (voiceAvailable && !state.sending) {
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Hellhound")
                        }
                        runCatching { voiceLauncher.launch(intent) }
                    }) {
                        Icon(Icons.Filled.Mic, contentDescription = "Voice input")
                    }
                }
                if (state.sending) {
                    IconButton(onClick = onCancelStream) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop")
                    }
                } else {
                    IconButton(
                        onClick = onSendMessage,
                        enabled = state.input.isNotBlank()
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.chat_send))
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceFirstControls(
    state: HellhoundUiState,
    voiceAvailable: Boolean,
    onLaunchVoice: () -> Unit,
    onCancelStream: () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (state.sending) {
            androidx.compose.material3.FilledIconButton(
                onClick = onCancelStream,
                modifier = Modifier.size(96.dp)
            ) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = "Stop",
                    modifier = Modifier.size(48.dp)
                )
            }
        } else {
            androidx.compose.material3.FilledIconButton(
                onClick = onLaunchVoice,
                enabled = voiceAvailable,
                modifier = Modifier.size(96.dp)
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Speak",
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(
    hasApiKey: Boolean,
    onPromptTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val examples = listOf(
        "What time is it?",
        "What's on my screen?",
        "Summarize my notifications",
        "Set an alarm for 7:00 am",
        "Set a 10 minute timer",
        "What's on my clipboard?",
        "Open com.spotify.music",
        "Search the web for Cerebras inference",
        "What did we talk about earlier?",
        "Battery level?"
    )
    androidx.compose.foundation.layout.Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (hasApiKey) stringResource(R.string.chat_empty)
                else stringResource(R.string.chat_empty_no_key),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        if (hasApiKey) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                examples.forEach { prompt ->
                    androidx.compose.material3.AssistChip(
                        onClick = { onPromptTap(prompt) },
                        label = { Text(prompt) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: UiMessage) {
    val isUser = message.role == "user"
    val bg = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val context = LocalContext.current
    val speaker = remember(context) {
        (context.applicationContext as? com.theartofsound.hellhound.HellhoundApp)?.speaker
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        if (isUser) Box(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(bg, RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        if (message.content.isNotBlank()) {
                            val cm = context.getSystemService(ClipboardManager::class.java)
                            cm?.setPrimaryClip(ClipData.newPlainText("Hellhound", message.content))
                        }
                    }
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val display = message.content.ifEmpty { if (message.streaming) "…" else "" }
            if (isUser) {
                Text(text = display, color = fg)
            } else {
                Text(text = rememberMarkdown(display), color = fg)
            }
        }
        if (!isUser && message.content.isNotBlank() && !message.streaming && speaker != null) {
            IconButton(onClick = { speaker.speak(message.content) }) {
                Icon(
                    Icons.Filled.VolumeUp,
                    contentDescription = "Speak aloud",
                    modifier = Modifier
                )
            }
        }
        if (!isUser) Box(modifier = Modifier.weight(1f))
    }
}
