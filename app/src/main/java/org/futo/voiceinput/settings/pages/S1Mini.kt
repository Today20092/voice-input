package org.futo.voiceinput.settings.pages

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.futo.voiceinput.settings.S1_MINI_CONTEXT
import org.futo.voiceinput.settings.S1_MINI_ENABLED
import org.futo.voiceinput.settings.S1_MINI_RUNTIME
import org.futo.voiceinput.settings.S1_MINI_STRUCTURE
import org.futo.voiceinput.settings.S1_MINI_STYLING
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
import org.futo.voiceinput.settings.Tip
import org.futo.voiceinput.settings.useDataStore

@Composable
fun S1MiniOptions() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val enabled = useDataStore(S1_MINI_ENABLED)
    val refresh = remember { mutableStateOf(0) }
    val benchmarking = remember { mutableStateOf(false) }
    refresh.value
    val installed = S1MiniModel.isInstalled(context)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh.value += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(installed) {
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
                        ?: "No valid benchmark result",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    ) { }

    ScreenTitle("Diagnostics")
    Tip("Performance data only—transcripts, audio, prompts, vocabulary, and identifiers are never included.")
    Column {
        SettingItem("Copy last report", onClick = {
            val copied = S1MiniDiagnostics.copyLatest(context)
            Toast.makeText(context, if (copied) "Report copied" else "No report yet", Toast.LENGTH_SHORT).show()
        }) { }
        SettingItem("Export diagnostics ZIP", onClick = {
            if (!S1MiniDiagnostics.shareZip(context)) {
                Toast.makeText(context, "No diagnostics to export", Toast.LENGTH_SHORT).show()
            }
        }) { }
        SettingItem("Clear diagnostic history", onClick = {
            S1MiniDiagnostics.clear(context)
            Toast.makeText(context, "Diagnostics cleared", Toast.LENGTH_SHORT).show()
        }) { }
    }
}
