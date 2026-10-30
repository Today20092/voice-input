package org.futo.voiceinput

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.ggml.DecodingMode
import org.futo.voiceinput.ml.loadGGMLModel
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class LegacyWhisperSmokeTest {
    @Test fun bundledFallbackStillTranscribesAfterUpstreamIntegration() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bytes = instrumentation.context.assets.open("jfk.wav").use { it.readBytes() }
        // This fixture's PCM data starts after its 78-byte WAV header.
        val pcm = ByteBuffer.wrap(bytes, 78, bytes.size - 78)
            .order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = FloatArray(pcm.remaining()) { pcm.get() / 32768f }
        val model = loadGGMLModel(context, ENGLISH_MODELS.first()) {}
        try {
            val transcript = model.infer(samples, "", arrayOf("en"), emptyArray(),
                DecodingMode.Greedy, suppressNonSpeechTokens = true).lowercase()
            assertTrue(transcript.contains("ask not what your country can do for you"))
            assertTrue(transcript.contains("what you can do for your country"))
        } finally {
            model.close()
        }
    }
}
