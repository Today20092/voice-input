package org.futo.voiceinput.proof

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import org.futo.voiceinput.MagnitudeState
import org.futo.voiceinput.ModelData
import org.futo.voiceinput.RecordingSession
import org.futo.voiceinput.ml.RunState
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.shared.RecognizerSession
import org.futo.voiceinput.shared.types.AudioRecognizerListener
import org.futo.voiceinput.shared.types.InferenceState
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.futo.voiceinput.shared.types.toWhisperString
import org.futo.voiceinput.shared.ui.MicrophoneDeviceState
import org.futo.voiceinput.shared.types.MagnitudeState as SharedMagnitude

/** Experimental view adapter. RecordingSession retains capture, model and finalizer ownership. */
class ForkRecognizerSession(
    context: Context,
    scope: LifecycleCoroutineScope,
    private val events: AudioRecognizerListener,
    language: Language?
) : RecognizerSession {
    // Kept visible to the experimental instrumentation harness for controlled PCM injection.
    private val session = object : RecordingSession() {
        override val context = context
        override val lifecycleScope = scope
        override fun cancelled() = events.cancelled()
        override fun finished(result: String) = events.deliver(result)
        override fun failed(error: Throwable) = events.modelLoadingFailed()
        override fun languageDetected(result: String) {
            getLanguageFromWhisperString(result)?.let(events::languageDetected)
        }
        override fun partialResult(result: String) = events.partialResult(result)
        override fun decodingStatus(status: RunState) {
            when (status) {
                RunState.Streaming -> Unit
                RunState.CatchingUp -> events.processing()
                RunState.OOMError -> events.modelLoadingFailed()
                else -> events.decodingStatus(when (status) {
                    RunState.ExtractingFeatures -> InferenceState.ExtractingMel
                    RunState.ProcessingEncoder -> InferenceState.Encoding
                    RunState.SwitchingModel -> InferenceState.SwitchingModel
                    else -> InferenceState.DecodingStarted
                })
            }
        }
        override fun loading() = events.loading()
        override fun needParakeetModelDownload() = events.modelLoadingFailed()
        override fun needRecognitionModelDownload(model: RecognitionModel) = events.modelLoadingFailed()
        override fun needMoonshineModelDownload() = events.modelLoadingFailed()
        override fun needWhisperModelDownload(models: List<ModelData>) = events.modelLoadingFailed()
        override fun needPermission() = events.needPermission { granted ->
            if (granted) permissionResultGranted() else permissionResultRejected()
        }
        override fun permissionRejected() = events.modelLoadingFailed()
        override fun recordingStarted() = events.recordingStarted(MicrophoneDeviceState(
            false, false, false, {}, ""))
        override fun updateWaveform(bars: List<Pair<Float, Float>>, state: MagnitudeState) {
            // shortcut: shared UI accepts one magnitude, retain the fork waveform before production adoption.
            events.updateMagnitude(bars.lastOrNull()?.let { maxOf(it.first, it.second) } ?: 0f,
                when (state) {
                    MagnitudeState.NOT_TALKED_YET -> SharedMagnitude.NOT_TALKED_YET
                    MagnitudeState.MIC_MAY_BE_BLOCKED -> SharedMagnitude.MIC_MAY_BE_BLOCKED
                    else -> SharedMagnitude.TALKING
                })
        }
        override fun processing() = events.processing()
        override fun cleaning() = events.processing()
        fun permissionSettings() = openPermissionSettings()
    }.apply { forceLanguage(language?.toWhisperString()) }

    internal val recording: RecordingSession get() = session

    override fun start() = recording.create()
    override fun finish() = recording.finishRecognizerIfRecording()
    override fun cancel() = recording.cancelRecognizer()
    override fun reset() = recording.reset()
    override fun inhibitAutoStop(inhibit: Boolean) = recording.pauseVAD(inhibit)
    override fun openPermissionSettings() = session.permissionSettings()
}
