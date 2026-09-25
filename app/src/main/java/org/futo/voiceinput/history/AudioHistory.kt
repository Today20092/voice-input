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
import org.futo.voiceinput.PersonalVocabulary
import org.futo.voiceinput.diagnostics.AppDiagnostics
import org.futo.voiceinput.diagnostics.DiagnosticEvent
import org.futo.voiceinput.diagnostics.DiagnosticMetric
import org.futo.voiceinput.modelNeedsDownloading
import org.futo.voiceinput.selectedWhisperModelsForCurrentSettings
import org.futo.voiceinput.recognition.RecognitionModelLifecycle
import org.futo.voiceinput.recognition.RecognitionModelSelection
import org.futo.voiceinput.recognition.RecognitionRuntimeCallbacks
import org.futo.voiceinput.s1.S1MiniTranscriptCleaner
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
            getSetting(MOONSHINE_MODEL_VARIANT), getSetting(NEMOTRON_PROFILE))
        val lifecycle = RecognitionModelLifecycle.create(filesDir, BuildConfig.BUNDLE_PARAKEET_MODEL)
        val readiness = lifecycle.readiness(selection)
        val report = AppDiagnostics.session(readiness?.model?.id ?: selection.runtimeId)
        report.event(DiagnosticEvent.SESSION_STARTED, mapOf(DiagnosticMetric.RETRANSCRIPTION to 1L))
        AppDiagnostics.sample(report.id, report.model)
        try {
            val store = audioHistory()
            store.purge(getSetting(AUDIO_HISTORY_RETENTION_HOURS))
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
                report.event(DiagnosticEvent.MODEL_LOAD_STARTED)
                val backend = try {
                    lifecycle.load(this@retranscribeAudio, selection, RecognitionRuntimeCallbacks({}, {}, { null }))
                } catch (failure: CancellationException) { throw failure
                } catch (failure: Throwable) {
                    report.event(DiagnosticEvent.MODEL_LOAD_FAILED, error = failure)
                    throw failure
                }
                try {
                    report.event(DiagnosticEvent.MODEL_LOAD_FINISHED,
                        mapOf(DiagnosticMetric.DURATION_MS to SystemClock.elapsedRealtime() - loadStarted))
                    AppDiagnostics.sample(report.id, report.model, detailed = true)
                    val recognitionStarted = SystemClock.elapsedRealtime()
                    report.event(DiagnosticEvent.RECOGNITION_STARTED,
                        mapOf(DiagnosticMetric.AUDIO_MS to samples.size.toLong() * 1000 / 16000))
                    val raw = backend.transcribe(samples)
                    report.event(DiagnosticEvent.RECOGNITION_FINISHED,
                        mapOf(DiagnosticMetric.DURATION_MS to SystemClock.elapsedRealtime() - recognitionStarted,
                            DiagnosticMetric.CHARACTERS to raw.length.toLong()))
                    AppDiagnostics.sample(report.id, report.model, detailed = true)
                    check(raw.isNotBlank()) { "No speech was recognized. The audio is still saved." }
                    val cleanupStarted = SystemClock.elapsedRealtime()
                    report.event(DiagnosticEvent.CLEANUP_STARTED)
                    val cleaned = S1MiniTranscriptCleaner.clean(this@retranscribeAudio, raw,
                        selection.runtimeId.toSpeechBackendType(), backend.detectedLanguage, null, {})
                    report.event(DiagnosticEvent.CLEANUP_FINISHED,
                        mapOf(DiagnosticMetric.DURATION_MS to SystemClock.elapsedRealtime() - cleanupStarted,
                            DiagnosticMetric.APPLIED to if (cleaned.applied) 1L else 0L))
                    val text = PersonalVocabulary.apply(cleaned.text, getSetting(PERSONAL_DICTIONARY))
                    check(text.isNotBlank() || cleaned.validEmpty) {
                        "No speech was recognized. The audio is still saved."
                    }
                    text
                } finally {
                    withContext(NonCancellable) { lifecycle.release(backend, scope) }
                }
            }
            report.end(DiagnosticEvent.RETRANSCRIPTION_FINISHED)
            result
        } catch (failure: CancellationException) {
            report.end(DiagnosticEvent.SESSION_CANCELLED)
            throw failure
        } catch (failure: Throwable) {
            report.end(DiagnosticEvent.SESSION_FAILED, failure)
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
