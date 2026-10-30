package org.futo.voiceinput.settings.pages

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.voiceinput.R
import org.futo.voiceinput.history.AudioHistoryStore
import org.futo.voiceinput.history.audioHistory
import org.futo.voiceinput.history.retranscribeAudio
import org.futo.voiceinput.settings.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioHistoryScreen(navController: NavHostController) {
    val context = LocalContext.current
    val store = remember(context) { context.audioHistory() }
    val scope = rememberCoroutineScope()
    val lifecycleScope = LocalLifecycleOwner.current.lifecycleScope
    val clipboard = LocalClipboardManager.current
    val retention = useDataStore(AUDIO_HISTORY_RETENTION_HOURS)
    var hours by remember { mutableStateOf(retention.value.toString()) }
    var entries by remember { mutableStateOf<List<AudioHistoryStore.Entry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    var transcript by remember { mutableStateOf<String?>(null) }
    var reading by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var clearResult by remember { mutableStateOf<AudioHistoryStore.ClearResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val requestedHours = hours.toIntOrNull()?.takeIf { it in 1..720 }

    LaunchedEffect(retention.value) { hours = retention.value.toString() }
    LaunchedEffect(retention.value, revision, selected) {
        transcript = null
        reading = true
        while (true) {
            try {
                val (updatedEntries, updatedTranscript) = withContext(Dispatchers.IO) {
                    store.purge(context.getSetting(AUDIO_HISTORY_RETENTION_HOURS))
                    store.entries() to selected?.let(store::transcript)
                }
                entries = updatedEntries
                transcript = updatedTranscript
                if (entries.none { it.id == selected }) selected = null
            } catch (failure: CancellationException) { throw failure
            } catch (failure: Exception) { error = context.getString(R.string.audio_history_read_failed) }
            loaded = true
            reading = false
            delay(15_000)
        }
    }

    SettingListLazy {
        item {
            ScreenTitle(stringResource(R.string.audio_history), true, navController)
            SettingToggleDataStore(stringResource(R.string.audio_history_save), AUDIO_HISTORY_ENABLED,
                subtitle = stringResource(R.string.audio_history_private))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.audio_history_retention_info),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = hours, onValueChange = { hours = it },
                    label = { Text(stringResource(R.string.audio_history_keep_hours)) },
                    supportingText = { Text(stringResource(R.string.audio_history_hours_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, isError = requestedHours == null, modifier = Modifier.fillMaxWidth())
                Button(enabled = requestedHours != null && requestedHours != retention.value && running == null,
                    onClick = {
                        scope.launch {
                            try {
                                context.setSetting(AUDIO_HISTORY_RETENTION_HOURS, requestedHours!!)
                                revision++
                            } catch (failure: CancellationException) { throw failure
                            } catch (failure: Exception) { error = context.getString(R.string.audio_history_save_failed) }
                        }
                    }) { Text(stringResource(R.string.audio_history_apply)) }
                Text(stringResource(R.string.audio_history_model_info),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (loaded && entries.isEmpty()) Text(stringResource(R.string.audio_history_empty))
                if (entries.isNotEmpty()) TextButton(
                    enabled = !clearing,
                    onClick = { confirmClear = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.audio_history_clear)) }
                if (clearing) LinearProgressIndicator(Modifier.fillMaxWidth())
                clearResult?.let { result ->
                    Text(stringResource(R.string.audio_history_clear_result,
                        result.deleted, result.inUse, result.failed),
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (selected == null) error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        items(entries, key = { it.id }) { entry ->
            var previewOverflows by remember(entry.preview) { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(DateFormat.getDateTimeInstance().format(Date(entry.createdAt)),
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.audio_history_duration,
                        String.format(Locale.getDefault(), "%.1f", entry.durationSeconds)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (entry.busy || running == entry.id) Text(stringResource(
                        if (running == entry.id) R.string.audio_history_transcribing else R.string.audio_history_recording))
                    if (selected != entry.id) {
                        Text(entry.preview ?: stringResource(R.string.audio_history_no_text),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (entry.preview == null) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { previewOverflows = it.hasVisualOverflow })
                    }
                    if (selected == entry.id) {
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (reading || running == entry.id) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (!reading) SelectionContainer {
                            Text(transcript?.takeIf { it.isNotBlank() } ?: stringResource(R.string.audio_history_no_text),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    // The store caps previews at 240 characters and appends an ellipsis.
                    if (selected == entry.id || (entry.preview != null &&
                        (previewOverflows || entry.preview.length > 240))) {
                        TextButton(enabled = running == null && !clearing, onClick = {
                            selected = if (selected == entry.id) null else entry.id
                            error = null
                        }) { Text(stringResource(if (selected == entry.id) R.string.audio_history_hide else R.string.audio_history_open)) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !entry.busy && running == null && !reading && !clearing, onClick = {
                            selected = entry.id
                            running = entry.id
                            error = null
                            scope.launch {
                                try { transcript = context.retranscribeAudio(entry.id, lifecycleScope)
                                } catch (failure: CancellationException) { throw failure
                                } catch (failure: OutOfMemoryError) { error = context.getString(R.string.audio_history_memory_failed)
                                } catch (failure: Exception) { error = context.getString(R.string.audio_history_transcribe_failed)
                                } finally { running = null; revision++ }
                            }
                        }) { Text(stringResource(R.string.audio_history_retranscribe)) }
                        TextButton(enabled = !entry.preview.isNullOrEmpty() && running == null && !clearing,
                            onClick = {
                                scope.launch(Dispatchers.Main) {
                                    try {
                                        val text = withContext(Dispatchers.IO) { store.transcript(entry.id) }
                                        if (!text.isNullOrEmpty()) {
                                            clipboard.setText(AnnotatedString(text))
                                            Toast.makeText(context, R.string.audio_history_copied, Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, R.string.audio_history_no_text, Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (failure: CancellationException) { throw failure
                                    } catch (failure: Exception) {
                                        Toast.makeText(context, R.string.audio_history_read_failed, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) { Text(stringResource(R.string.audio_history_copy)) }
                        TextButton(enabled = !entry.busy && running == null && !clearing,
                            onClick = { deleteId = entry.id }) { Text(stringResource(R.string.audio_history_delete)) }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.audio_history_clear_question)) },
            text = { Text(stringResource(R.string.audio_history_clear_info)) },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(android.R.string.cancel)) }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    clearing = true
                    clearResult = null
                    error = null
                    scope.launch {
                        try {
                            clearResult = withContext(Dispatchers.IO) { store.clear() }
                            // A running retranscription is protected and keeps its detail view.
                            if (running == null) selected = null
                        } catch (failure: CancellationException) { throw failure
                        } catch (failure: Exception) { error = context.getString(R.string.audio_history_clear_failed)
                        } finally { clearing = false; revision++ }
                    }
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.audio_history_clear))
                }
            })
    }
    deleteId?.let { id ->
        AlertDialog(onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.audio_history_delete_question)) },
            text = { Text(stringResource(R.string.audio_history_delete_info)) },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(android.R.string.cancel)) } },
            confirmButton = {
                TextButton(onClick = {
                    deleteId = null
                    scope.launch {
                        try {
                            check(withContext(Dispatchers.IO) { store.delete(id) })
                            selected = null
                            revision++
                        } catch (failure: CancellationException) { throw failure
                        } catch (failure: Exception) { error = context.getString(R.string.audio_history_delete_failed) }
                    }
                }) { Text(stringResource(R.string.audio_history_delete)) }
            })
    }
}
