package org.futo.voiceinput.moonshine

import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.backend.SpeechBackend
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class MoonshineBackendTest {
    @Test
    fun transcribesThroughSpeechBackendAndReleasesEngine() = runBlocking {
        val engine = FakeMoonshineEngine()
        val backend: SpeechBackend = MoonshineBackend(MoonshineModelVariant.Small, engine)
        val samples = floatArrayOf(0.1f, 0.2f)

        assertEquals("known words", backend.transcribe(samples))
        assertArrayEquals(samples, engine.samples, 0.0f)

        backend.close()
        assertTrue(engine.closed)
    }

    @Test
    fun streamsAudioPublishesPartialsAndFinalizes() = runBlocking {
        val engine = FakeMoonshineEngine()
        val backend = MoonshineBackend(MoonshineModelVariant.Small, engine)
        val partials = Collections.synchronizedList(mutableListOf<String>())

        backend.startStreaming(partials::add)
        backend.acceptAudio(floatArrayOf(0.1f, 0.2f))
        val final = backend.finishStreaming()

        assertEquals(listOf(0.1f, 0.2f), engine.streamedSamples)
        assertEquals(listOf("partial words", "final words"), partials)
        assertEquals("final words", final)
    }
}

private class FakeMoonshineEngine : MoonshineEngine {
    var samples = floatArrayOf()
    val streamedSamples = mutableListOf<Float>()
    var closed = false
    private var onTranscript: ((String, Boolean) -> Unit)? = null

    override fun transcribe(samples: FloatArray): String {
        this.samples = samples
        return "known words"
    }

    override fun start(onTranscript: (String, Boolean) -> Unit) {
        this.onTranscript = onTranscript
    }

    override fun addAudio(samples: FloatArray) {
        streamedSamples += samples.toList()
        onTranscript?.invoke("partial words", false)
    }

    override fun stop() {
        onTranscript?.invoke("final words", true)
    }
    override fun close() { closed = true }
}
