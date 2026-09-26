package org.futo.voiceinput

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorPrivacyManager
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MicrophoneDirection
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import com.konovalov.vad.webrtc.VadWebRTC
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.futo.voiceinput.ml.RunState
import org.futo.voiceinput.diagnostics.AppDiagnostics
import org.futo.voiceinput.diagnostics.DiagnosticEvent
import org.futo.voiceinput.diagnostics.DiagnosticMetric
import org.futo.voiceinput.diagnostics.DiagnosticSession
import org.futo.voiceinput.history.AudioHistoryStore
import org.futo.voiceinput.harper.HarperTranscriptCleaner
import org.futo.voiceinput.history.audioHistory
import org.futo.voiceinput.settings.AUDIO_HISTORY_ENABLED
import org.futo.voiceinput.settings.AUDIO_HISTORY_RETENTION_HOURS
import org.futo.voiceinput.settings.ENABLE_30S_LIMIT
import org.futo.voiceinput.settings.END_OF_SPEECH_PROFILE
import org.futo.voiceinput.settings.IS_VAD_ENABLED
import org.futo.voiceinput.settings.MANUAL_STOP_DRAIN_MS
import org.futo.voiceinput.settings.MOONSHINE_MODEL_VARIANT
import org.futo.voiceinput.settings.NEMOTRON_PROFILE
import org.futo.voiceinput.settings.PARAKEET_KEEP_WARM
import org.futo.voiceinput.settings.PARAKEET_KEEP_WARM_TIMEOUT_MS
import org.futo.voiceinput.settings.PARAKEET_USE_VAD
import org.futo.voiceinput.settings.PERSONAL_DICTIONARY
import org.futo.voiceinput.settings.SPEECH_BACKEND
import org.futo.voiceinput.settings.S1_MINI_TRANSCRIPT_DIAGNOSTICS
import org.futo.voiceinput.settings.SpeechBackendType
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.parakeet.ParakeetEngineLease
import org.futo.voiceinput.parakeet.parakeetUnifiedRecognitionModel
import org.futo.voiceinput.backend.SpeechBackend
import org.futo.voiceinput.backend.StreamingSpeechBackend
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelLifecycle
import org.futo.voiceinput.recognition.RecognitionModelSelection
import org.futo.voiceinput.recognition.RecognitionModelStore
import org.futo.voiceinput.recognition.RecognitionRuntimeCallbacks
import org.futo.voiceinput.s1.S1MiniCleanupResult
import org.futo.voiceinput.s1.S1MiniDiagnostics
import org.futo.voiceinput.s1.S1MiniTranscriptCleaner
import org.futo.voiceinput.settings.toSpeechBackendType
import org.futo.voiceinput.settings.toEndOfSpeechProfile
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

private const val AUDIO_SAMPLE_RATE = 16000
private const val AUDIO_READ_SIZE = 1600
private const val AUTO_STOP_DRAIN_MS = 100L
private const val PARAKEET_AUTO_STOP_DRAIN_MS = 300L
private const val FINAL_SILENCE_PAD_MS = 200

internal class NoSpeechRecognizedException : Exception("The recognizer returned no text")

enum class MagnitudeState {
    NOT_TALKED_YET,
    MIC_MAY_BE_BLOCKED,
    TALKING,
    ENDING_SOON_VAD,
    ENDING_SOON_30S
}

internal enum class StopReason {
    Manual,
    Vad,
    DurationLimit,
    Cancel
}

private enum class AppendResult {
    Accepted,
    DurationLimit,
    SessionEnded
}

internal object RecordingSessionPolicy {
    fun shouldRetryRecorderInitialization(failedAttempt: Int) = failedAttempt <= 32
    fun shouldReportRecorderReadFailure(readCount: Int, stopping: Boolean) = readCount <= 0 && !stopping
    fun shouldAcceptSamples(stopReason: StopReason?, captureGeneration: Long, currentGeneration: Long) =
        stopReason != StopReason.Cancel && captureGeneration == currentGeneration

    fun tailDrainMs(reason: StopReason, backendType: SpeechBackendType, manualDrainMs: Long) = when(reason) {
        StopReason.Manual -> manualDrainMs.coerceIn(0L, 1500L)
        StopReason.Vad -> if (backendType == SpeechBackendType.Parakeet) PARAKEET_AUTO_STOP_DRAIN_MS else AUTO_STOP_DRAIN_MS
        StopReason.DurationLimit -> AUTO_STOP_DRAIN_MS
        StopReason.Cancel -> 0L
    }
}

internal class StreamingAudioReplay {
    private val pendingAudio = mutableListOf<FloatArray>()
    private var activeBackend: StreamingSpeechBackend? = null
    private var enabled = false

    @Synchronized
    fun reset(enabled: Boolean = false) {
        pendingAudio.clear()
        activeBackend = null
        this.enabled = enabled
    }

    @Synchronized
    fun isEnabled() = enabled

    @Synchronized
    fun acceptAudio(samples: FloatArray) {
        if (!enabled) return

        val backend = activeBackend
        if (backend == null) {
            pendingAudio.add(samples)
        } else {
            backend.acceptAudio(samples)
        }
    }

