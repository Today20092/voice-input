package org.futo.voiceinput.settings.pages

import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.futo.voiceinput.s1.S1MiniBenchmark
import org.futo.voiceinput.s1.S1MiniClient
import org.futo.voiceinput.s1.S1MiniDiagnostics
import org.futo.voiceinput.s1.S1MiniModel
import org.futo.voiceinput.s1.S1MiniTranscriptCapture
import org.futo.voiceinput.settings.S1_MINI_CONTEXT
import org.futo.voiceinput.settings.S1_MINI_ENABLED
import org.futo.voiceinput.settings.S1_MINI_RUNTIME
import org.futo.voiceinput.settings.S1_MINI_STRUCTURE
import org.futo.voiceinput.settings.S1_MINI_STYLING
import org.futo.voiceinput.settings.S1_MINI_TRANSCRIPT_DIAGNOSTICS
import org.futo.voiceinput.settings.S1_MINI_WARM_DURATION
import org.futo.voiceinput.settings.S1MiniContext
import org.futo.voiceinput.settings.S1MiniRuntime
import org.futo.voiceinput.settings.S1MiniStructure
import org.futo.voiceinput.settings.S1MiniStyling
import org.futo.voiceinput.settings.S1MiniWarmDuration
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.SettingItem
import org.futo.voiceinput.settings.SettingRadio
import org.futo.voiceinput.settings.SettingToggleDataStoreItem
import org.futo.voiceinput.settings.SettingToggleRaw
import org.futo.voiceinput.settings.Tip
import org.futo.voiceinput.settings.useDataStore
import java.text.DateFormat
import java.util.Date

