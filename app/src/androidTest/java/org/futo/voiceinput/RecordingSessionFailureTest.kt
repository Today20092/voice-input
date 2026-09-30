package org.futo.voiceinput

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.futo.voiceinput.backend.StreamingSpeechBackend
import org.futo.voiceinput.ml.RunState
import org.futo.voiceinput.recognition.RecognitionModel
import org.junit.Assert.*
import org.junit.Test
import java.nio.FloatBuffer

class RecordingSessionFailureTest {
    @Test fun loadingOomIsTerminalWithoutReload() = runBlocking {
        val failure = OutOfMemoryError("injected model load")
        val session = newSession()
        var loads = 0
        try {
            withTimeout(10_000) {
                session.loadModelInner(loadBackend = { _, _, _ -> loads++; throw failure })
            }
            assertEquals(1, loads)
            assertEquals(listOf(failure), session.failures)
            assertEquals(0, session.finals)
        } finally {
            session.lifecycleScope.cancel()
        }
    }

    @Test fun stopDuringPendingLoadDoesNotReloadAfterTerminalOom() = runBlocking {
        val failure = OutOfMemoryError("injected pending load")
        val release = CompletableDeferred<Unit>()
        val session = newSession()
        var loads = 0
        try {
            session.setField("isRecording", true)
            val loading = session.lifecycleScope.async(Dispatchers.Default) {
                session.loadModelInner(loadBackend = { _, _, _ ->
                    loads++
                    release.await()
                    throw failure
                })
            }
            session.setField("loadModelJob", loading)
            val decoding = session.beginFinishing()
            release.complete(Unit)
            withTimeout(10_000) { decoding.join() }
            assertEquals(1, loads)
            assertEquals(listOf(failure), session.failures)
            assertTrue(decoding.isCancelled)
            assertEquals(0, session.finals)
        } finally {
            session.lifecycleScope.cancel()
        }
    }

