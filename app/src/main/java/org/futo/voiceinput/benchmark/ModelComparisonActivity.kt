package org.futo.voiceinput.benchmark

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.voiceinput.theme.UixThemeAuto
import org.json.JSONArray
import java.io.File

class ModelComparisonActivity : ComponentActivity() {
    private val model: BenchmarkViewModel by viewModels()
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UixThemeAuto { Surface { Comparison() } } }
    }

    override fun onStop() {
        player?.release(); player = null
        if (model.recording) model.stopRecording()
        super.onStop()
    }
    override fun onResume() { super.onResume(); model.refreshReports() }

    override fun onDestroy() {
        player?.release(); player = null
        if (isFinishing) model.cancel()
        super.onDestroy()
    }

    private fun preview(reportId: String, clip: BenchmarkClip) {
        player?.release()
        try {
            player = MediaPlayer().apply {
                setDataSource(File(model.benchmark.store.directory(reportId), "${clip.id}.original").absolutePath)
                setOnCompletionListener { it.release(); player = null }
                prepare(); start()
            }
        } catch (_: Exception) { player?.release(); player = null; model.error = "Cannot play this recording. Try importing it again." }
    }

    @Composable
    private fun Comparison() {
        val scope = rememberCoroutineScope()
        var reference by rememberSaveable { mutableStateOf("Please remind me to pick up milk, eggs, and coffee after work tomorrow.") }
        val prompts = remember { JSONArray(assets.open("benchmark-prompts.json").bufferedReader().use { it.readText() }) }
        var showPrompts by remember { mutableStateOf(false) }
        var showLanguages by remember { mutableStateOf(false) }
        var confirmRun by remember { mutableStateOf(false) }
        var confirmDelete by remember { mutableStateOf<BenchmarkReport?>(null) }
        var showCleanup by remember { mutableStateOf(false) }
        var cleanupIds by remember { mutableStateOf(setOf<String>()) }
        var exportRunId by rememberSaveable { mutableStateOf<String?>(null) }
        var exportMarkdown by rememberSaveable { mutableStateOf(false) }
        val downloader = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            model.completeDownload(it.resultCode == RESULT_OK)
        }
        DisposableEffect(downloader) {
            model.download = { selected ->
                model.awaitDownload(selected) { downloader.launch(it.downloadIntent(this@ModelComparisonActivity)) }
            }
            onDispose { model.download = null }
        }
        DisposableEffect(model.busy) {
            if (model.busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) model.record(reference) else model.error = "Microphone permission is required to record. You can import audio instead."
        }
        val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let { uri -> model.import(uri, reference) }
        }
        val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) scope.launch {
                try { withContext(Dispatchers.IO) {
                    val report = model.benchmark.store.load(requireNotNull(exportRunId))
                    val text = if (exportMarkdown) model.benchmark.store.markdown(report) else model.benchmark.store.json(report)
                    contentResolver.openOutputStream(uri).use { output -> requireNotNull(output).write(text.toByteArray()) }
                } } catch (_: Exception) { model.error = "Cannot save report. Choose another location." }
            }
        }
        BackHandler(model.busy) { model.cancel() }
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Model comparison", style = MaterialTheme.typography.headlineMedium)
            Text("Record once and compare the same audio on this phone. Results describe your voice and device. Your daily settings stay unchanged.")
            if (model.report == null) {
                Text("Use a supplied passage or write your own. The reference must match the words you actually speak, including fillers. Edit it after listening if you deviate.")
                OutlinedButton(onClick = { showPrompts = true }, enabled = !model.busy) { Text("Choose passage") }
                OutlinedButton(onClick = { showLanguages = true }, enabled = !model.busy && model.clips.isEmpty()) {
                    Text("Recording language: ${org.futo.voiceinput.LANGUAGE_LIST.firstOrNull { it.id == model.language }?.name ?: model.language}")
                }
                Text("One language per comparison. English-only models are skipped for other languages. Models with automatic detection report that configuration.")
                OutlinedTextField(reference, { if (it.length <= 6000) reference = it }, label = { Text("Prompt / spoken reference (optional)") }, modifier = Modifier.fillMaxWidth(), readOnly = model.busy)
                Text("Limit: 180 seconds, 64 MiB per file. Import AAC M4A or 16-bit PCM WAV, mono/stereo, 8 to 48 kHz. Audio stays in private benchmark storage.")
                if (model.recording) Button(onClick = model::stopRecording) { Text("Stop recording") }
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        player?.release(); player = null
                        if (ContextCompat.checkSelfPermission(this@ModelComparisonActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) model.record(reference)
                        else permission.launch(Manifest.permission.RECORD_AUDIO)
                    }, enabled = !model.busy) { Text("Record") }
                    OutlinedButton(onClick = { importer.launch(arrayOf("audio/*")) }, enabled = !model.busy) { Text("Import audio") }
                }
                model.clips.forEachIndexed { index, clip ->
                    Text("Recording ${index + 1}: %.1f seconds".format(clip.durationSeconds))
                    OutlinedButton(onClick = { preview(model.draftId, clip) }, enabled = !model.busy) { Text("Listen") }
                    OutlinedTextField(clip.reference.orEmpty(), { model.reference(clip.id, it) }, label = { Text("Words actually spoken") }, modifier = Modifier.fillMaxWidth(), enabled = !model.busy)
                    TextButton(onClick = { player?.release(); player = null; model.removeClip(clip.id) }, enabled = !model.busy) { Text("Remove recording") }
                }
                Text("Models and variants", style = MaterialTheme.typography.titleLarge)
                Row {
                    TextButton(onClick = { model.selected = model.models.map { it.id }.toSet() }, enabled = !model.busy) { Text("Select all") }
                    TextButton(onClick = { model.selected = model.installed }, enabled = !model.busy) { Text("Installed only") }
                }
                model.models.forEach { candidate ->
                    val supported = candidate.supportsLanguage(model.language)
                    Row(Modifier.fillMaxWidth().toggleable(value = candidate.id in model.selected, enabled = !model.busy && supported,
                        role = Role.Checkbox, onValueChange = { checked -> model.selected = if (checked) model.selected + candidate.id else model.selected - candidate.id })) {
                        Checkbox(candidate.id in model.selected, onCheckedChange = null, enabled = !model.busy && supported)
                        Column(Modifier.weight(1f)) {
                            Text(candidate.name)
                            Text("${candidate.languages}; ${if (candidate.id in model.installed) "Installed" else "Download required"}; ${mib(candidate.storageBytes)} MiB", style = MaterialTheme.typography.bodySmall)
                            if (!supported) Text("Skipped for this recording language", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text("Additional download: ${mib(model.downloadBytes)} MiB. Additional installed storage: ${mib(model.storageBytes)} MiB. Archives may need temporary extra space. Shared directories are counted once.")
                Button(onClick = { confirmRun = true }, enabled = !model.busy && model.clips.isNotEmpty() && model.selected.isNotEmpty()) { Text("Compare selected models") }
            }
            if (model.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(model.progress)
                OutlinedButton(onClick = model::cancel) { Text("Cancel comparison") }
                Text("Keep this screen open. Native decoding may finish its current call before cancellation takes effect. Completed measurements are kept.")
            }
            model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            model.report?.takeUnless { model.busy }?.let { report ->
                Text("Results: ${report.status}", style = MaterialTheme.typography.titleLarge)
                report.cases.toList().forEach { result ->
                    var showObservations by remember(report.runId, result.modelId) { mutableStateOf(false) }
                    Text(model.models.firstOrNull { it.id == result.modelId }?.name ?: result.modelId, style = MaterialTheme.typography.titleMedium)
                    Text("${result.status}. ${result.reason.orEmpty()}")
                    Text("Model loading: ${ms(result.initializationNs)} ms.")
                    report.clips.forEachIndexed { index, clip ->
                        Text("Recording ${index + 1}: %.1f seconds".format(clip.durationSeconds))
                        val observations = result.observations.toList().filter { it.clipId == clip.id }
                        val warm = observations.filter { it.phase == "warm" }.map { it.elapsedNs }.sorted()
                        if (warm.isNotEmpty()) Text("Typical transcription: ${ms(warm.medianNs())} ms once loaded. Range: ${ms(warm.first())}–${ms(warm.last())} ms across ${warm.size}/5 timed passes.")
                        observations.firstOrNull()?.let { observation ->
                            Text("${if (observation.phase == "first") "First decode" else "Warm decode ${observation.repetition}"}: ${ms(observation.elapsedNs)} ms.")
                            if (observation.phase == "first") Text("Startup inclusive: ${ms(result.initializationNs?.plus(observation.elapsedNs))} ms.")
                            Text("Word error rate: ${observation.accuracy.words?.let { "%.1f%%".format(it.rate * 100) } ?: "unavailable: ${observation.accuracy.unavailable}"}")
                            Text("Character error rate: ${observation.accuracy.characters?.let { "%.1f%%".format(it.rate * 100) } ?: "unavailable"}")
                            Text("Reference: ${clip.reference ?: "Unavailable"}")
                            Text("Recognized: ${observation.transcript}")
                        }
                    }
                    TextButton(onClick = { showObservations = !showObservations }) { Text(if (showObservations) "Hide individual measurements" else "Show every transcript and timing") }
                    if (showObservations) result.observations.toList().forEach { observation ->
                        Text("${observation.clipId} ${observation.phase} ${observation.repetition}: ${ms(observation.elapsedNs)} ms; WER ${observation.accuracy.words?.let { "%.1f%%".format(it.rate * 100) } ?: "unavailable"}")
                        Text(observation.transcript)
                    }
                    model.models.firstOrNull { it.id == result.modelId && result.status == "completed" }?.let { candidate ->
                        OutlinedButton(onClick = { model.select(candidate) }, enabled = !model.busy) { Text("Use as daily model") }
                    }
                    HorizontalDivider()
                }
                Text("Lower word error rate means fewer word substitutions, missing words and extra words. It can exceed 100%. Punctuation and capitalization are not scored; number formatting stays literal. Export includes every transcript and timing. Warm timings exclude one warm-up.")
                Text(report.contentNotice)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportRunId = report.runId; exportMarkdown = false; exporter.launch("benchmark-${report.runId}-WITH-TRANSCRIPTS.json") }, enabled = !model.busy) { Text("Export JSON") }
                    OutlinedButton(onClick = { exportRunId = report.runId; exportMarkdown = true; exporter.launch("benchmark-${report.runId}-WITH-TRANSCRIPTS.md") }, enabled = !model.busy) { Text("Export Markdown") }
                }
                OutlinedButton(onClick = {
                    cleanupIds = report.selectedModelIds.filter { it !in report.installedBefore && it in model.installed }.toSet()
                    showCleanup = true
                }, enabled = !model.busy) { Text("Optional model removal") }
                OutlinedButton(onClick = { confirmDelete = report }, enabled = !model.busy) { Text("Delete this benchmark and its audio") }
                Button(onClick = model::startNew, enabled = !model.busy) { Text("New comparison") }
            }
            if (!model.busy && model.report == null) {
                Text("Saved comparisons", style = MaterialTheme.typography.titleMedium)
                model.saved.forEach { saved ->
                    TextButton(onClick = { model.show(saved) }) { Text("${saved.runId.take(8)}: ${saved.status}, ${saved.clips.size} clips") }
                }
            }
            TextButton(onClick = { finish() }, enabled = !model.busy) { Text("Back to settings") }
        }
        if (showPrompts) AlertDialog(onDismissRequest = { showPrompts = false }, title = { Text("Choose a passage") }, text = {
            Column { repeat(prompts.length()) { index ->
                val prompt = prompts.getJSONObject(index)
                TextButton(onClick = { reference = prompt.getString("text"); showPrompts = false }) { Text(prompt.getString("title")) }
            } }
        }, confirmButton = { TextButton(onClick = { showPrompts = false }) { Text("Close") } })
        if (showLanguages) AlertDialog(onDismissRequest = { showLanguages = false }, title = { Text("Recording language") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                org.futo.voiceinput.LANGUAGE_LIST.forEach { language ->
                    TextButton(onClick = { model.language = language.id; showLanguages = false }) { Text(language.name) }
                }
            }
        }, confirmButton = { TextButton(onClick = { showLanguages = false }) { Text("Close") } })
        if (confirmRun) AlertDialog(onDismissRequest = { confirmRun = false }, title = { Text("Start comparison?") },
            text = { Text("Confirm the reference matches your recording, or leave it blank to compare outputs without an accuracy score. Download ${mib(model.downloadBytes)} MiB, retain ${mib(model.storageBytes)} MiB of additional models, then run them sequentially. Each model makes a first pass, an untimed warm-up and five timed passes on the same audio. Large models can take several minutes. Missing or failed models get a skipped or failed row. Your daily model stays selected.") },
            confirmButton = { TextButton(onClick = { confirmRun = false; player?.release(); player = null; model.run() }) { Text("Download and compare") } },
            dismissButton = { TextButton(onClick = { confirmRun = false }) { Text("Cancel") } })
        confirmDelete?.let { report -> AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("Delete benchmark?") },
            text = { Text("Delete this benchmark's recordings, references and reports. Recognition models have separate removal controls.") },
            confirmButton = { TextButton(onClick = { confirmDelete = null; model.deleteReport(report) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }) }
        if (showCleanup) AlertDialog(onDismissRequest = { showCleanup = false }, title = { Text("Remove selected model downloads") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("New downloads are checked by default. Your daily model and bundled assets are protected.")
                model.models.filter { it.id in model.report!!.selectedModelIds && it.id in model.installed && it.whisper?.ggml?.is_builtin_asset != true && !(BuildConfigBundled(it)) }.forEach { candidate ->
                    Row { Checkbox(candidate.id in cleanupIds, { checked -> cleanupIds = if (checked) cleanupIds + candidate.id else cleanupIds - candidate.id }); Text(candidate.name) }
                }
                Text("Up to ${mib(model.models.filter { it.id in cleanupIds }.distinctBy { it.managed?.directoryName ?: it.id }.sumOf { it.storageBytes })} MiB recoverable. Protected files are retained.")
            }
        }, confirmButton = { TextButton(onClick = { showCleanup = false; model.cleanup(cleanupIds) }) { Text("Remove checked models") } },
            dismissButton = { TextButton(onClick = { showCleanup = false }) { Text("Keep models") } })
    }

    private fun BuildConfigBundled(model: BenchmarkModel) = org.futo.voiceinput.BuildConfig.BUNDLE_PARAKEET_MODEL && model.managed?.runtimeId == "parakeet"
    private fun mib(bytes: Long) = "%.1f".format(bytes / 1048576.0)
    private fun ms(ns: Long?) = ns?.let { "%.1f".format(it / 1e6) } ?: "unavailable"
}