    @Synchronized
    fun start(
        backend: StreamingSpeechBackend,
        onPartial: (String) -> Unit,
        onCatchingUp: (Boolean) -> Unit
    ) {
        if (!enabled || activeBackend === backend) return
        check(activeBackend == null)

        backend.startStreaming(onPartial, onCatchingUp)
        pendingAudio.forEach(backend::acceptAudio)
        pendingAudio.clear()
        activeBackend = backend
    }
}

abstract class RecordingSession {
    protected var diagnostics: DiagnosticSession? = null
        private set
    private var diagnosticSamplingJob: Job? = null
    private data class OwnedParakeetLease(
        val generation: Long,
        val lease: ParakeetEngineLease
    )

    private var isRecording = false
    private var recorder: AudioRecord? = null
    @Volatile private var stopReason: StopReason? = null

    fun isCurrentlyRecording(): Boolean {
        return isRecording
    }

    private var backend: SpeechBackend? = null
    private var backendGeneration = -1L
    private var parakeetLease: OwnedParakeetLease? = null
    @Volatile
    private var recognitionGeneration = 0L

    private var floatSamples: FloatBuffer = FloatBuffer.allocate(16000 * 30)
    private val waveform = RecordingWaveform()
    private var recorderJob: Job? = null
    private var modelJob: Job? = null
    private var loadModelJob: Job? = null
    private var personalVocabulary = ""
    private var backup: Pair<Long, AudioHistoryStore.Capture>? = null
    private var backupId: Pair<Long, String>? = null

    private fun backupFailed() {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, R.string.audio_history_save_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun saveBackupTranscript(id: String?, text: String) {
        if (id == null) return
        try {
            context.audioHistory().saveTranscript(id, text)
        } catch (error: Exception) {
            backupFailed()
        }
    }
    private val streamingAudio = StreamingAudioReplay()
    private var selectedManagedModel: RecognitionModel? = null
    var selectedModelName: String? = null
        private set
    private val modelLifecycle by lazy {
        RecognitionModelLifecycle.create(context.filesDir, BuildConfig.BUNDLE_PARAKEET_MODEL)
    }

    private var canExpandSpace = true

    @Synchronized
    private fun clearCapturedSamples() {
        floatSamples.clear()
        waveform.clear()
    }

    @Synchronized
    private fun cancelCapture() {
        stopReason = StopReason.Cancel
    }

    private fun expandSpaceIfAllowed(): Boolean {
        if(canExpandSpace) {
            // Allocate an extra 30 seconds
            val newSampleBuffer = FloatBuffer.allocate(floatSamples.capacity() + 16000 * 30)
            newSampleBuffer.put(floatSamples.array(), 0, floatSamples.capacity() - floatSamples.remaining())
            floatSamples = newSampleBuffer
            return true
        }
        return false
    }


    protected abstract val context: Context
    protected abstract val lifecycleScope: LifecycleCoroutineScope

    protected abstract fun cancelled()
    protected abstract fun finished(result: String)
    protected abstract fun failed(error: Throwable)
    protected abstract fun languageDetected(result: String)
    protected abstract fun partialResult(result: String)
    protected abstract fun decodingStatus(status: RunState)

    protected abstract fun loading()
    protected abstract fun needParakeetModelDownload()
    protected abstract fun needRecognitionModelDownload(model: RecognitionModel)
    protected abstract fun needMoonshineModelDownload()
    protected abstract fun needWhisperModelDownload(models: List<ModelData>)
    protected abstract fun needPermission()
    protected abstract fun permissionRejected()

    protected abstract fun recordingStarted()
    protected abstract fun updateWaveform(bars: List<Pair<Float, Float>>, state: MagnitudeState)

    protected abstract fun processing()
    protected abstract fun cleaning()

    private var isVADPaused = false
    fun pauseVAD(v: Boolean) {
        isVADPaused = v
    }

    fun finishRecognizerIfRecording() {
        if (isRecording) {
            finishRecognizer()
        }
    }

    protected fun finishRecognizer() {
        println("Finish called")
        onFinishRecording()
    }

    fun cancelRecognizer() {
        println("Cancelling recognition")
        diagnostics?.end(DiagnosticEvent.SESSION_CANCELLED)
        reset()

        cancelled()
    }

    @Synchronized
    private fun stopAndReleaseRecorder(target: AudioRecord? = recorder) {
        val current = target ?: return
        if (recorder !== current) {
            return
        }

        try {
            if (current.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                current.stop()
            }
        } catch(e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                current.release()
            } catch(e: Exception) {
                e.printStackTrace()
            } finally {
                if (recorder === current) {
                    recorder = null
                }
            }
        }
    }

