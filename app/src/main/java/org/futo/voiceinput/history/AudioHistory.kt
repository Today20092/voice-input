package org.futo.voiceinput.history

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.TranscriptFinalizer
import org.futo.voiceinput.diagnostics.AppDiagnostics
import org.futo.voiceinput.diagnostics.DiagnosticEvent
import org.futo.voiceinput.diagnostics.DiagnosticMetric
import org.futo.voiceinput.diagnostics.DiagnosticConfiguration
import org.futo.voiceinput.modelNeedsDownloading
import org.futo.voiceinput.selectedWhisperModelsForCurrentSettings
import org.futo.voiceinput.recognition.RecognitionModelLifecycle
import org.futo.voiceinput.recognition.RecognitionModelSelection
import org.futo.voiceinput.recognition.RecognitionRuntimeCallbacks
import org.futo.voiceinput.settings.*
import java.io.File

fun Context.audioHistory() = AudioHistoryStore(File(noBackupFilesDir, "audio-history"))

suspend fun Context.purgeAudioHistory() = withContext(Dispatchers.IO) {
    audioHistory().purge(getSetting(AUDIO_HISTORY_RETENTION_HOURS))
}

/** Uses the current recognition model and cleanup settings, without opening the microphone. */
suspend fun Context.retranscribeAudio(id: String, scope: LifecycleCoroutineScope): String =
    withContext(Dispatchers.IO) {
        val selection = RecognitionModelSelection(getSetting(SPEECH_BACKEND),
            getSetting(MOONSHINE_MODEL_VARIANT), getSetting(NEMOTRON_PROFILE), getSetting(org.futo.voiceinput.settings.ASR4ALL_VARIANT))
        val lifecycle = RecognitionModelLifecycle.create(filesDir, BuildConfig.BUNDLE_PARAKEET_MODEL)
        val readiness = lifecycle.readiness(selection)
        val report = AppDiagnostics.session(readiness?.model?.id ?: selection.runtimeId)
        report?.event(DiagnosticEvent.SESSION_STARTED, mapOf(DiagnosticMetric.RETRANSCRIPTION to 1L))
        report?.let { AppDiagnostics.sample(it.id, it.model) }
        var operation = 14L
        try {
            val store = audioHistory()
            store.purge(getSetting(AUDIO_HISTORY_RETENTION_HOURS))
            operation = 15L
            val result = store.retranscribe(id) { samples ->
                check(readiness?.isReady != false) {
                    "Download the selected model in Model Options first."
                }
                if (selection.runtimeId.toSpeechBackendType() == SpeechBackendType.WhisperGGML) {
                    check(selectedWhisperModelsForCurrentSettings(null).none { modelNeedsDownloading(it) }) {
                        "Download the selected model in Model Options first."
                    }
                }
                val loadStarted = SystemClock.elapsedRealtime()
                runCatching { report?.event(DiagnosticEvent.MODEL_LOAD_STARTED,
                    configuration = DiagnosticConfiguration(runtime = selection.runtimeId,
                        modelVersion = readiness?.installedModel?.version)) }
                val backend = try {
                    lifecycle.load(this@retranscribeAudio, selection, RecognitionRuntimeCallbacks({}, {}, { null }))
                } catch (failure: CancellationException) { throw failure
                } catch (failure: Throwable) {
                    report?.event(DiagnosticEvent.MODEL_LOAD_FAILED, error = failure)
                    throw failure
                }
                try {
                    report?.event(DiagnosticEvent.MODEL_LOAD_FINISHED,
                        mapOf(DiagnosticMetric.DURATION_MS to SystemClock.elapsedRealtime() - loadStarted))
                    report?.let { AppDiagnostics.sample(it.id, it.model, detailed = true) }
                    val recognitionStarted = SystemClock.elapsedRealtime()
                    report?.event(DiagnosticEvent.RECOGNITION_STARTED,
                        mapOf(DiagnosticMetric.AUDIO_MS to samples.size.toLong() * 1000 / 16000))
                    val raw = backend.transcribe(samples)
                    report?.event(DiagnosticEvent.RECOGNITION_FINISHED,
                        mapOf(DiagnosticMetric.DURATION_MS to SystemClock.elapsedRealtime() - recognitionStarted,
                            DiagnosticMetric.CHARACTERS to raw.length.toLong()),
                        configuration = DiagnosticConfiguration(language = backend.detectedLanguage))
                    report?.let { AppDiagnostics.sample(it.id, it.model, detailed = true) }
                    if (raw.isBlank()) report?.event(DiagnosticEvent.NO_SPEECH)
                    check(raw.isNotBlank()) { "No speech was recognized. The audio is still saved." }
                    val finalized = TranscriptFinalizer.clean(this@retranscribeAudio, raw,
                        selection.runtimeId.toSpeechBackendType(), backend.detectedLanguage, null,
                        getSetting(PERSONAL_DICTIONARY), report)
                    check(finalized.text.isNotBlank() || finalized.validEmpty) {
                        "No speech was recognized. The audio is still saved."
                    }
                    finalized.text
                } finally {
                    withContext(NonCancellable) { lifecycle.release(backend, scope) }
                }
            }
            report?.end(DiagnosticEvent.RETRANSCRIPTION_FINISHED)
            result
        } catch (failure: CancellationException) {
            report?.end(DiagnosticEvent.SESSION_CANCELLED)
            throw failure
        } catch (failure: Throwable) {
            runCatching { report?.event(DiagnosticEvent.HISTORY_OPERATION_FAILED,
                mapOf(DiagnosticMetric.OPERATION to operation), error = failure) }
            report?.end(DiagnosticEvent.SESSION_FAILED, failure)
            throw failure
        }
    }

class AudioHistoryCleanupService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        job = scope.launch {
            val success = try {
                purgeAudioHistory()
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AppDiagnostics.event(DiagnosticEvent.HISTORY_OPERATION_FAILED,
                    metrics = mapOf(DiagnosticMetric.OPERATION to 14L), error = failure)
                false
            }
            jobFinished(params, !success)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 15782791

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(JOB_ID) == null) {
                scheduler.schedule(JobInfo.Builder(JOB_ID,
                    ComponentName(context, AudioHistoryCleanupService::class.java))
                    .setPeriodic(15 * 60 * 1000L).setPersisted(true).build())
            }
        }
    }
}
