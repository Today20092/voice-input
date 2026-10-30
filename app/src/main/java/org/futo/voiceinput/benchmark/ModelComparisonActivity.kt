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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
        setContent { ComparisonPage() }
    }

    @Composable
    internal fun ComparisonPage() {
        UixThemeAuto {
            val colors = MaterialTheme.colorScheme
            // Match the settings' neutral detail actions, including themes with a low-contrast primary color.
            MaterialTheme(colorScheme = colors.copy(primary = colors.onSurface, onPrimary = colors.surfaceContainer)) {
                Surface(color = colors.background) { Comparison() }
            }
        }
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

    @OptIn(ExperimentalLayoutApi::class)
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
        Column(Modifier.fillMaxSize().imePadding()) {
            org.futo.voiceinput.settings.ScreenTitle("Model comparison", showBack = true,
                onBack = { if (model.busy) model.cancel() else finish() })
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (model.report == null) ComparisonText("Compare the same audio across models on this phone. Your daily model and settings stay unchanged.")
                model.error?.let { error ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Text(error, Modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                if (model.busy && model.report != null) ComparisonCard {
                    ComparisonHeading("Comparison in progress")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(model.progress, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    OutlinedButton(onClick = model::cancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel comparison") }
                    ComparisonText("Keep this screen open. Cancellation may wait for the current decode; completed measurements are kept.")
                }
                if (model.report == null) {
                    ComparisonCard {
                        ComparisonHeading("Audio")
                        ComparisonText("Add up to 8 recordings. Record a passage, then choose another and record again. Every selected model runs on all recordings.")
                        OutlinedButton(onClick = { showLanguages = true }, enabled = !model.busy && model.clips.isEmpty(),
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Language: ${org.futo.voiceinput.LANGUAGE_LIST.firstOrNull { it.id == model.language }?.name ?: model.language}")
                        }
                        OutlinedTextField(reference, { if (it.length <= 6000) reference = it },
                            label = { Text("Passage") }, supportingText = { Text("Optional") },
                            modifier = Modifier.fillMaxWidth(), readOnly = model.busy)
                        TextButton(onClick = { showPrompts = true }, enabled = !model.busy) { Text("Choose a passage") }
                        if (model.recording) Button(onClick = model::stopRecording, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) { Text("Stop recording") }
                        else FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2) {
                            Button(onClick = {
                                player?.release(); player = null
                                if (ContextCompat.checkSelfPermission(this@ModelComparisonActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) model.record(reference)
                                else permission.launch(Manifest.permission.RECORD_AUDIO)
                            }, enabled = !model.busy && model.clips.size < 8, modifier = Modifier.weight(1f).widthIn(min = 128.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) { Text(if (model.clips.isEmpty()) "Record" else "Record another") }
                            OutlinedButton(onClick = { importer.launch(arrayOf("audio/*")) },
                                enabled = !model.busy && model.clips.size < 8, modifier = Modifier.weight(1f).widthIn(min = 128.dp)) { Text("Import audio") }
                        }
                        if (model.busy) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(model.progress, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = model::cancel, modifier = Modifier.fillMaxWidth()) {
                                Text(if (model.recording) "Cancel recording" else "Cancel")
                            }
                        }
                        if (model.clips.isEmpty()) ComparisonText("No recordings yet. Add audio to start a comparison.")
                        else ComparisonText("${model.clips.size} of 8 recordings added. ${if (model.clips.size < 8) "Add another passage above, or compare all recordings below." else "Ready to compare all recordings below."} Listen back and check each recording’s words.")
                        ComparisonDetails("Recording tips and file limits") {
                            ComparisonText("The reference must match the words actually spoken, including fillers. Edit it after listening, or leave it blank to compare transcripts without an accuracy score.")
                            ComparisonText("Use one language per comparison. English-only models are skipped for other languages; automatic-detection models report that configuration.")
                            ComparisonText("Up to 8 recordings, 180 seconds and 64 MiB per file. Import AAC M4A or 16-bit PCM WAV, mono/stereo, 8–48 kHz. Audio stays in private comparison storage.")
                        }
                    }
                    model.clips.forEachIndexed { index, clip ->
                        key(clip.id) {
                            ComparisonCard {
                                ComparisonHeading("Recording ${index + 1}")
                                ComparisonText("Audio length: %.1f seconds".format(clip.durationSeconds))
                                OutlinedButton(onClick = { preview(model.draftId, clip) }, enabled = !model.busy) { Text("Listen") }
                                OutlinedTextField(clip.reference.orEmpty(), { model.reference(clip.id, it) },
                                    label = { Text("Words spoken") }, modifier = Modifier.fillMaxWidth(), enabled = !model.busy)
                                TextButton(onClick = { player?.release(); player = null; model.removeClip(clip.id) }, enabled = !model.busy,
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Remove recording") }
                            }
                        }
                    }
                    ComparisonCard {
                        val selectedCount = model.models.count { it.id in model.selected && it.supportsLanguage(model.language) }
                        ComparisonHeading("Models")
                        ComparisonText("$selectedCount selected for this recording language. Check the models and variants to compare.")
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2) {
                            TextButton(onClick = { model.selected = model.models.filter { it.supportsLanguage(model.language) }.map { it.id }.toSet() },
                                enabled = !model.busy, modifier = Modifier.weight(1f).widthIn(min = 128.dp)) { Text("Select all") }
                            TextButton(onClick = { model.selected = emptySet() },
                                enabled = !model.busy, modifier = Modifier.weight(1f).widthIn(min = 128.dp)) { Text("Select none") }
                            TextButton(onClick = { model.selected = model.models.filter { it.id in model.installed && it.supportsLanguage(model.language) }.map { it.id }.toSet() },
                                enabled = !model.busy, modifier = Modifier.weight(1f).widthIn(min = 128.dp)) { Text("Installed only") }
                        }
                        org.futo.voiceinput.settings.pages.modelFamiliesForDisplay().forEach { (family, cards) ->
                            val ids = cards.flatMap { it.models }.map { it.id }.toSet()
                            val candidates = model.models.filter { it.managed?.id in ids || (it.whisper != null && cards.any { card -> card.id == "whisper" }) }
                            ComparisonModelFamily(family, candidates, model.selected, model.installed, model.language, !model.busy) { id, checked ->
                                model.selected = if (checked) model.selected + id else model.selected - id
                            }
                        }
                        ComparisonText("Additional download: ${mib(model.downloadBytes)} MiB · Installed storage: ${mib(model.storageBytes)} MiB")
                        ComparisonText("Archives may need temporary extra space. Shared model files are counted once.")
                        Button(onClick = { confirmRun = true },
                            enabled = !model.busy && model.clips.isNotEmpty() && selectedCount > 0, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) {
                            Text("Compare selected models")
                        }
                        if (model.clips.isEmpty()) ComparisonText("Add a recording above to enable comparison.")
                        else if (selectedCount == 0) ComparisonText("Select at least one model that supports the recording language.")
                    }
                }
                model.report?.takeUnless { model.busy }?.let { report ->
                    var resultOrder by rememberSaveable(report.runId) { mutableStateOf(ResultOrder.SPEED) }
                    var startupSpeed by rememberSaveable(report.runId) { mutableStateOf(false) }
                    val summaries = comparisonSummaries(report, model.models)
                    ComparisonCard {
                        ComparisonHeading("Results")
                        ComparisonText("${report.status.replaceFirstChar { it.uppercase() }} · ${report.cases.count { it.status == "completed" }} of ${report.selectedModelIds.size} models completed")
                    }
                    ComparisonOverview(summaries, report.clips.size, startupSpeed) { startupSpeed = it }
                    ComparisonCard {
                        ComparisonHeading("Model details")
                        ComparisonText("Sort by · ${resultOrder.label}${if (resultOrder == ResultOrder.SPEED) if (startupSpeed) " (load + first run)" else " (already loaded)" else ""}")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ResultOrder.entries.forEach { order ->
                                FilterChip(resultOrder == order, { resultOrder = order }, label = { Text(order.label) })
                            }
                        }
                        ComparisonDetails("Recordings and references") {
                            report.clips.forEachIndexed { index, clip ->
                                ComparisonHeading("Recording ${index + 1} · %.1f s audio".format(clip.durationSeconds))
                                Text(clip.reference ?: "No reference provided", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    summaries.ordered(resultOrder, startupSpeed).forEach { summary ->
                        val result = summary.result
                        key(report.runId, result.modelId) {
                            ComparisonCard {
                                ComparisonHeading(summary.name)
                                ComparisonText(result.status.replaceFirstChar { it.uppercase() } + result.reason?.let { ": $it" }.orEmpty())
                                ComparisonMetricTable(listOf(
                                    (if (result.loadsEveryPass) "Backend setup" else "Model load and setup") to "${ms(result.initializationNs)} ms",
                                    "Model files" to (summary.modelBytes?.let { "${resultNumber(it / 1e6)} MB" } ?: "—"),
                                    "Peak app RAM" to (summary.ramMiB?.let { "${resultNumber(it)} MiB" } ?: "—")
                                ), "model-metrics-${result.modelId}")
                                if (result.loadsEveryPass) ComparisonText("Whistle reloads a worker every pass. Separate load/already-loaded timings and worker RAM are unavailable.")
                                report.clips.forEachIndexed { index, clip ->
                                    val observations = result.observations.toList().filter { it.clipId == clip.id }
                                    val warm = observations.filter { it.phase == "warm" }.map { it.elapsedNs }.sorted()
                                    ComparisonHeading("Recording ${index + 1} · %.1f s audio".format(clip.durationSeconds))
                                    val metrics = mutableListOf<Pair<String, String>>()
                                    if (warm.isNotEmpty()) metrics += (if (result.loadsEveryPass) "Median incl. worker load" else "Loaded median") to "${ms(warm.medianNs())} ms"
                                    observations.firstOrNull { it.phase == "first" }?.let { first ->
                                        metrics += (if (result.loadsEveryPass) "First incl. worker load" else "First decode") to "${ms(first.elapsedNs)} ms"
                                        metrics += "Load + first decode" to "${ms(result.initializationNs?.plus(first.elapsedNs))} ms"
                                    }
                                    observations.firstOrNull()?.let { observation ->
                                        metrics += "Word error rate" to (observation.accuracy.words?.let { "%.1f%%".format(it.rate * 100) } ?: "No score")
                                    }
                                    ComparisonMetricTable(metrics, "clip-metrics-${result.modelId}-${clip.id}")
                                    observations.firstOrNull()?.let { observation ->
                                        if (observation.accuracy.words == null) ComparisonText(observation.accuracy.unavailable ?: "No word score available")
                                        androidx.compose.foundation.text.selection.SelectionContainer {
                                            Text("Recognized: ${observation.transcript}", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                                ComparisonDetails("Every transcript and timing") {
                                    result.identity["memoryNotice"]?.let { ComparisonText(it) }
                                    report.clips.forEachIndexed { index, clip ->
                                        val observations = result.observations.toList().filter { it.clipId == clip.id }
                                        ComparisonHeading("Recording ${index + 1} · ${observations.count { it.phase == "warm" }}/${report.warmRepetitions} repeated passes")
                                        ComparisonMetricTable(observations.filter { it.phase == "warm" }.map {
                                            "Pass ${it.repetition}" to "${ms(it.elapsedNs)} ms"
                                        }, "passes-${result.modelId}-${clip.id}")
                                        val representative = observations.firstOrNull()?.transcript
                                        observations.filter { it.phase == "warm" && it.transcript != representative }.forEach { observation ->
                                            Text("Pass ${observation.repetition}: ${observation.transcript}", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                                model.models.firstOrNull { it.id == result.modelId && result.status == "completed" }?.let { candidate ->
                                    OutlinedButton(onClick = { model.select(candidate) }) { Text("Use as daily model") }
                                }
                            }
                        }
                    }
                    ComparisonCard {
                        ComparisonHeading("Save or manage results")
                        ComparisonText("Your recordings are saved with these results. Reuse them to compare other models without recording again.")
                        Button(onClick = { model.reuseRecordings() }, enabled = report.clips.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) { Text("Compare these recordings again") }
                        OutlinedButton(onClick = model::startNew, modifier = Modifier.fillMaxWidth()) { Text("New comparison with new audio") }
                        ComparisonText(report.contentNotice)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { exportRunId = report.runId; exportMarkdown = false; exporter.launch("benchmark-${report.runId}-WITH-TRANSCRIPTS.json") },
                                modifier = Modifier.fillMaxWidth()) { Text("Export JSON") }
                            OutlinedButton(onClick = { exportRunId = report.runId; exportMarkdown = true; exporter.launch("benchmark-${report.runId}-WITH-TRANSCRIPTS.md") },
                                modifier = Modifier.fillMaxWidth()) { Text("Export Markdown") }
                        }
                        ComparisonDetails("How to read these results") {
                            ComparisonText("Lower word error rate means fewer substitutions, missing words and extra words. It can exceed 100%. Punctuation and capitalization are not scored; number formatting stays literal.")
                            ComparisonText("Model load and setup measures backend creation and loading, including validation. First transcription is the first decode after that load. Load + first transcription adds both measurements and excludes downloads, audio conversion and report saving.")
                            ComparisonText("Already-loaded transcription is the median of the repeated timed passes after one untimed warm-up, excluding loading. Whistle starts and loads a worker every pass, so its repeated timings include that work. Filesystem caches may already be warm; these are not cold-device measurements.")
                            ComparisonText("Results describe these recordings and this phone, not a general model ranking.")
                            ComparisonText(report.cacheNotice)
                        }
                        TextButton(onClick = {
                            cleanupIds = report.selectedModelIds.filter { it !in report.installedBefore && it in model.installed }.toSet()
                            showCleanup = true
                        }) { Text("Optional model removal") }
                        TextButton(onClick = { confirmDelete = report },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete this benchmark and its audio") }
                    }
                }
                if (!model.busy && model.report == null) {
                    ComparisonCard {
                        ComparisonHeading("Saved comparisons")
                        if (model.saved.isEmpty()) ComparisonText("Completed comparisons and saved recordings appear here.")
                        model.saved.forEach { saved ->
                            TextButton(onClick = { model.show(saved) }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(saved.createdAtMs)),
                                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                    ComparisonText("${saved.status.replaceFirstChar { it.uppercase() }} · ${saved.clips.size} recordings · ${saved.selectedModelIds.size} models")
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showPrompts) AlertDialog(onDismissRequest = { showPrompts = false }, title = { Text("Choose a passage") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) { repeat(prompts.length()) { index ->
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
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Every selected model will transcribe all ${model.clips.size} recordings.")
                Text("Check that each reference matches the words spoken. Leave it blank to compare transcripts without an accuracy score.")
                Text("Download ${mib(model.downloadBytes)} MiB and retain ${mib(model.storageBytes)} MiB of additional models. Your daily model stays selected.")
                ComparisonText("Each model runs sequentially: a first pass, an untimed warm-up and five timed passes. Large models can take several minutes. Failed or missing models are listed in the results.")
            } },
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