    fun reset() {
        diagnostics?.end(DiagnosticEvent.SESSION_RESET)
        diagnosticSamplingJob?.cancel()
        isVADPaused = false
        cancelCapture()
        stopAndReleaseRecorder()
        val recorderJobToJoin: Job?
        val modelJobToJoin: Job?
        val loadModelJobToJoin: Job?
        val backendToClose: SpeechBackend?
        val parakeetLeaseToRelease: OwnedParakeetLease?
        val backupToClose: AudioHistoryStore.Capture?
        synchronized(this) {
            recognitionGeneration += 1
            backupToClose = backup?.second
            backup = null
            backupId = null
            recorderJobToJoin = recorderJob
            modelJobToJoin = modelJob
            loadModelJobToJoin = loadModelJob
            backendToClose = backend
            parakeetLeaseToRelease = parakeetLease
            recorderJob = null
            modelJob = null
            loadModelJob = null
            backend = null
            backendGeneration = -1L
            parakeetLease = null
            streamingAudio.reset()
        }
        recorderJobToJoin?.cancel()
        modelJobToJoin?.cancel()
        loadModelJobToJoin?.cancel()
        isRecording = false

        clearCapturedSamples()

        unfocusAudio()

        lifecycleScope.launch(NonCancellable) {
            recorderJobToJoin?.join()
            modelJobToJoin?.join()
            loadModelJobToJoin?.join()
            withContext(Dispatchers.IO) { runCatching { backupToClose?.close() } }
            if (parakeetLeaseToRelease != null) {
                modelLifecycle.release(parakeetLeaseToRelease.lease, lifecycleScope)
            } else if (backendToClose != null) {
                modelLifecycle.release(backendToClose, lifecycleScope)
            }
        }
    }

    protected fun openPermissionSettings() {
        val packageName = context.packageName
        val myAppSettings = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse(
                "package:$packageName"
            )
        )
        myAppSettings.addCategory(Intent.CATEGORY_DEFAULT)
        myAppSettings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(myAppSettings)

