package org.futo.voiceinput

import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.backend.SpeechBackend
import org.futo.voiceinput.ggml.DecodingMode
import org.futo.voiceinput.ml.WhisperEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperGGMLBackendTest {
    @Test
    fun transcribesThroughSpeechBackendAndReleasesEngine() = runBlocking {
        val engine = FakeWhisperEngine()
        val backend: SpeechBackend = WhisperGGMLBackend({}, {}, { "en" }, engine = engine)
        val samples = floatArrayOf(0.1f, 0.2f)

        assertEquals("known words", backend.transcribe(samples))
        assertArrayEquals(samples, engine.samples, 0.0f)
        assertEquals("en", engine.language)
        assertEquals(DecodingMode.BeamSearch5, engine.decodingMode)

        backend.close()
        assertTrue(engine.closed)
    }
}

private class FakeWhisperEngine : WhisperEngine {
    var samples = floatArrayOf()
    var language: String? = null
    var decodingMode: DecodingMode? = null
    var closed = false

    override suspend fun run(
        samples: FloatArray,
        glossary: String,
        forceLanguage: String?,
        decodingMode: DecodingMode
    ): String {
        this.samples = samples
        language = forceLanguage
        this.decodingMode = decodingMode
        return "known words"
    }

    override suspend fun close() { closed = true }
}
