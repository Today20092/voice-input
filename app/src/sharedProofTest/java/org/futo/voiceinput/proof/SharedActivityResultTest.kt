package org.futo.voiceinput.proof

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.activity.result.ActivityResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.RecordingSession
import org.futo.voiceinput.backend.SpeechBackend
import org.futo.voiceinput.settings.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.FloatBuffer

class SharedActivityResultTest {
    @Test(timeout = 30_000) fun sharedViewReturnsOneAndroidActivityResult() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val saved = context.dataStore.data.first()
        val resultReceived = CompletableDeferred<ActivityResult>()
        val monitor = instrumentation.addMonitor(SharedResultProofActivity::class.java.name, null, false)
        var host: SharedResultProofActivity? = null
        var caller: Activity? = null
        try {
            context.setSetting(SPEECH_BACKEND, "orukeet")
            context.setSetting(S1_MINI_ENABLED, false)
            context.setSetting(HARPER_ENABLED, false)
            context.setSetting(S1_MINI_TRANSCRIPT_DIAGNOSTICS, false)
            context.setSetting(PERSONAL_DICTIONARY, "")
            context.setSetting(PARAKEET_KEEP_WARM, false)
            SharedResultCallerActivity.onResult = { resultReceived.complete(it) }
            caller = instrumentation.startActivitySync(Intent(context, SharedResultCallerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            host = monitor.waitForActivityWithTimeout(10_000) as? SharedResultProofActivity
            val activeHost = checkNotNull(host) { "Experimental result host did not launch" }
            instrumentation.runOnMainSync {
                    val backend = object : SpeechBackend {
                        override suspend fun load(context: Context) = error("Unexpected load")
                        override suspend fun transcribe(samples: FloatArray) = "controlled activity result"
                        override suspend fun close() {}
                    }
                    fun attach(name: String, value: Any) = RecordingSession::class.java
                        .getDeclaredField(name).apply { isAccessible = true }.set(activeHost.session.recording, value)
                    attach("backend", backend)
                    attach("backendGeneration", 0L)
                    attach("isRecording", true)
                    attach("floatSamples", FloatBuffer.allocate(3).apply { put(floatArrayOf(.1f, .2f, .3f)) })
                    activeHost.view.finish()
                }
                val result = withTimeout(10_000) { resultReceived.await() }
                assertEquals(Activity.RESULT_OK, result.resultCode)
                assertEquals(listOf("controlled activity result"),
                    result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS))
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { host?.finish(); caller?.finish() }
            SharedResultCallerActivity.onResult = null
            context.dataStore.updateData { saved }
        }
    }
}
