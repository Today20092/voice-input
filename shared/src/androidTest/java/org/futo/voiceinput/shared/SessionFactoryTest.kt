package org.futo.voiceinput.shared

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.test.platform.app.InstrumentationRegistry
import org.futo.voiceinput.shared.types.AudioRecognizerListener
import org.futo.voiceinput.shared.ui.MicrophoneDeviceState
import org.futo.voiceinput.shared.whisper.DecodingConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionFactoryTest {
    @Test fun injectedSessionHandlesLifecycleWithoutNativeRuntimeOrModelManager() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val owner = object : LifecycleOwner {
                val registry = LifecycleRegistry(this)
                override val lifecycle: Lifecycle get() = registry
            }
            owner.registry.currentState = Lifecycle.State.RESUMED
            var factories = 0
            var callbacks: AudioRecognizerListener? = null
            val calls = mutableListOf<String>()
            val results = mutableListOf<String>()
            val session = object : RecognizerSession {
                override fun start() { calls += "start" }
                override fun finish() { calls += "finish"; callbacks!!.finished("controlled result") }
                override fun cancel() { calls += "cancel"; callbacks!!.cancelled() }
                override fun reset() { calls += "reset" }
                override fun inhibitAutoStop(inhibit: Boolean) { calls += "inhibit:$inhibit" }
                override fun openPermissionSettings() { calls += "permission" }
            }
            val listener = object : RecognizerViewListener {
                override fun cancelled() { calls += "cancelled" }
                override fun recordingStarted(device: MicrophoneDeviceState) {}
                override fun finished(result: String) { results += result }
                override fun deliver(result: String): Boolean { results += "rejected:$result"; return false }
                override fun partialResult(result: String) { results += "partial:$result" }
                override fun requestPermission(onGranted: () -> Unit, onRejected: () -> Unit) = false
                override fun openSettings() { error("unexpected settings") }
                override fun modelNotFound() { error("unexpected catalog access") }
            }
            try {
                val view = RecognizerView(instrumentation.targetContext, listener,
                    RecognizerViewSettings(false, true, true, false, false,
                        null,
                        DecodingConfiguration(emptyList(), emptySet(), false, 0),
                        RecordingSettings(false, false, true, false)), owner.lifecycleScope,
                    sessionFactory = { events, _ -> factories++; callbacks = events; session })
                assertEquals(1, factories)
                assertFalse(view.isFinishable.value)
                view.start()
                callbacks!!.partialResult("controlled partial")
                view.inhibitAutoStop(true)
                view.finish()
                view.reset()
                view.start()
                view.cancel()
                assertEquals(listOf("start", "inhibit:true", "finish", "reset", "start", "cancel", "cancelled"), calls)
                assertEquals(listOf("partial:controlled partial", "controlled result"), results)
                assertFalse(callbacks!!.deliver("handoff"))
                assertEquals("rejected:handoff", results.last())
                assertEquals(1, factories)
            } finally {
                owner.registry.currentState = Lifecycle.State.DESTROYED
            }
        }
    }
}