@Composable
fun S1MiniOptions() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val enabled = useDataStore(S1_MINI_ENABLED)
    val transcriptDiagnostics = useDataStore(S1_MINI_TRANSCRIPT_DIAGNOSTICS)
    val refresh = remember { mutableStateOf(0) }
    val benchmarking = remember { mutableStateOf(false) }
    val showCaptureConsent = remember { mutableStateOf(false) }
    val showTranscriptExportConfirmation = remember { mutableStateOf(false) }
    val selectedCapture = remember { mutableStateOf<S1MiniTranscriptCapture?>(null) }
    refresh.value
    val installed = S1MiniModel.isInstalled(context)
    val transcriptCaptureResult = remember(refresh.value) {
        S1MiniDiagnostics.transcriptCaptures(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh.value += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(installed) {
        S1MiniDiagnostics.purgeTranscriptCaptures(context)
        refresh.value += 1
        if (installed && S1MiniBenchmark.needsRun(context)) {
            benchmarking.value = true
            S1MiniBenchmark.run(context)
            benchmarking.value = false
        }
    }

    ScreenTitle("Transcript cleanup")
    Tip(
        "S1-mini by Superwhisper cleans final English transcripts on-device after you press Stop. " +
            "English only • 484.2 MB download • experimental beta feature."
    )

    SettingItem(
        title = "S1-mini by Superwhisper",
        subtitle = if (installed) {
            "Q4_K_M • v1 • Installed and verified"
        } else {
            "Q4_K_M • v1 • Download required (484.2 MB)"
        },
        onClick = { if (!installed) S1MiniModel.startDownload(context) }
    ) {
        if (installed) {
            TextButton(onClick = {
                lifecycleOwner.lifecycleScope.launch {
                    enabled.setValue(false)
                    S1MiniModel.delete(context)
                    refresh.value += 1
                }
            }) { Text("Delete") }
        } else {
            TextButton(onClick = { S1MiniModel.startDownload(context) }) { Text("Download") }
        }
    }

    SettingToggleDataStoreItem(
        title = "Enable S1-mini cleanup",
        dataStoreItem = enabled,
        subtitle = "Runs once on the final transcript; non-English input is bypassed.",
        onChanged = { newValue ->
            if (newValue && !installed) {
                enabled.setValue(false)
                S1MiniModel.startDownload(context, enableAfterDownload = true)
            } else if (!newValue) {
                lifecycleOwner.lifecycleScope.launch { S1MiniClient.unload(context) }
            }
        }
    )

    SettingRadio(
        "Styling",
        S1MiniStyling.entries.map { it.id },
        S1MiniStyling.entries.map { it.label },
        S1_MINI_STYLING
    )
    SettingRadio(
        "Structure",
        S1MiniStructure.entries.map { it.id },
        S1MiniStructure.entries.map { it.label },
        S1_MINI_STRUCTURE
    )
    SettingRadio(
        "Context",
        S1MiniContext.entries.map { it.id },
        S1MiniContext.entries.map { it.label },
        S1_MINI_CONTEXT
    )
    SettingRadio(
        "Keep model weights warm",
        S1MiniWarmDuration.entries.map { it.id },
        S1MiniWarmDuration.entries.map { it.label },
        S1_MINI_WARM_DURATION,
        onChanged = { lifecycleOwner.lifecycleScope.launch { S1MiniClient.unload(context) } }
    )
    SettingRadio(
        "Inference runtime",
        S1MiniRuntime.entries.map { it.id },
        S1MiniRuntime.entries.map { it.label },
        S1_MINI_RUNTIME,
        onChanged = { lifecycleOwner.lifecycleScope.launch { S1MiniClient.unload(context) } }
    )

    SettingItem(
        title = if (benchmarking.value) "Optimizing S1-mini…" else "Rerun optimization test",
        subtitle = "Validates CPU and OpenCL output, then selects the fastest stable runtime.",
        disabled = !installed || benchmarking.value,
        onClick = {
            lifecycleOwner.lifecycleScope.launch {
                benchmarking.value = true
                val result = S1MiniBenchmark.run(context, force = true)
                benchmarking.value = false
                Toast.makeText(
                    context,
                    result?.let { "Selected ${it.backend} (${it.threads} threads, ${it.medianMs} ms)" }
                        ?: "No valid benchmark result; export standard diagnostics for details",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    ) { }

    ScreenTitle("Diagnostics")
    Tip(
        if (transcriptDiagnostics.value) {
            "Transcript capture is ON. Standard exports remain transcript-free; use the separately labeled transcript export only when you intend to share dictated text."
        } else {
            "Standard diagnostics contain performance data only—transcripts, audio, prompts, vocabulary, and identifiers are not included."
        }
    )
    Column {
        SettingToggleRaw(
            title = "Include transcripts in diagnostics",
            enabled = transcriptDiagnostics.value,
            setValue = { requested ->
                if (requested) showCaptureConsent.value = true
                else transcriptDiagnostics.setValue(false)
            },
            subtitle = "Captures eligible English S1-mini runs until you turn it off. Keeps the latest 10 for up to 7 days."
        )
        SettingItem("Copy last report", onClick = {
            val copied = S1MiniDiagnostics.copyLatest(context)
            Toast.makeText(context, if (copied) "Report copied" else "No report yet", Toast.LENGTH_SHORT).show()
        }) { }
        SettingItem("Export standard diagnostics ZIP", onClick = {
            if (!S1MiniDiagnostics.shareZip(context)) {
                Toast.makeText(context, "No diagnostics to export", Toast.LENGTH_SHORT).show()
            }
        }) { }
        SettingItem(
            title = "Export diagnostics WITH TRANSCRIPTS",
            subtitle = "${transcriptCaptureResult.captures.size} captured run(s)",
            disabled = transcriptCaptureResult.captures.isEmpty(),
            onClick = { showTranscriptExportConfirmation.value = true }
        ) { }
        transcriptCaptureResult.captures.forEach { capture ->
            SettingItem(
                title = DateFormat.getDateTimeInstance().format(Date(capture.capturedAtEpochMs)),
                subtitle = capture.failureOrBypassReason?.let { "Cleanup failed or bypassed: $it" }
                    ?: "Cleanup completed",
                onClick = { selectedCapture.value = capture }
            ) { }
        }
        if (transcriptCaptureResult.captures.isNotEmpty() || transcriptCaptureResult.unreadableCount > 0) {
            SettingItem("Clear captured transcripts", onClick = {
                S1MiniDiagnostics.clearTranscriptCaptures(context)
                refresh.value += 1
                Toast.makeText(context, "Captured transcripts cleared", Toast.LENGTH_SHORT).show()
            }) { }
        }
        SettingItem("Clear diagnostic history", onClick = {
            S1MiniDiagnostics.clear(context)
            Toast.makeText(context, "Diagnostics cleared", Toast.LENGTH_SHORT).show()
        }) { }
    }

    if (showCaptureConsent.value) {
        AlertDialog(
            onDismissRequest = { showCaptureConsent.value = false },
            title = { Text("Include dictated text?") },
            text = {
                Text(
                    "Future eligible English S1-mini runs may contain passwords, messages, names, or other sensitive information. " +
                        "Text stays in private app storage until it expires or you delete it. Audio, clipboard contents, surrounding app text, and app names are never captured."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    transcriptDiagnostics.setValue(true)
                    showCaptureConsent.value = false
                }) { Text("Enable capture") }
            },
            dismissButton = {
                TextButton(onClick = { showCaptureConsent.value = false }) { Text("Cancel") }
            }
        )
    }

    if (showTranscriptExportConfirmation.value) {
        AlertDialog(
            onDismissRequest = { showTranscriptExportConfirmation.value = false },
            title = { Text("Export dictated text?") },
            text = {
                Text(
                    "This readable ZIP will contain ${transcriptCaptureResult.captures.size} transcript run(s). " +
                        "Only share it with someone you trust."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showTranscriptExportConfirmation.value = false
                    if (!S1MiniDiagnostics.shareZip(context, includeTranscripts = true)) {
                        Toast.makeText(context, "No transcript diagnostics to export", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Export WITH TRANSCRIPTS") }
            },
            dismissButton = {
                TextButton(onClick = { showTranscriptExportConfirmation.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedCapture.value?.let { capture ->
        AlertDialog(
            onDismissRequest = { selectedCapture.value = null },
            title = { Text("Captured transcript") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TranscriptStage("Raw transcript", capture.raw.status, capture.raw.text)
                    TranscriptStage("Cleaned transcript", capture.cleaned.status, capture.cleaned.text)
                    TranscriptStage(
                        "Final delivered transcript",
                        capture.finalDelivered.status,
                        capture.finalDelivered.text
                    )
                    capture.failureOrBypassReason?.let { Text("Reason: $it") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    S1MiniDiagnostics.deleteTranscriptCapture(context, capture.reportId)
                    selectedCapture.value = null
                    refresh.value += 1
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { selectedCapture.value = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun TranscriptStage(label: String, status: String, text: String?) {
    Text("$label: ${if (status == "produced") text.orEmpty() else "Not produced"}")
}
