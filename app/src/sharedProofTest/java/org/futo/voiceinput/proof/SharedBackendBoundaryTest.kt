package org.futo.voiceinput.proof

import android.content.Context
import android.widget.EditText
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.futo.voiceinput.RecordingSession
import org.futo.voiceinput.ImeInsertionSession
import org.futo.voiceinput.InputConnectionInsertionEditor
import org.futo.voiceinput.backend.SpeechBackend
import org.futo.voiceinput.backend.StreamingSpeechBackend
import org.futo.voiceinput.recognition.RecognitionModelLifecycle
import org.futo.voiceinput.recognition.RecognitionModelSelection
import org.futo.voiceinput.recognition.RecognitionRuntimeCallbacks
import org.futo.voiceinput.settings.*
import org.futo.voiceinput.shared.*
import org.futo.voiceinput.shared.ui.MicrophoneDeviceState
import org.futo.voiceinput.shared.whisper.DecodingConfiguration
import org.junit.Assert.*
import org.junit.Test
import java.nio.FloatBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SharedBackendBoundaryTest {
    @Test fun finalOnlyAndStreamingUseSharedViewAndForkFinalization() = runBlocking {
        exercise(streaming = false, cancel = false)
        exercise(streaming = true, cancel = false)
    }

    @Test fun cancellationSuppressesPendingFinalAcrossSharedView() = runBlocking {
        exercise(streaming = true, cancel = true)
    }

    @Test fun includedAsr4allAndInstalledOrukeetUseTheirActualJni() = runBlocking {
        exercise(streaming = true, cancel = false, nativeRuntime = "asr4all")
        exercise(streaming = false, cancel = false, nativeRuntime = "orukeet")
    }

    @Test fun sharedFinalReplacesCompositionInANativeEditor() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var editor: EditText
        lateinit var insertion: ImeInsertionSession
        instrumentation.runOnMainSync {
            editor = EditText(instrumentation.targetContext).apply {
                setText("hello old world")
                setSelection(6, 9)
            }
            insertion = ImeInsertionSession(InputConnectionInsertionEditor(
                checkNotNull(editor.onCreateInputConnection(EditorInfo()))) { true })
            assertTrue(insertion.partial("draft"))
        }
        exercise(streaming = false, cancel = false, delivery = { insertion.finish(it, "en") })
        instrumentation.runOnMainSync {
            assertEquals("hello boundary final world", editor.text.toString())
            assertFalse(insertion.finish("duplicate", "en"))
        }
    }

    private suspend fun exercise(streaming: Boolean, cancel: Boolean, nativeRuntime: String? = null,
        delivery: ((String) -> Boolean)? = null) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val saved = context.dataStore.data.first()
        val entered = CompletableDeferred<Unit>()
        val proceed = CompletableDeferred<Unit>()
        val samples = if (nativeRuntime == null) expectedSamples else {
            val bytes = instrumentation.context.assets.open("jfk.wav").use { it.readBytes() }
            val pcm = ByteBuffer.wrap(bytes, 78, bytes.size - 78).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            FloatArray(pcm.remaining()) { pcm.get() / 32768f }
        }
        val base = object : SpeechBackend {
            override suspend fun load(context: Context) = error("must not reload")
            override suspend fun transcribe(samples: FloatArray): String {
                assertArrayEquals(expectedSamples, samples, 0f)
                return "boundary final"
            }
            override suspend fun close() {}
        }
        val backend = if (!streaming) base else object : StreamingSpeechBackend, SpeechBackend by base {
            override fun startStreaming(onPartial: (String) -> Unit, onCatchingUp: (Boolean) -> Unit) {}
            override fun acceptAudio(samples: FloatArray) {}
            override suspend fun finishStreaming(): String {
                entered.complete(Unit)
                if (cancel) proceed.await()
                return "boundary final"
            }
        }
        lateinit var owner: LifecycleOwner
        lateinit var registry: LifecycleRegistry
        lateinit var adapter: ForkRecognizerSession
        lateinit var view: RecognizerView
        lateinit var events: org.futo.voiceinput.shared.types.AudioRecognizerListener
        val results = mutableListOf<String>()
        var cancellations = 0
        var partials = 0
        var failure = false
        var initialized = false
        try {
            context.setSetting(SPEECH_BACKEND, nativeRuntime ?: "orukeet")
            context.setSetting(ASR4ALL_VARIANT, "m")
            context.setSetting(S1_MINI_ENABLED, false)
            context.setSetting(HARPER_ENABLED, false)
            context.setSetting(S1_MINI_TRANSCRIPT_DIAGNOSTICS, false)
            context.setSetting(PERSONAL_DICTIONARY, "")
            context.setSetting(PARAKEET_KEEP_WARM, false)
            instrumentation.runOnMainSync {
                owner = object : LifecycleOwner {
                    override val lifecycle: Lifecycle get() = registry
                }
                registry = LifecycleRegistry(owner)
                registry.currentState = Lifecycle.State.RESUMED
                view = RecognizerView(context, object : RecognizerViewListener {
                    override fun cancelled() { cancellations++ }
                    override fun recordingStarted(device: MicrophoneDeviceState) {}
                    override fun finished(result: String) { results += result }
                    override fun deliver(result: String): Boolean {
                        results += result
                        return delivery?.invoke(result) ?: true
                    }
                    override fun partialResult(result: String) { if (result.isNotBlank()) partials++ }
                    override fun requestPermission(onGranted: () -> Unit, onRejected: () -> Unit) = false
                    override fun openSettings() { failure = true }
                    override fun modelNotFound() { failure = true }
                }, RecognizerViewSettings(false, true, false, false, false,
                    null,
                    DecodingConfiguration(emptyList(), emptySet(), false, 0),
                    RecordingSettings(false, false, true, false)), owner.lifecycleScope,
                    sessionFactory = { listener, language ->
                        events = listener
                        ForkRecognizerSession(context, owner.lifecycleScope, listener, language).also { adapter = it }
                    })
                initialized = true
            }
            val activeBackend = if (nativeRuntime == null) backend else {
                val lifecycle = RecognitionModelLifecycle.create(context.filesDir, false)
                val selection = RecognitionModelSelection(runtimeId = nativeRuntime, asr4allVariantId = "m")
                check(lifecycle.readiness(selection)?.isReady == true) { "Install required proof model: $nativeRuntime" }
                withTimeout(180_000) {
                    lifecycle.load(context, selection, RecognitionRuntimeCallbacks(
                        onStatusUpdate = {}, onPartialDecode = events::partialResult,
                        forceLanguageProvider = { "en" }))
                }
            }
            if (nativeRuntime != null && streaming) {
                check(activeBackend is StreamingSpeechBackend)
                activeBackend.startStreaming(events::partialResult)
                samples.asList().chunked(1600).forEach { activeBackend.acceptAudio(it.toFloatArray()) }
            }
            instrumentation.runOnMainSync {
                setField(adapter.recording, "backend", activeBackend)
                setField(adapter.recording, "backendGeneration", 0L)
                setField(adapter.recording, "isRecording", true)
                setField(adapter.recording, "floatSamples", FloatBuffer.allocate(samples.size).apply { put(samples) })
                view.finish()
            }
            val job = field(adapter.recording, "modelJob") as Job
            if (cancel) {
                withTimeout(10_000) { entered.await() }
                instrumentation.runOnMainSync { view.cancel() }
                proceed.complete(Unit)
            }
            withTimeout(if (nativeRuntime == null) 10_000L else 180_000L) { job.join() }
            if (nativeRuntime == null) {
                assertEquals(if (cancel) emptyList<String>() else listOf("boundary final"), results)
            } else {
                assertEquals(1, results.size)
                assertTrue(results.single().lowercase().contains("ask not what your country can do for you"))
                if (streaming) assertTrue("Live backend must emit partial text", partials > 0)
            }
            assertEquals(if (cancel) 1 else 0, cancellations)
            assertFalse(failure)
        } finally {
            if (initialized) instrumentation.runOnMainSync {
                adapter.reset()
                registry.currentState = Lifecycle.State.DESTROYED
            }
            context.dataStore.updateData { saved }
        }
    }

    private val expectedSamples = floatArrayOf(0.1f, -0.2f, 0.3f)
    private fun field(session: RecordingSession, name: String): Any? =
        RecordingSession::class.java.getDeclaredField(name).apply { isAccessible = true }.get(session)
    private fun setField(session: RecordingSession, name: String, value: Any) =
        RecordingSession::class.java.getDeclaredField(name).apply { isAccessible = true }.set(session, value)
}
