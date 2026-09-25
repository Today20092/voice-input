package org.futo.voiceinput.settings.pages

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.voiceinput.R
import org.futo.voiceinput.diagnostics.AppDiagnostics
import org.futo.voiceinput.settings.SettingItem
import org.futo.voiceinput.settings.SettingToggleRaw
import org.futo.voiceinput.settings.Tip
import java.io.File
import java.util.zip.ZipFile

@Composable
fun AppDiagnosticOptions() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(AppDiagnostics.collectionEnabled()) }
    var remaining by remember { mutableLongStateOf(AppDiagnostics.detailedRemainingMs()) }
    var busy by remember { mutableStateOf(false) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var clearOpen by remember { mutableStateOf(false) }
    var happened by rememberSaveable { mutableStateOf("") }
    var expected by rememberSaveable { mutableStateOf("") }
    var preview by remember { mutableStateOf<File?>(null) }
    var summary by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            enabled = AppDiagnostics.collectionEnabled()
            remaining = AppDiagnostics.detailedRemainingMs()
            delay(1000)
        }
    }

    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
                enabled = AppDiagnostics.collectionEnabled()
                remaining = AppDiagnostics.detailedRemainingMs()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(context, R.string.app_diagnostics_failed, Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    SettingsSeparator(stringResource(R.string.app_diagnostics_heading))
    SettingToggleRaw(
        title = stringResource(R.string.app_diagnostics_collect),
        subtitle = stringResource(R.string.app_diagnostics_collect_description),
        enabled = enabled,
        setValue = { value -> perform { AppDiagnostics.setEnabled(value) } }
    )
    Tip(stringResource(R.string.app_diagnostics_privacy))
    SettingItem(
        title = if (remaining > 0) stringResource(R.string.app_diagnostics_stop_detailed)
            else stringResource(R.string.app_diagnostics_start_detailed),
        subtitle = if (remaining > 0) stringResource(R.string.app_diagnostics_countdown,
            (remaining / 60000).toInt(), ((remaining / 1000) % 60).toInt())
            else stringResource(R.string.app_diagnostics_detailed_description),
        disabled = !enabled || busy,
        onClick = { perform {
            if (remaining > 0) AppDiagnostics.stopDetailed() else AppDiagnostics.startDetailed()
        } }
    ) { }
    SettingItem(
        title = stringResource(if (busy) R.string.app_diagnostics_working else R.string.app_diagnostics_export),
        subtitle = stringResource(R.string.app_diagnostics_export_description),
        disabled = busy,
        onClick = { notesOpen = true }
    ) { }
    SettingItem(
        title = stringResource(R.string.app_diagnostics_clear),
        disabled = busy,
        onClick = { clearOpen = true }
    ) { }

    if (notesOpen) AlertDialog(
        onDismissRequest = { if (!busy) notesOpen = false },
        title = { Text(stringResource(R.string.app_diagnostics_export)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.app_diagnostics_export_contents))
                OutlinedTextField(value = happened, onValueChange = { happened = it.take(4000) },
                    label = { Text(stringResource(R.string.app_diagnostics_happened)) },
                    modifier = Modifier.fillMaxWidth(), enabled = !busy, minLines = 2)
                OutlinedTextField(value = expected, onValueChange = { expected = it.take(4000) },
                    label = { Text(stringResource(R.string.app_diagnostics_expected)) },
                    modifier = Modifier.fillMaxWidth(), enabled = !busy, minLines = 2)
                Text(stringResource(R.string.app_diagnostics_notes_warning))
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { perform {
            val file = AppDiagnostics.export(context, happened, expected)
            summary = withContext(Dispatchers.IO) {
                ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry("summary.txt")).bufferedReader().use { it.readText() } }
            }
            preview = file
            notesOpen = false
        } }) { Text(stringResource(if (busy) R.string.app_diagnostics_working else R.string.app_diagnostics_review)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { notesOpen = false }) { Text(stringResource(R.string.app_diagnostics_cancel)) } }
    )

    preview?.let { file -> AlertDialog(
        onDismissRequest = { preview = null },
        title = { Text(stringResource(R.string.app_diagnostics_review)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.app_diagnostics_export_contents))
                Text(summary)
                if (happened.isNotBlank()) Text(stringResource(R.string.app_diagnostics_happened) + "\n" + happened)
                if (expected.isNotBlank()) Text(stringResource(R.string.app_diagnostics_expected) + "\n" + expected)
                Text(stringResource(R.string.app_diagnostics_limits))
            }
        },
        confirmButton = { TextButton(onClick = {
            try {
                AppDiagnostics.share(context, file)
                preview = null
            } catch (_: Exception) {
                Toast.makeText(context, R.string.app_diagnostics_failed, Toast.LENGTH_LONG).show()
            }
        }) { Text(stringResource(R.string.app_diagnostics_share)) } },
        dismissButton = { TextButton(onClick = { preview = null }) { Text(stringResource(R.string.app_diagnostics_cancel)) } }
    ) }

    if (clearOpen) AlertDialog(
        onDismissRequest = { clearOpen = false },
        title = { Text(stringResource(R.string.app_diagnostics_clear)) },
        text = { Text(stringResource(R.string.app_diagnostics_clear_description)) },
        confirmButton = { TextButton(enabled = !busy, onClick = { perform {
            AppDiagnostics.clear(context)
            preview = null
            clearOpen = false
            Toast.makeText(context, R.string.app_diagnostics_cleared, Toast.LENGTH_SHORT).show()
        } }) { Text(stringResource(R.string.app_diagnostics_clear)) } },
        dismissButton = { TextButton(onClick = { clearOpen = false }) { Text(stringResource(R.string.app_diagnostics_cancel)) } }
    )
}
