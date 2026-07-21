package org.futo.voiceinput.nemotron

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.backend.StreamingSpeechBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.Collections

class NemotronBackendTest {
    @Test
    fun streamsEveryChunkPublishesPartialsAndFinalizes() = runBlocking {
        val decoder = FakeNemotronDecoder()
        val backend: StreamingSpeechBackend = NemotronBackend(
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
        val final = backend.finishStreaming()

        assertEquals(listOf(0.1f, 0.2f, 0.3f, 0.4f), decoder.samples)
        assertEquals(listOf("two words", "four words"), partials)
        assertEquals("four words", final)
        assertEquals(false, catchingUp.last())
        backend.close()
        assertTrue(decoder.closed)
    }

    @Test
    fun surfacesDecoderErrors() = runBlocking {
        val decoder = ErrorNemotronDecoder()
        val backend: StreamingSpeechBackend = NemotronBackend(decoderFactory = { decoder }, decoder = decoder)

        backend.startStreaming({}, {})
        backend.acceptAudio(floatArrayOf(0.1f))
        val error = runCatching { backend.finishStreaming() }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("decode failed", error?.message)
        backend.close()
        assertTrue(decoder.closed)
    }

    @Test
    fun closeCancelsQueuedAudioAndReleasesDecoder() = runBlocking {
        val decoder = BlockingNemotronDecoder()
        val backend: StreamingSpeechBackend = NemotronBackend(decoderFactory = { decoder }, decoder = decoder)

        backend.startStreaming({}, {})
        backend.acceptAudio(floatArrayOf(0.1f))
        backend.acceptAudio(floatArrayOf(0.2f))
        assertTrue(decoder.started.await(2, TimeUnit.SECONDS))
        val closing = async(Dispatchers.Default) { backend.close() }
        delay(20)
        decoder.allowDecode.countDown()
        closing.await()

        assertEquals(1, decoder.calls)
        assertTrue(decoder.closed)
    }
}

private class FakeNemotronDecoder : NemotronDecoder {
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

    override fun finish(): String = "four words"

    override fun close() {
        closed = true
    }
}

private class ErrorNemotronDecoder : NemotronDecoder {
    var closed = false

    override fun acceptAudio(samples: FloatArray): String = error("decode failed")
    override fun finish(): String = error("finish should not run")
    override fun close() { closed = true }
}

private class BlockingNemotronDecoder : NemotronDecoder {
    val started = CountDownLatch(1)
    val allowDecode = CountDownLatch(1)
    var calls = 0
    var closed = false

    override fun acceptAudio(samples: FloatArray): String {
        calls += 1
        started.countDown()
        allowDecode.await(2, TimeUnit.SECONDS)
        return "partial"
    }

    override fun finish(): String = "final"
    override fun close() { closed = true }
}
