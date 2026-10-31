package org.futo.voiceinput.proof

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.lifecycleScope
import org.futo.voiceinput.EXTRA_DETECTED_LANGUAGE
import org.futo.voiceinput.successfulRecognitionActivityResult
import org.futo.voiceinput.shared.*
import org.futo.voiceinput.shared.ui.MicrophoneDeviceState
import org.futo.voiceinput.shared.whisper.DecodingConfiguration

/** Controlled PCM host, available only in the opt-in experimental debug source set. */
class SharedResultProofActivity : ComponentActivity() {
    internal lateinit var session: ForkRecognizerSession
    internal lateinit var view: RecognizerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = RecognizerView(this, object : RecognizerViewListener {
            override fun cancelled() { setResult(RESULT_CANCELED); finish() }
            override fun recordingStarted(device: MicrophoneDeviceState) {}
            override fun finished(result: String) { deliver(result) }
            override fun deliver(result: String): Boolean {
                val outcome = successfulRecognitionActivityResult(result, "en")
                setResult(outcome.resultCode, Intent().apply {
                    putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, ArrayList(outcome.transcripts))
                    putExtra(EXTRA_DETECTED_LANGUAGE, outcome.detectedLanguage)
                })
                finish()
                return true
            }
            override fun partialResult(result: String) {}
            override fun requestPermission(onGranted: () -> Unit, onRejected: () -> Unit) = false
            override fun openSettings() { finish() }
            override fun modelNotFound() { finish() }
        }, RecognizerViewSettings(false, true, true, false, false, null,
            DecodingConfiguration(emptyList(), emptySet(), false, 0),
            RecordingSettings(false, false, true, false)), lifecycleScope,
            sessionFactory = { events, language ->
                ForkRecognizerSession(this, lifecycleScope, events, language).also { session = it }
            })
        setContent { MaterialTheme { view.Content() } }
    }

    override fun onDestroy() {
        if (::session.isInitialized) session.reset()
        super.onDestroy()
    }
}