        cancelRecognizer()
    }

    private var focusRequest: AudioFocusRequest? = null
    private fun focusAudio() {
        unfocusAudio()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                focusRequest =
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .build()
                audioManager.requestAudioFocus(focusRequest!!)
            }
        }catch(e: Exception) {
            e.printStackTrace()
        }
    }
    private fun unfocusAudio() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (focusRequest != null) {
                    audioManager.abandonAudioFocusRequest(focusRequest!!)
                }
                focusRequest = null
            }
        }catch(e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadModelInner(retryAfterOom: Boolean = true) {
        val loadGeneration = recognitionGeneration
        val report = diagnostics
        val loadStarted = SystemClock.elapsedRealtime()
        report?.event(DiagnosticEvent.MODEL_LOAD_STARTED)
        try {
            val selection = RecognitionModelSelection(
                runtimeId = context.getSetting(SPEECH_BACKEND),
                moonshineVariantId = context.getSetting(MOONSHINE_MODEL_VARIANT),
                nemotronVariantId = context.getSetting(NEMOTRON_PROFILE)
            )
            val loadedBackend = modelLifecycle.load(
                context,
                selection,
                RecognitionRuntimeCallbacks(
                    onStatusUpdate = { decodingStatus(it) },
                    onPartialDecode = {
                        report?.partial(it.length)
                        lifecycleScope.launch {
                            withContext(Dispatchers.Main) {
                                partialResult(it)
                            }
                        }
                    },
                    forceLanguageProvider = { forcedLanguage }
                )
            )
            val published = synchronized(this) {
                if (loadGeneration != recognitionGeneration) {
                    false
                } else {
                    backend = loadedBackend
                    backendGeneration = loadGeneration
                    if (loadedBackend is ParakeetEngineLease) {
                        parakeetLease = OwnedParakeetLease(loadGeneration, loadedBackend)
                    }
                    true
                }
            }
            if (!published) {
                withContext(NonCancellable) {
                    modelLifecycle.release(loadedBackend, lifecycleScope)
                }
                throw CancellationException("Recognition was reset while the model loaded")
            }
            (loadedBackend as? StreamingSpeechBackend)?.let(::startStreaming)
            report?.event(DiagnosticEvent.MODEL_LOAD_FINISHED,
                mapOf(DiagnosticMetric.DURATION_MS to SystemClock.elapsedRealtime() - loadStarted))
        } catch (error: CancellationException) {
            throw error
        } catch(e: OutOfMemoryError) {
            report?.event(DiagnosticEvent.MODEL_LOAD_FAILED, error = e)
            if (loadGeneration != recognitionGeneration) {
                throw CancellationException("Recognition was reset while loading the model")
            }
            decodingStatus(RunState.OOMError)
            val failedBackend = backendForGeneration(loadGeneration)
            if (failedBackend != null) {
                modelLifecycle.release(failedBackend, lifecycleScope)
            }
            clearBackend(loadGeneration, failedBackend)

            for(i in 0 until 2) {
                System.gc()
                System.runFinalization()
                delay(500L)
            }

            if (loadGeneration != recognitionGeneration) {
                throw CancellationException("Recognition was reset while recovering from OOM")
            }
            if (retryAfterOom) {
                return loadModelInner(retryAfterOom = false)
            }
            withContext(Dispatchers.Main) { failed(e) }
        } catch (error: Exception) {
            report?.event(DiagnosticEvent.MODEL_LOAD_FAILED, error = error)
            if (loadGeneration == recognitionGeneration) {
                selectedManagedModel?.let {
                    RecognitionModelStore(context.filesDir).invalidate(it)
                }
                withContext(Dispatchers.Main) { failed(error) }
            }
        }
    }

    private fun loadModel() {
        if (backend == null) {
            loadModelJob = lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    loadModelInner()
                }
            }
        }
    }

    private var forcedLanguage: String? = null
    fun forceLanguage(language: String?) {
        forcedLanguage = language
    }

    fun create() {
        selectedModelName = null
        loading()

        lifecycleScope.launch {
            val backendType = context.getSetting(SPEECH_BACKEND).toSpeechBackendType()
            diagnostics?.end(DiagnosticEvent.SESSION_RESET)
            diagnosticSamplingJob?.cancel()
            personalVocabulary = context.getSetting(PERSONAL_DICTIONARY)
            val readiness = RecognitionModelLifecycle.create(
                context.filesDir,
                BuildConfig.BUNDLE_PARAKEET_MODEL
            ).readiness(
                RecognitionModelSelection(
                    runtimeId = backendType.id,
                    moonshineVariantId = context.getSetting(MOONSHINE_MODEL_VARIANT),
                    nemotronVariantId = context.getSetting(NEMOTRON_PROFILE)
                )
            )
            selectedManagedModel = readiness?.model
            selectedModelName = readiness?.model?.displayName?.let { name ->
                if (backendType == SpeechBackendType.Nemotron && !name.startsWith("Nemotron")) {
                    "Nemotron $name"
                } else {
                    name
                }
            }
            val report = AppDiagnostics.session(readiness?.model?.id ?: backendType.id)
            diagnostics = report
            report.event(DiagnosticEvent.SESSION_STARTED)
            AppDiagnostics.sample(report.id, report.model)
            diagnosticSamplingJob = lifecycleScope.launch {
                while (!report.terminal) {
                    delay(5_000L)
                    if (!report.terminal) AppDiagnostics.sample(report.id, report.model, detailed = true)
                }
            }
            if (readiness != null && !readiness.isReady) {
                report.event(DiagnosticEvent.MODEL_REQUIRED)
                needRecognitionModelDownload(readiness.model)
                return@launch
            }
            streamingAudio.reset(
                backendType == SpeechBackendType.ParakeetUnified ||
                    backendType == SpeechBackendType.Nemotron ||
                    backendType == SpeechBackendType.Moonshine
            )
            if (backendType == SpeechBackendType.WhisperGGML) {
                    val requiredModels = context.selectedWhisperModelsForCurrentSettings(forcedLanguage)
                    selectedModelName = requiredModels.joinToString(" / ") {
                        it.name.substringBefore(" (")
                    }
                    if (requiredModels.any { context.modelNeedsDownloading(it) }) {
                        report.event(DiagnosticEvent.MODEL_REQUIRED)
                        needWhisperModelDownload(requiredModels)
                        return@launch
                    }
            }

            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                report.event(DiagnosticEvent.PERMISSION_REQUIRED)
                needPermission()
            } else {
                startRecording()
            }
        }
    }

    fun permissionResultGranted() {
        startRecording()
    }

    fun permissionResultRejected() {
        diagnostics?.event(DiagnosticEvent.PERMISSION_REJECTED)
        permissionRejected()
    }

    private fun startRecording(numTries: Int = 0) {
        val report = diagnostics
        if (BuildConfig.DEBUG) Log.d("WaveformTiming", "recorder_requested t=${SystemClock.elapsedRealtime()}")
        if (isRecording) {
            throw IllegalStateException("Start recording when already recording")
        }

        isVADPaused = false
        stopReason = null
        clearCapturedSamples()
        canExpandSpace = true

        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                AUDIO_SAMPLE_RATE * 2 * 5
            )

            if(recorder!!.state == AudioRecord.STATE_UNINITIALIZED) {
                recorder!!.release()
                recorder = null

                println("Failed to initialize AudioRecord, retrying")
                report?.event(DiagnosticEvent.RECORDER_RETRY,
                    mapOf(DiagnosticMetric.ATTEMPT to numTries.toLong()))

                if(!RecordingSessionPolicy.shouldRetryRecorderInitialization(numTries)) {
                    throw IllegalStateException("AudioRecord could not be initialized in 32 tries")
                }

                return startRecording(numTries + 1)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    recorder!!.setPreferredMicrophoneDirection(MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER)
                }
            } catch(e: Exception) {
                println("Failed to set preferred mic direction")
                e.printStackTrace()
            }

            recorder!!.startRecording()
            report?.event(DiagnosticEvent.RECORDER_STARTED)
            if (BuildConfig.DEBUG) Log.d("WaveformTiming", "recorder_started t=${SystemClock.elapsedRealtime()}")
            val activeRecorder = recorder!!
            val captureGeneration = recognitionGeneration

            focusAudio()
            isRecording = true

            (backend as? StreamingSpeechBackend)?.let(::startStreaming)

            val canMicBeBlocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(SensorPrivacyManager::class.java) as SensorPrivacyManager).supportsSensorToggle(
                    SensorPrivacyManager.Sensors.MICROPHONE
                )
            } else {
                false
            }

            recorderJob = lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    canExpandSpace = context.getSetting(ENABLE_30S_LIMIT) == false
                    val backendType = context.getSetting(SPEECH_BACKEND).toSpeechBackendType()
                    val shouldUseVad = context.getSetting(IS_VAD_ENABLED) &&
                        (backendType != SpeechBackendType.Parakeet || context.getSetting(PARAKEET_USE_VAD))
                    val endOfSpeechProfile = context.getSetting(END_OF_SPEECH_PROFILE).toEndOfSpeechProfile()

                    var hasTalked = false
                    var anyNoiseAtAll = false
                    var isMicBlocked = false

                    val vad = VadWebRTC(
                        sampleRate = SampleRate.SAMPLE_RATE_16K,
                        frameSize = FrameSize.FRAME_SIZE_480,
                        mode = if (backendType == SpeechBackendType.Parakeet) Mode.AGGRESSIVE else Mode.VERY_AGGRESSIVE,
                        speechDurationMs = 150,
                        silenceDurationMs = 300
                    )
                    
                    val vadSampleBuffer = ShortBuffer.allocate(480)
                    var numConsecutiveNonSpeech = 0
                    var numConsecutiveSpeech = 0

                    val samples = ShortArray(AUDIO_READ_SIZE)
                    var firstRead = true
                    var firstUpdate = true

                    val capture = try {
                        val store = context.audioHistory()
                        store.purge(context.getSetting(AUDIO_HISTORY_RETENTION_HOURS))
                        if (context.getSetting(AUDIO_HISTORY_ENABLED)) store.begin() else null
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        backupFailed()
                        null
                    }

                    try {
                        synchronized(this@RecordingSession) {
                            if (captureGeneration == recognitionGeneration) {
                                backup = capture?.let { captureGeneration to it }
                                backupId = capture?.let { captureGeneration to it.id }
                            }
                        }
                        captureLoop@ while(stopReason == null && activeRecorder.recordingState == AudioRecord.RECORDSTATE_RECORDING){
                            yield()
                            val nRead = activeRecorder.read(samples, 0, AUDIO_READ_SIZE, AudioRecord.READ_BLOCKING)

                            if(nRead <= 0) {
                                if (RecordingSessionPolicy.shouldReportRecorderReadFailure(nRead,
                                    stopReason != null || captureGeneration != recognitionGeneration || report?.terminal == true)) {
                                    report?.event(DiagnosticEvent.RECORDER_READ_FAILED,
                                        mapOf(DiagnosticMetric.ERROR_CODE to nRead.toLong()))
                                }
                                break
                            }
                            if (firstRead) {
                                firstRead = false
                                report?.event(DiagnosticEvent.FIRST_AUDIO)
                                if (BuildConfig.DEBUG) Log.d("WaveformTiming", "first_samples t=${SystemClock.elapsedRealtime()}")
                            }
                            yield()

                            // Persist before VAD or streaming recognition can fail.
                            val appendResult = appendSamples(samples, nRead, captureGeneration)
                            if (appendResult == AppendResult.SessionEnded) break
                            if (appendResult == AppendResult.DurationLimit) {
                                withContext(Dispatchers.Main) {
                                    if (isRecording && captureGeneration == recognitionGeneration) finishRecognizer()
                                }
                                break
                            }

                            // Run VAD
                            if(shouldUseVad && !isVADPaused) {
                                var remainingSamples = nRead
                                var offset = 0
                                while(remainingSamples > 0) {
                                    if(!vadSampleBuffer.hasRemaining()) {
                                        val isSpeech = vad.isSpeech(vadSampleBuffer.array())
                                        vadSampleBuffer.clear()
                                        vadSampleBuffer.rewind()

                                        if(!isSpeech) {
                                            numConsecutiveNonSpeech++
                                            numConsecutiveSpeech = 0
                                        } else {
                                            numConsecutiveNonSpeech = 0
                                            numConsecutiveSpeech++
                                        }
                                    }

                                    val samplesToRead = min(min(remainingSamples, 480), vadSampleBuffer.remaining())
                                    for(i in 0 until samplesToRead) {
                                        vadSampleBuffer.put(samples[offset])
                                        offset += 1
                                        remainingSamples -= 1
                                    }
                                }
                            } else {
                                numConsecutiveNonSpeech = 0
                            }

                            if(stopReason != null) break

                            // Don't set hasTalked if the start sound may still be playing, otherwise on some
                            // devices the rms just explodes and `hasTalked` is always true
                            val startSoundPassed = (floatSamples.position() > AUDIO_SAMPLE_RATE*0.6)
                            if(!startSoundPassed){
                                numConsecutiveSpeech = 0
                                numConsecutiveNonSpeech = 0
                            }

                            val rms = sqrt(samples.sumOf { ((it.toFloat() / Short.MAX_VALUE.toFloat()).pow(2)).toDouble() } / samples.size).toFloat()

                            if(startSoundPassed && ((rms > 0.01) || (numConsecutiveSpeech > 8))) hasTalked = true

                            if(rms > 0.0001){
                                anyNoiseAtAll = true
                                isMicBlocked = false
                            }

                            // Check if mic is blocked
                            if(!anyNoiseAtAll && canMicBeBlocked && (floatSamples.position() > 2*AUDIO_SAMPLE_RATE)){
                                isMicBlocked = true
                            }

                            // End if VAD hasn't detected speech in a while
                            if(shouldUseVad && hasTalked && (numConsecutiveNonSpeech > endOfSpeechProfile.silenceFrames)) {
                                stopReason = StopReason.Vad
                                withContext(Dispatchers.Main){
                                    if(isRecording) {
                                        finishRecognizer()
                                    }
                                }
                                break
                            }

                            val state = if (!canExpandSpace && floatSamples.remaining() < (AUDIO_SAMPLE_RATE * 5)) {
                                MagnitudeState.ENDING_SOON_30S
                            } else if(hasTalked && shouldUseVad && (numConsecutiveNonSpeech > 33)) {
                                MagnitudeState.ENDING_SOON_VAD
                            } else if(hasTalked) {
                                MagnitudeState.TALKING
                            } else if(isMicBlocked) {
                                MagnitudeState.MIC_MAY_BE_BLOCKED
                            } else {
                                MagnitudeState.NOT_TALKED_YET
                            }

                            yield()
                            withContext(Dispatchers.Main) {
                                yield()
                                if(isRecording && captureGeneration == recognitionGeneration) {
                                    if (firstUpdate) {
                                        firstUpdate = false
                                        if (BuildConfig.DEBUG) Log.d("WaveformTiming", "first_update t=${SystemClock.elapsedRealtime()}")
                                    }
                                    updateWaveform(synchronized(this@RecordingSession) { waveform.snapshot() }, state)
                                }
                            }

                            // Skip ahead as much as possible, in case we are behind (taking more than
                            // 100ms to process 100ms)
                            while(stopReason == null){
                                yield()
                                val nRead2 = activeRecorder.read(samples, 0, AUDIO_READ_SIZE, AudioRecord.READ_NON_BLOCKING)
                                if(nRead2 > 0) {
                                    when (appendSamples(samples, nRead2, captureGeneration)) {
                                        AppendResult.Accepted -> Unit
                                        AppendResult.SessionEnded -> break@captureLoop
                                        AppendResult.DurationLimit -> {
                                            yield()
                                            withContext(Dispatchers.Main){
                                                if(isRecording && captureGeneration == recognitionGeneration) {
                                                    finishRecognizer()
                                                }
                                            }
                                            break@captureLoop
                                        }
                                    }
                                } else {
                                    break
                                }
                            }
                        }

                        val reason = stopReason
                        if(reason != null && reason != StopReason.Cancel && activeRecorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            drainRecorderTail(activeRecorder, reason, samples, captureGeneration)
                            appendFinalSilence(captureGeneration)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        report?.event(DiagnosticEvent.RECORDER_FAILED, error = error)
                        throw error
                    } finally {
                        try {
                            capture?.finishWriting()
                            if (stopReason == null || stopReason == StopReason.Cancel ||
                                captureGeneration != recognitionGeneration) capture?.close()
                        } catch (error: Exception) {
                            runCatching { capture?.close() }
                            backupFailed()
                        }
                        stopAndReleaseRecorder(activeRecorder)
                    }
                }
            }

            // We can only load model now, because the model loading may fail and need to cancel
            // everything we just did.
            // TODO: We could check if the model exists before doing all this work
            loadModel()

            recordingStarted()
        } catch(e: SecurityException){
            report?.event(DiagnosticEvent.RECORDER_FAILED, error = e)
            stopAndReleaseRecorder()
            // It's possible we may have lost permission, so let's just ask for permission again
            needPermission()
        } catch(e: Exception) {
            report?.event(DiagnosticEvent.RECORDER_FAILED, error = e)
            stopAndReleaseRecorder()
            throw e
        }
    }

    @Synchronized
    private fun appendSamples(samples: ShortArray, nRead: Int, captureGeneration: Long): AppendResult {
        if (!RecordingSessionPolicy.shouldAcceptSamples(stopReason, captureGeneration, recognitionGeneration)) {
            return AppendResult.SessionEnded
        }

        backup?.takeIf { it.first == captureGeneration }?.second?.let { capture ->
            try {
                capture.append(samples, nRead)
            } catch (error: Exception) {
                backup = null
                runCatching { capture.close() }
                backupFailed()
            }
        }

        if(floatSamples.remaining() < nRead && !expandSpaceIfAllowed()) {
            stopReason = StopReason.DurationLimit
            return AppendResult.DurationLimit
        }

        val streamingChunk = if (streamingAudio.isEnabled()) FloatArray(nRead) else null
        for(i in 0 until nRead) {
            val sample = samples[i].toFloat() / Short.MAX_VALUE.toFloat()
            floatSamples.put(sample)
            streamingChunk?.set(i, sample)
        }
        streamingChunk?.let(streamingAudio::acceptAudio)
        waveform.append(samples, nRead)

        return AppendResult.Accepted
    }

    private fun startStreaming(backend: StreamingSpeechBackend) {
        val report = diagnostics
        streamingAudio.start(
            backend = backend,
            onPartial = { result ->
                report?.partial(result.length)
                lifecycleScope.launch {
                    withContext(Dispatchers.Main) {
                        partialResult(PersonalVocabulary.apply(result, personalVocabulary))
                    }
                }
            },
            onCatchingUp = { catchingUp ->
                report?.event(DiagnosticEvent.CATCHING_UP,
                    mapOf(DiagnosticMetric.CATCHING_UP to if (catchingUp) 1L else 0L), detailed = true)
                lifecycleScope.launch(Dispatchers.Main) {
                    decodingStatus(if (catchingUp) RunState.CatchingUp else RunState.Streaming)
                }
            }
        )
    }

    private suspend fun drainRecorderTail(
        recorder: AudioRecord,
        reason: StopReason,
        scratch: ShortArray,
        captureGeneration: Long
    ) {
        val drainMs = RecordingSessionPolicy.tailDrainMs(
            reason,
            context.getSetting(SPEECH_BACKEND).toSpeechBackendType(),
            context.getSetting(MANUAL_STOP_DRAIN_MS)
        )

        val deadline = System.currentTimeMillis() + drainMs
        while(drainMs > 0L && System.currentTimeMillis() < deadline && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            yield()
            val nRead = recorder.read(scratch, 0, scratch.size, AudioRecord.READ_NON_BLOCKING)
            if(nRead > 0) {
                if(appendSamples(scratch, nRead, captureGeneration) != AppendResult.Accepted) {
                    return
                }
            } else {
                delay(10L)
            }
        }
    }

    @Synchronized
    private fun appendFinalSilence(captureGeneration: Long) {
        if (!RecordingSessionPolicy.shouldAcceptSamples(stopReason, captureGeneration, recognitionGeneration)) return

        val silenceSamples = (AUDIO_SAMPLE_RATE * FINAL_SILENCE_PAD_MS) / 1000
        if(floatSamples.remaining() < silenceSamples && !expandSpaceIfAllowed()) {
            return
        }

        repeat(silenceSamples) {
            floatSamples.put(0.0f)
        }
        (backend as? StreamingSpeechBackend)?.acceptAudio(FloatArray(silenceSamples))
    }

    private suspend fun runModel() {
        val runGeneration = recognitionGeneration
        val report = diagnostics
        val runLoadModelJob = synchronized(this) {
            if (runGeneration == recognitionGeneration) loadModelJob else null
        }
        try {
            runModelInner(runGeneration, runLoadModelJob)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (runGeneration != recognitionGeneration) {
                return
            }
            Log.e("AudioRecognizer", "Speech recognition failed", error)
            report?.end(DiagnosticEvent.SESSION_FAILED, error)
            try {
                closeFailedBackend(runGeneration)
            } catch (closeError: Exception) {
                error.addSuppressed(closeError)
            }
            withContext(Dispatchers.Main) {
                failed(error)
            }
        } finally {
            withContext(NonCancellable) {
                try {
                    releaseParakeetLease(runGeneration, keepWarm = false)
                } finally {
                    val capture = synchronized(this@RecordingSession) {
                        backup?.takeIf { it.first == runGeneration }?.second
                    }
                    withContext(Dispatchers.IO) { runCatching { capture?.close() } }
                }
            }
        }
    }

    private suspend fun runModelInner(runGeneration: Long, runLoadModelJob: Job?) {
        val report = diagnostics
        val savedAudioId = synchronized(this) { backupId?.takeIf { it.first == runGeneration }?.second }
        if(runLoadModelJob != null && runLoadModelJob.isActive) {
            println("Model was not finished loading...")
            runLoadModelJob.join()
        }

        var runBackend = backendForGeneration(runGeneration)
        if(runBackend == null) {
            if (runGeneration != recognitionGeneration) {
                throw CancellationException("Recognition was reset before the model became ready")
            }
            println("Model was null by the time runModel was called...")
            loadModel()
            val retryLoadJob = synchronized(this) { loadModelJob }
            retryLoadJob?.join()
            runBackend = backendForGeneration(runGeneration)
        }
        runBackend ?: throw IllegalStateException("Model did not load for this recognition")

        val finalStopReason = stopReason
        val backendType = context.getSetting(SPEECH_BACKEND).toSpeechBackendType()
        val vadEnabled = context.getSetting(IS_VAD_ENABLED) &&
            (backendType != SpeechBackendType.Parakeet || context.getSetting(PARAKEET_USE_VAD))
        val floatArray = floatSamples.array().sliceArray(0 until floatSamples.position())
        if (BuildConfig.DEBUG) {
            var sumSquares = 0.0
            var peak = 0.0f
            for (sample in floatArray) {
                val abs = kotlin.math.abs(sample)
                if (abs > peak) peak = abs
                sumSquares += (sample * sample).toDouble()
            }
            val rms = if (floatArray.isNotEmpty()) sqrt(sumSquares / floatArray.size).toFloat() else 0.0f
            Log.d(
                "AudioRecognizer",
                "capture samples=${floatArray.size} durationSec=${"%.2f".format(floatArray.size.toFloat() / AUDIO_SAMPLE_RATE)} rms=${"%.5f".format(rms)} peak=${"%.5f".format(peak)} stopReason=$finalStopReason backend=$backendType vadEnabled=$vadEnabled"
            )
        }

        yield()
        var cleanupResult = S1MiniCleanupResult("", applied = false)
        val text = try {
            val recognitionStarted = SystemClock.elapsedRealtime()
            report?.event(DiagnosticEvent.RECOGNITION_STARTED,
                mapOf(DiagnosticMetric.AUDIO_MS to floatArray.size.toLong() * 1000 / AUDIO_SAMPLE_RATE))
            val rawText = (runBackend as? StreamingSpeechBackend)?.finishStreaming()
                ?: runBackend.transcribe(floatArray)
            report?.event(DiagnosticEvent.RECOGNITION_FINISHED, mapOf(
                DiagnosticMetric.DURATION_MS to SystemClock.elapsedRealtime() - recognitionStarted,
                DiagnosticMetric.CHARACTERS to rawText.length.toLong()))
            saveBackupTranscript(savedAudioId, rawText)
            val cleanupStarted = SystemClock.elapsedRealtime()
            report?.event(DiagnosticEvent.CLEANUP_STARTED)
            cleanupResult = S1MiniTranscriptCleaner.clean(
                context = context,
                rawTranscript = rawText,
                backend = backendType,
                detectedLanguage = runBackend.detectedLanguage,
                forcedLanguage = forcedLanguage,
                onCleaning = { withContext(Dispatchers.Main) { cleaning() } }
            )
            report?.event(DiagnosticEvent.CLEANUP_FINISHED, mapOf(
                DiagnosticMetric.DURATION_MS to SystemClock.elapsedRealtime() - cleanupStarted,
                DiagnosticMetric.APPLIED to if (cleanupResult.applied) 1L else 0L))
            val harperStarted = SystemClock.elapsedRealtime()
            val harperResult = HarperTranscriptCleaner.clean(
                context, cleanupResult.text, personalVocabulary, backendType,
                runBackend.detectedLanguage, forcedLanguage
            )
            report?.event(DiagnosticEvent.HARPER_FINISHED, mapOf(
                DiagnosticMetric.DURATION_MS to SystemClock.elapsedRealtime() - harperStarted,
                DiagnosticMetric.APPLIED to if (harperResult.edits > 0) 1L else 0L,
                DiagnosticMetric.HARPER_EDITS to harperResult.edits.toLong(),
                DiagnosticMetric.HARPER_OUTCOME to harperResult.outcome.toLong()))
            val finalDeliveredText = PersonalVocabulary.apply(harperResult.text, personalVocabulary)
            if (
                cleanupResult.diagnosticReportId != null &&
                context.getSetting(S1_MINI_TRANSCRIPT_DIAGNOSTICS)
            ) {
                runCatching {
                    S1MiniDiagnostics.recordTranscript(
                        context = context,
                        reportId = cleanupResult.diagnosticReportId,
                        rawTranscript = rawText,
                        cleanedTranscript = cleanupResult.text.takeIf { cleanupResult.applied },
                        finalDeliveredTranscript = finalDeliveredText,
                        failureOrBypassReason = cleanupResult.fallbackCategory
                    )
                }
            }
            finalDeliveredText
        } catch(e: OutOfMemoryError) {
            report?.event(DiagnosticEvent.SESSION_FAILED, error = e)
            decodingStatus(RunState.OOMError)
            closeFailedBackend(runGeneration)

            for(i in 0 until 2) {
                System.gc()
                System.runFinalization()
                delay(500L)
            }

            loadModel()
            return runModel()
        }

        if (runGeneration != recognitionGeneration) {
            throw CancellationException("Recognition was reset while decoding")
        }

        saveBackupTranscript(savedAudioId, text)

        val keepWarmEnabled = context.getSetting(PARAKEET_KEEP_WARM)
        val timeoutMs = context.getSetting(PARAKEET_KEEP_WARM_TIMEOUT_MS)
        modelLifecycle.release(runBackend, lifecycleScope, keepWarmEnabled, timeoutMs)
        synchronized(this) {
            if (parakeetLease?.generation == runGeneration) parakeetLease = null
        }
        clearBackend(runGeneration, runBackend)

        withContext(Dispatchers.Main) {
            runBackend.detectedLanguage?.let(::languageDetected)
            if (text.isBlank() && !cleanupResult.validEmpty) {
                failed(NoSpeechRecognizedException())
            } else {
                report?.event(DiagnosticEvent.RESULT_READY,
                    mapOf(DiagnosticMetric.CHARACTERS to text.length.toLong()))
                finished(text)
            }
        }
    }

    private suspend fun closeFailedBackend(generation: Long) {
        val backendToClose = backendForGeneration(generation)
        if (!releaseParakeetLease(generation, keepWarm = false)) {
            backendToClose?.let { modelLifecycle.release(it, lifecycleScope) }
        }
        clearBackend(generation, backendToClose)
    }

    private suspend fun releaseParakeetLease(
        generation: Long,
        keepWarm: Boolean,
        timeoutMs: Long = 0L
    ): Boolean {
        val ownedLease = synchronized(this) {
            parakeetLease?.takeIf { it.generation == generation }?.also {
                parakeetLease = null
            }
        } ?: return false
        modelLifecycle.release(ownedLease.lease, lifecycleScope, keepWarm, timeoutMs)
        return true
    }

    private fun backendForGeneration(generation: Long): SpeechBackend? = synchronized(this) {
        backend.takeIf { backendGeneration == generation }
    }

    private fun clearBackend(generation: Long, expectedBackend: SpeechBackend?) {
        synchronized(this) {
            if (backendGeneration == generation && backend === expectedBackend) {
                backend = null
                backendGeneration = -1L
            }
        }
    }

    private fun onFinishRecording() {
        if(!isRecording) {
            throw IllegalStateException("Should not call onFinishRecording when not recording")
        }

        isRecording = false

        unfocusAudio()
        if(stopReason == null) {
            stopReason = StopReason.Manual
        }
        diagnostics?.event(DiagnosticEvent.RECORDING_STOPPED, mapOf(
            DiagnosticMetric.STOP_REASON to (stopReason?.ordinal ?: -1).toLong()))

        processing()

        modelJob = lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                recorderJob?.join()
                runModel()
            }
        }
    }
}

abstract class AudioRecognizer : RecordingSession()
