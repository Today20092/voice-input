package org.futo.voiceinput.benchmark

import android.annotation.SuppressLint
import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.recognition.RecognitionModelLifecycle
import org.futo.voiceinput.settings.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class BenchmarkViewModel(application: Application) : AndroidViewModel(application) {
    val benchmark = DeviceBenchmark(application)
    val models = BenchmarkModel.all()
    var selected by mutableStateOf(setOf<String>())
    var installed by mutableStateOf(setOf<String>())
    var installedBefore by mutableStateOf(setOf<String>())
    var clips by mutableStateOf(listOf<BenchmarkClip>())
    var language by mutableStateOf("en")
    var draftId = UUID.randomUUID().toString()
    var report by mutableStateOf<BenchmarkReport?>(null)
    var saved by mutableStateOf(listOf<BenchmarkReport>())
    var busy by mutableStateOf(false)
    var recording by mutableStateOf(false)
    var progress by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    private var downloadResult: CompletableDeferred<Boolean>? = null
    private val draftWrites = Mutex()
    private var job: Job? = null
    var download: (suspend (BenchmarkModel) -> Boolean)? = null

    suspend fun awaitDownload(model: BenchmarkModel, launch: (BenchmarkModel) -> Unit): Boolean {
        val result = CompletableDeferred<Boolean>()
        downloadResult = result
        return try { launch(model); result.await() } finally { downloadResult = null }
    }

    fun completeDownload(completed: Boolean) { downloadResult?.complete(completed) }

    init {
        work {
            withContext(Dispatchers.IO) { refreshInstalled() }
            installedBefore = installed
            selected = installed
            saved = withContext(Dispatchers.IO) { benchmark.recoverOnce() }
        }
    }

    fun refreshInstalled() { installed = models.filter { it.installed(getApplication()) }.map { it.id }.toSet() }
    val missing get() = models.filter { it.id in selected && it.id !in installed && it.supportsLanguage(language) }
    val downloadBytes get() = missing.distinctBy { it.managed?.directoryName ?: it.id }.sumOf { it.transferBytes }
    val storageBytes get() = missing.distinctBy { it.managed?.directoryName ?: it.id }.sumOf { it.storageBytes }

    private fun work(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        job = viewModelScope.launch {
            try { action() } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: OutOfMemoryError) { error = "Not enough memory. Try a shorter recording or a smaller model."
            } catch (failure: Exception) { error = when (failure) {
                is IllegalArgumentException, is IllegalStateException -> failure.message?.takeIf { it.length < 200 } ?: "Cannot complete comparison"
                else -> "Cannot complete comparison (${failure.javaClass.simpleName}). Try again."
            } } finally {
                try { saved = withContext(Dispatchers.IO + NonCancellable) { benchmark.store.reports() } }
                finally { busy = false; recording = false; progress = "" }
            }
        }
    }

    fun import(uri: Uri, reference: String) = work {
        require(clips.size < 8) { "Use at most eight recordings per comparison" }
        progress = "Converting audio once for every model"
        val id = "clip-${UUID.randomUUID()}"
        withContext(Dispatchers.IO) {
            val original = File(benchmark.store.directory(draftId), "$id.original")
            try {
                var conversion = ""
                val samples = BenchmarkAudio.import(getApplication(), uri, original) { conversion = it }
                addClip(id, samples, original, reference, conversion)
            } catch (failure: Throwable) { original.delete(); throw failure }
        }
    }

    private suspend fun addClip(id: String, samples: FloatArray, original: File, reference: String, conversion: String) {
        require(clips.size < 8) { "Use at most eight recordings per comparison" }
        require(reference.length <= 6000) { "Reference exceeds 6000 characters" }
        val canonicalHash = benchmark.store.saveAudio(draftId, id, samples)
        val previous = clips
        clips = clips + BenchmarkClip(id, BenchmarkStore.hashFile(original), canonicalHash, samples.size,
            reference, BenchmarkStore.hash(reference.toByteArray()), language = language, conversion = conversion)
        try { saveDraft() } catch (failure: Throwable) {
            clips = previous
            benchmark.store.audioFile(draftId, id).delete()
            throw failure
        }
    }

    fun reference(id: String, text: String) {
        if (busy || text.length > 6000) return
        clips = clips.map { if (it.id == id) it.copy(reference = text, referenceSha256 = BenchmarkStore.hash(text.toByteArray())) else it }
        viewModelScope.launch(Dispatchers.IO) {
            try { saveDraft() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Cannot save the reference. Check available storage and try again." }
        }
    }

    private suspend fun saveDraft() {
        val draft = BenchmarkReport(runId = draftId, device = benchmark.device(),
            clips = clips, selectedModelIds = selected.toList(), installedBefore = installedBefore.toList())
        draftWrites.withLock {
            // A queued reference edit must never overwrite a started run or a newer draft.
            if (report == null && draftId == draft.runId && clips == draft.clips) benchmark.store.save(draft)
        }
    }

    fun removeClip(id: String) = work {
        clips = clips.filterNot { it.id == id }
        withContext(Dispatchers.IO) {
            saveDraft()
            check(!benchmark.store.audioFile(draftId, id).exists() || benchmark.store.audioFile(draftId, id).delete())
            val original = File(benchmark.store.directory(draftId), "$id.original")
            check(!original.exists() || original.delete())
        }
    }

    @SuppressLint("MissingPermission")
    fun record(reference: String) = work {
        require(clips.size < 8) { "Use at most eight recordings per comparison" }
        val ownership = RecognitionModelLifecycle.create(getApplication<Application>().filesDir, BuildConfig.BUNDLE_PARAKEET_MODEL).acquireBenchmark()
        try {
            recording = true; progress = "Recording. Stop after reading the passage. Limit: 180 seconds."
            withContext(Dispatchers.IO) {
                val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                require(minimum > 0) { "Microphone cannot record 16 kHz audio" }
                val capture = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 8192))
                try {
                    check(capture.state == AudioRecord.STATE_INITIALIZED) { "Microphone could not start" }
                    capture.startRecording()
                    check(capture.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone could not start" }
                    val pcm = ByteArrayOutputStream()
                    val shorts = ShortArray(2048)
                    var total = 0
                    while (recording && total < 16000 * BENCHMARK_MAX_SECONDS) {
                        currentCoroutineContext().ensureActive()
                        val read = capture.read(shorts, 0, minOf(shorts.size, 16000 * BENCHMARK_MAX_SECONDS - total))
                        check(read > 0) { "Microphone stopped. Re-record the passage." }
                        val bytes = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                        repeat(read) { bytes.putShort(shorts[it]) }
                        pcm.write(bytes.array()); total += read
                    }
                    require(total > 0) { "Recording is empty" }
                    val data = pcm.toByteArray()
                    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                    header.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVEfmt ".toByteArray())
                        .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
                        .put("data".toByteArray()).putInt(data.size)
                    val id = "clip-${UUID.randomUUID()}"
                    val original = File(benchmark.store.directory(draftId), "$id.original")
                    try {
                        original.outputStream().use { it.write(header.array()); it.write(data) }
                        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        addClip(id, FloatArray(total) { buffer.short / 32768f }, original, reference,
                            "Microphone 16 kHz mono PCM16; divide by 32768 to float32 LE")
                    } catch (failure: Throwable) { original.delete(); throw failure }
                } finally {
                    try { if (capture.recordingState == AudioRecord.RECORDSTATE_RECORDING) capture.stop() }
                    finally { capture.release() }
                }
            }
        } finally { ownership.close() }
    }

    fun stopRecording() { recording = false }
    fun cancel() { recording = false; job?.cancel() }

    fun run() = work {
        check(clips.isNotEmpty() && selected.isNotEmpty()) { "Add a recording and choose at least one model" }
        val snapshot = selected.toList()
        val newReport = BenchmarkReport(runId = draftId, device = benchmark.device(), clips = clips,
            selectedModelIds = snapshot, installedBefore = installedBefore.toList())
        report = newReport
        newReport.status = "downloading"
        try {
            withContext(Dispatchers.IO) { draftWrites.withLock { benchmark.store.save(newReport) } }
            for (model in models.filter { it.id in snapshot && it.id !in installed }) {
                currentCoroutineContext().ensureActive()
                if (!model.supportsLanguage(language)) continue
                if (model.id in installed) continue
                progress = "Download ${model.name}"
                val completed = try { download?.invoke(model) == true } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { false }
                withContext(Dispatchers.IO) { refreshInstalled() }
                if (!completed && model.id !in installed) {
                    newReport.cases.add(BenchmarkCaseResult(model.id, status = "skipped", reason = "Download failed or cancelled"))
                    withContext(Dispatchers.IO) { benchmark.store.save(newReport) }
                }
            }
            benchmark.run(newReport) { name -> viewModelScope.launch { progress = "Comparing $name" } }
        } catch (cancelled: CancellationException) {
            newReport.status = "cancelled"
            throw cancelled
        } catch (failure: Exception) {
            newReport.status = "interrupted"
            throw failure
        } catch (failure: OutOfMemoryError) {
            newReport.status = "interrupted"
            throw failure
        } finally { withContext(Dispatchers.IO + NonCancellable) { refreshInstalled(); benchmark.store.save(newReport) } }
    }

    fun startNew() {
        if (busy) return
        draftId = UUID.randomUUID().toString(); clips = emptyList(); report = null
        installedBefore = installed
    }

    fun reuseRecordings() = work {
        val source = requireNotNull(report)
        require(source.clips.isNotEmpty()) { "This comparison has no recordings to reuse" }
        val newId = UUID.randomUUID().toString()
        withContext(Dispatchers.IO) {
            try {
                for (clip in source.clips) {
                    currentCoroutineContext().ensureActive()
                    val original = File(benchmark.store.directory(source.runId), "${clip.id}.original")
                    check(BenchmarkStore.hashFile(original) == clip.originalSha256) { "Original audio changed; import it again" }
                    benchmark.store.saveAudio(newId, clip.id, benchmark.store.readAudio(source.runId, clip))
                    original.copyTo(File(benchmark.store.directory(newId), "${clip.id}.original"))
                }
                val draft = BenchmarkReport(runId = newId, device = benchmark.device(), clips = source.clips,
                    selectedModelIds = source.selectedModelIds, installedBefore = installed.toList())
                draftWrites.withLock { benchmark.store.save(draft) }
            } catch (failure: Throwable) {
                benchmark.store.delete(newId)
                throw failure
            }
        }
        draftId = newId; clips = source.clips; language = source.clips.first().language
        selected = source.selectedModelIds.toSet(); installedBefore = installed; report = null
    }

    fun show(report: BenchmarkReport) {
        if (busy) return
        if (report.status == "prepared" && report.cases.isEmpty()) {
            draftId = report.runId; clips = report.clips; selected = report.selectedModelIds.toSet()
            language = report.clips.firstOrNull()?.language ?: "en"
            installedBefore = report.installedBefore.toSet(); this.report = null
        } else this.report = report
    }
    fun refreshReports() {
        if (busy) return
        viewModelScope.launch {
            saved = withContext(Dispatchers.IO) { benchmark.store.reports() }
            report?.let { current -> report = saved.firstOrNull { it.runId == current.runId } }
        }
    }
    fun deleteReport(target: BenchmarkReport) = work {
        withContext(Dispatchers.IO) { benchmark.store.delete(target.runId) }
        if (report?.runId == target.runId) report = null
        if (draftId == target.runId) { draftId = UUID.randomUUID().toString(); clips = emptyList() }
    }

    fun select(model: BenchmarkModel) = work { withContext(Dispatchers.IO) { model.select(getApplication()) } }

    fun cleanup(ids: Set<String>) = work {
        val app = getApplication<Application>()
        val lifecycle = RecognitionModelLifecycle.create(app.filesDir, BuildConfig.BUNDLE_PARAKEET_MODEL)
        for (model in models.filter { it.id in ids }) {
            withContext(Dispatchers.IO) {
                if (model.managed != null) {
                    val selection = RecognitionModelSelectionForSettings(app)
                    lifecycle.delete(model.managed, lifecycle.readiness(selection)?.model?.id)
                } else {
                    require(app.getSetting(SPEECH_BACKEND) != "whisper_ggml") { "Switch away from Whisper before removing its files" }
                    lifecycle.acquireBenchmark().use {
                        val file = File(app.filesDir, requireNotNull(model.whisper).ggml.ggml_file)
                        require(!model.whisper.ggml.is_builtin_asset) { "Bundled models cannot be removed" }
                        check(!file.exists() || file.delete()) { "Cannot remove model" }
                    }
                }
            }
        }
        withContext(Dispatchers.IO) { refreshInstalled() }
    }
}

private suspend fun RecognitionModelSelectionForSettings(context: android.content.Context) = org.futo.voiceinput.recognition.RecognitionModelSelection(
    context.getSetting(SPEECH_BACKEND), context.getSetting(MOONSHINE_MODEL_VARIANT),
    context.getSetting(NEMOTRON_PROFILE), context.getSetting(ASR4ALL_VARIANT)
)
