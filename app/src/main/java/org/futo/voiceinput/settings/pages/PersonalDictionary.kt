package org.futo.voiceinput.settings.pages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.futo.voiceinput.DictionaryImport
import org.futo.voiceinput.R
import org.futo.voiceinput.settings.PERSONAL_DICTIONARY
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.settings.setSetting
import java.nio.charset.CharacterCodingException

@Composable
fun PersonalDictionaryEditor(disabled: Boolean, showTitle: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Finish already requested saves even when the user leaves this settings page.
    val saveScope = LocalLifecycleOwner.current.lifecycleScope
    val saveMutex = remember { Mutex() }
    var value by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var saving by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<Int?>(null) }
    var bulk by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<DictionaryImport.Preview?>(null) }

    LaunchedEffect(Unit) {
        try {
            value = context.getSetting(PERSONAL_DICTIONARY)
            loaded = true
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { error = R.string.dictionary_read_failed }
    }

    fun save(next: String, onSaved: () -> Unit = {}) {
        saving++
        saveScope.launch {
            try {
                saveMutex.withLock { context.setSetting(PERSONAL_DICTIONARY, next) }
                error = null
                onSaved()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { error = R.string.dictionary_save_failed
            } finally { saving-- }
        }
    }

    fun showPreview(text: String) {
        reading = true
        scope.launch {
            try {
                preview = withContext(Dispatchers.Default) { DictionaryImport.preview(value, text) }
            } finally { reading = false }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            reading = true
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use(DictionaryImport::read)
                            ?: error("Cannot open document")
                    }
                    // The document picker may recreate the activity before returning.
                    value = context.getSetting(PERSONAL_DICTIONARY)
                    loaded = true
                    bulk = text
                    preview = withContext(Dispatchers.Default) { DictionaryImport.preview(value, text) }
                    error = null
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: CharacterCodingException) { error = R.string.dictionary_invalid_utf8
                } catch (_: IllegalArgumentException) { error = R.string.dictionary_too_large
                } catch (_: Exception) { error = R.string.dictionary_import_failed
                } finally { reading = false }
            }
        }
    }

    if (showTitle) ScreenTitle(stringResource(R.string.personal_dictionary))
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.dictionary_help), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.personal_dictionary_placeholder),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = value, onValueChange = { value = it; save(it) },
            label = { Text(stringResource(R.string.dictionary_entries)) },
            modifier = Modifier.fillMaxWidth(), minLines = 5, maxLines = 12,
            enabled = loaded && !disabled && !reading && bulk == null)
        if (reading || !loaded && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (saving > 0) Text(stringResource(R.string.dictionary_saving), style = MaterialTheme.typography.bodySmall)
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        if (error == R.string.dictionary_save_failed) {
            TextButton(onClick = { save(value) }, enabled = saving == 0) { Text(stringResource(R.string.dictionary_retry_save)) }
        }
        Button(onClick = { bulk = ""; preview = null; error = null }, enabled = loaded && !disabled && !reading && saving == 0) {
            Text(stringResource(R.string.dictionary_add_multiple))
        }
        OutlinedButton(onClick = { picker.launch(arrayOf("text/plain", "text/*")) },
            enabled = loaded && !disabled && !reading && saving == 0) {
            Text(stringResource(R.string.dictionary_import_file))
        }
    }

    bulk?.let { text ->
        val result = preview
        AlertDialog(
            onDismissRequest = { if (!reading && saving == 0) { bulk = null; preview = null } },
            title = { Text(stringResource(if (result == null) R.string.dictionary_add_multiple else R.string.dictionary_preview)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (result == null) {
                        Text(stringResource(R.string.personal_dictionary_placeholder))
                        OutlinedTextField(value = text, onValueChange = {
                            if (it.toByteArray(Charsets.UTF_8).size <= DictionaryImport.MAX_BYTES) {
                                bulk = it
                                error = null
                            } else error = R.string.dictionary_too_large
                        }, enabled = !reading, modifier = Modifier.fillMaxWidth(), minLines = 5, maxLines = 10)
                    } else {
                        Text(stringResource(R.string.dictionary_preview_count, result.additions.size, result.duplicates, result.invalid.size))
                        Text(result.additions.take(100).joinToString("\n"))
                        if (result.additions.size > 100) Text(stringResource(R.string.dictionary_preview_truncated))
                        if (result.invalid.isNotEmpty()) {
                            Text(stringResource(R.string.dictionary_invalid_entries), color = MaterialTheme.colorScheme.error)
                            Text(result.invalid.take(10).joinToString("\n"))
                        }
                        TextButton(onClick = { preview = null }, enabled = !reading && saving == 0) {
                            Text(stringResource(R.string.dictionary_edit_import))
                        }
                    }
                    if (reading || saving > 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !reading && saving == 0 &&
                    (if (result == null) text.isNotBlank() else result.additions.isNotEmpty() && result.invalid.isEmpty()),
                    onClick = {
                        if (result == null) showPreview(text)
                        else {
                            val merged = result.appendTo(value)
                            save(merged) { value = merged; bulk = null; preview = null }
                        }
                    }) {
                    Text(stringResource(if (result == null) R.string.dictionary_preview else R.string.dictionary_add_entries))
                }
            },
            dismissButton = {
                TextButton(onClick = { bulk = null; preview = null }, enabled = !reading && saving == 0) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}