    @Test fun stopDuringSuccessfulPendingLoadDeliversOneFinal() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val backend = FailingBackend(null)
        val session = newSession()
        try {
            session.setField("isRecording", true)
            val loading = session.lifecycleScope.async(Dispatchers.Default) {
                session.loadModelInner(loadBackend = { _, _, _ -> release.await(); backend })
            }
            session.setField("loadModelJob", loading)
            val decoding = session.beginFinishing()
            release.complete(Unit)
            withTimeout(10_000) { decoding.join() }
            assertFalse(decoding.isCancelled)
            assertTrue(session.failures.isEmpty())
            assertEquals(1, session.finals)
            assertEquals("known final", session.finalText)
            assertEquals(1, backend.finishes)
            assertEquals(1, backend.closes)
        } finally {
            session.lifecycleScope.cancel()
        }
    }

    @Test fun decodingOomRetainsAudioAndReportsOriginalFailureWithoutReload() = runBlocking {
        val failure = OutOfMemoryError("injected")
        val backend = FailingBackend(failure)
        val session = newSession()
        try {
            session.attach(backend)
            withTimeout(10_000) { session.decode() }
            assertEquals(listOf(failure), session.failures)
            assertEquals(1, backend.finishes)
            assertEquals(1, backend.closes)
            assertEquals(0, session.finals)
            assertArrayEquals(floatArrayOf(0.1f, 0.2f, 0.3f), session.samples(), 0f)
        } finally {
            session.lifecycleScope.cancel()
        }
    }

    @Test fun failedRetirementDoesNotReplaceTheOriginalOom() = runBlocking {
        val failure = OutOfMemoryError("injected")
        val closeFailure = IllegalStateException("injected retirement failure")
        val backend = FailingBackend(failure, closeFailure)
        val session = newSession()
        try {
            session.attach(backend)
            withTimeout(10_000) { session.decode() }
            assertEquals(listOf(failure), session.failures)
            assertTrue(failure.suppressed.contains(closeFailure))
            assertEquals(1, backend.finishes)
            assertEquals(0, session.finals)
        } finally {
            session.lifecycleScope.cancel()
        }
    }

    @Test fun cancelDuringFinalizationSuppressesFailureAndAllowsANewSession() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val backend = FailingBackend(OutOfMemoryError("injected"), entered = entered)
        val session = newSession()
        try {
            session.attach(backend)
            val decoding = session.beginFinishing()
            withTimeout(10_000) { entered.await() }
            session.cancelRecognizer()
            withTimeout(10_000) { backend.closed.await() }
            assertTrue(decoding.isCancelled)
            assertTrue(session.failures.isEmpty())
            assertEquals(0, session.finals)
            assertEquals(1, session.cancellations)

            val nextFailure = OutOfMemoryError("new session")
            val next = FailingBackend(nextFailure)
            session.attach(next, generation = 1L)
            withTimeout(10_000) { session.decode() }
            assertEquals(listOf(nextFailure), session.failures)
            assertEquals(1, next.finishes)
            assertEquals(1, next.closes)
        } finally {
            session.lifecycleScope.cancel()
        }
    }

    private class FailingBackend(
        private val failure: Throwable?,
        private val closeFailure: Throwable? = null,
        private val entered: CompletableDeferred<Unit>? = null
    ) : StreamingSpeechBackend {
        var finishes = 0
        var closes = 0
        val closed = CompletableDeferred<Unit>()
        override fun startStreaming(onPartial: (String) -> Unit, onCatchingUp: (Boolean) -> Unit) = Unit
        override fun acceptAudio(samples: FloatArray) = Unit
        override suspend fun load(context: Context) = error("Unexpected model reload")
        override suspend fun transcribe(samples: FloatArray): String = error("Unexpected replay")
        override suspend fun finishStreaming(): String {
            finishes++
            entered?.let { it.complete(Unit); CompletableDeferred<Unit>().await() }
            failure?.let { throw it }
            return "known final"
        }
        override suspend fun close() {
            closes++
            closed.complete(Unit)
            if (closes == 1) closeFailure?.let { throw it }
        }
    }

    private fun newSession(): TestSession {
        lateinit var session: TestSession
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val owner = object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry(this)
            }
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
            session = TestSession(owner.lifecycleScope)
        }
        return session
    }

    private class TestSession(public override val lifecycleScope: LifecycleCoroutineScope) : RecordingSession() {
        override val context = InstrumentationRegistry.getInstrumentation().targetContext
        val failures = mutableListOf<Throwable>()
        var finals = 0
        var finalText: String? = null
        var cancellations = 0
        fun attach(backend: StreamingSpeechBackend, generation: Long = 0L) {
            setField("backend", backend)
            setField("backendGeneration", generation)
            setField("isRecording", true)
            setField("stopReason", null)
            setField("floatSamples", FloatBuffer.allocate(3).apply { put(floatArrayOf(0.1f, 0.2f, 0.3f)) })
        }
        fun setField(name: String, value: Any?) {
            RecordingSession::class.java.getDeclaredField(name).apply { isAccessible = true }.set(this, value)
        }
        fun samples(): FloatArray = (RecordingSession::class.java.getDeclaredField("floatSamples")
            .apply { isAccessible = true }.get(this) as FloatBuffer).let {
            it.array().copyOf(it.position())
        }
        fun beginFinishing(): Job {
            finishRecognizerIfRecording()
            return RecordingSession::class.java.getDeclaredField("modelJob")
                .apply { isAccessible = true }.get(this) as Job
        }
        suspend fun decode() = beginFinishing().join()
        override fun cancelled() { cancellations++ }
        override fun finished(result: String) { finals++; finalText = result }
        override fun failed(error: Throwable) { failures += error }
        override fun languageDetected(result: String) = Unit
        override fun partialResult(result: String) = Unit
        override fun decodingStatus(status: RunState) = Unit
        override fun loading() = Unit
        override fun needParakeetModelDownload() = Unit
        override fun needRecognitionModelDownload(model: RecognitionModel) = Unit
        override fun needMoonshineModelDownload() = Unit
        override fun needWhisperModelDownload(models: List<ModelData>) = Unit
        override fun needPermission() = Unit
        override fun permissionRejected() = Unit
        override fun recordingStarted() = Unit
        override fun updateWaveform(bars: List<Pair<Float, Float>>, state: MagnitudeState) = Unit
        override fun processing() = Unit
        override fun cleaning() = Unit
    }
}
