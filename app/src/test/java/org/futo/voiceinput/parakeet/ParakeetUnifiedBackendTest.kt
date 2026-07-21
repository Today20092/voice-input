package org.futo.voiceinput.parakeet

import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.backend.StreamingSpeechBackend
import org.futo.voiceinput.nemotron.SherpaStreamingDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ParakeetUnifiedBackendTest {
    @Test
    fun streamsEveryChunkPublishesPartialsAndFinalizes() = runBlocking {
        val decoder = FakeUnifiedDecoder()
        val backend: StreamingSpeechBackend = parakeetUnifiedBackend(
            decoderFactory = { decoder },
            catchingUpSamples = 2,
            decoder = decoder
        )
        val partials = Collections.synchronizedList(mutableListOf<String>())
        val catchingUp = Collections.synchronizedList(mutableListOf<Boolean>())

        backend.startStreaming(partials::add, catchingUp::add)
        backend.acceptAudio(floatArrayOf(0.1f, 0.2f))
        backend.acceptAudio(floatArrayOf(0.3f, 0.4f))
        assertTrue(decoder.started.await(2, TimeUnit.SECONDS))
        assertTrue(catchingUp.contains(true))
        decoder.allowDecode.countDown()

        assertEquals("four words", backend.finishStreaming())
        assertEquals(listOf(0.1f, 0.2f, 0.3f, 0.4f), decoder.samples)
        assertEquals(listOf("two words", "four words"), partials)
        assertEquals(false, catchingUp.last())

        backend.close()
        assertTrue(decoder.closed)
    }
}

private class FakeUnifiedDecoder : SherpaStreamingDecoder {
    val samples = mutableListOf<Float>()
    val started = CountDownLatch(1)
    val allowDecode = CountDownLatch(1)
    var closed = false

    override fun acceptAudio(samples: FloatArray): String {
        started.countDown()
        allowDecode.await(2, TimeUnit.SECONDS)
        this.samples += samples.toList()
        return if (this.samples.size == 2) "two words" else "four words"
    }

    override fun finish() = "four words"

    override fun close() {
        closed = true
    }
}
