package org.futo.voiceinput.parakeet

import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.backend.SpeechBackend
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.futo.voiceinput.recognition.PerformanceClass
import org.futo.voiceinput.recognition.TranscriptionBehavior

class ParakeetBackendTest {
    @Test
    fun transcribesThroughSpeechBackendAndReleasesDecoder() = runBlocking {
        val decoder = FakeParakeetDecoder()
        val backend: SpeechBackend = ParakeetBackend(
            decoderFactory = { decoder },
            decoder = decoder
        )
        val samples = floatArrayOf(0.1f, 0.2f)

        assertEquals("known words", backend.transcribe(samples))
        assertArrayEquals(samples, decoder.samples, 0.0f)

        backend.close()
        assertTrue(decoder.closed)
    }

    @Test
    fun modelPackageIsPinnedForSherpaAndAttributed() {
        val model = ParakeetModel.recognitionModel

        assertEquals(
            listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"),
            model.artifacts.map { it.name }
        )
        assertTrue(model.artifacts.all { it.url.contains("/resolve/${model.version}/") })
        assertEquals("sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8", model.directoryName)
        assertTrue(model.source.contains("CC BY 4.0"))
        assertEquals(TranscriptionBehavior.FINAL_ONLY, model.transcription)
        assertEquals(PerformanceClass.DEMANDING, model.performanceClass)
    }
}

private class FakeParakeetDecoder : ParakeetDecoder {
    var samples = floatArrayOf()
    var closed = false

    override fun transcribe(samples: FloatArray): String {
        this.samples = samples
        return "known words"
    }

    override fun close() {
        closed = true
    }
}
