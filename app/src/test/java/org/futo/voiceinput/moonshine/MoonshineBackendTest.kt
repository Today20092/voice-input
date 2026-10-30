package org.futo.voiceinput.moonshine

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.withTimeout
import org.futo.voiceinput.backend.SpeechBackend
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MoonshineBackendTest {
    @Test
    fun closeWaitsForFullClipTranscription() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val engine = object : MoonshineEngine {
            override fun transcribe(samples: FloatArray): String {
                entered.complete(Unit)
                check(release.await(5, TimeUnit.SECONDS))
                return "complete"
            }
            override fun start(onTranscript: (String, Boolean) -> Unit) {}
            override fun addAudio(samples: FloatArray) {}
            override fun stop() {}
            override fun close() { closed.countDown() }
        }
        val backend = MoonshineBackend(MoonshineModelVariant.Small, engine)
        val transcription = launch { assertEquals("complete", backend.transcribe(floatArrayOf(1f))) }
        try {
            withTimeout(5_000) { entered.await() }
            val closing = launch(start = CoroutineStart.UNDISPATCHED) { backend.close() }
            assertFalse("native release raced transcription", closed.await(200, TimeUnit.MILLISECONDS))
            release.countDown()
            withTimeout(5_000) { transcription.join(); closing.join() }
            assertEquals(0L, closed.count)
        } finally {
            release.countDown()
            backend.close()
        }
    }

    @Test
    fun cancellationAndOutOfMemoryKeepTheirOriginalIdentity() = runBlocking {
        for (failure in listOf(CancellationException("cancelled"), OutOfMemoryError("test only"))) {
            val backend = MoonshineBackend(MoonshineModelVariant.Small, FakeMoonshineEngine(failure))
            try {
                backend.startStreaming({})
                backend.acceptAudio(floatArrayOf(1f))
                assertOriginalFailure(failure, runCatching { backend.finishStreaming() }.exceptionOrNull())
            } finally {
                backend.close()
            }
        }
    }

    @Test
    fun failedStopCannotReturnPartialAsFinal() = runBlocking {
        val failure = IllegalStateException("stop failed")
        val engine = FakeMoonshineEngine(stopFailure = failure)
        val backend = MoonshineBackend(MoonshineModelVariant.Small, engine)
        try {
            backend.startStreaming({})
            backend.acceptAudio(floatArrayOf(1f))
            assertOriginalFailure(failure, runCatching { backend.finishStreaming() }.exceptionOrNull())
        } finally {
            backend.close()
        }
        assertTrue(engine.closed)
    }

    @Test
    fun closeWaitsForNativeFeedAndDiscardsQueuedAudio() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val releaseFeed = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<String>())
        val engine = object : MoonshineEngine {
            override fun transcribe(samples: FloatArray) = "unused"
            override fun start(onTranscript: (String, Boolean) -> Unit) {}
            override fun addAudio(samples: FloatArray) {
                events += "feed"
                entered.complete(Unit)
                check(releaseFeed.await(5, TimeUnit.SECONDS))
                events += "feed returned"
            }
            override fun stop() { events += "stop" }
            override fun close() { events += "close" }
        }
        val backend = MoonshineBackend(MoonshineModelVariant.Small, engine)
        backend.startStreaming({})
        backend.acceptAudio(floatArrayOf(1f))
        try {
            withTimeout(5_000) { entered.await() }
            backend.acceptAudio(floatArrayOf(2f))
            val closing = launch(start = CoroutineStart.UNDISPATCHED) { backend.close() }
            assertFalse(events.contains("close"))
            releaseFeed.countDown()
            withTimeout(5_000) { closing.join() }
            assertEquals(listOf("feed", "feed returned", "stop", "close"), events)
        } finally {
            releaseFeed.countDown()
            backend.close()
        }
        val fresh = MoonshineBackend(MoonshineModelVariant.Small, FakeMoonshineEngine())
        try {
            fresh.startStreaming({})
            fresh.acceptAudio(floatArrayOf(3f))
            assertEquals("final words", fresh.finishStreaming())
        } finally {
            fresh.close()
        }
    }

    @Test
    fun cancelledCallerStillReleasesStreamingEngine() = runBlocking {
        val engine = FakeMoonshineEngine()
        val backend = MoonshineBackend(MoonshineModelVariant.Small, engine)
        backend.startStreaming({})
        launch {
            currentCoroutineContext().cancel()
            backend.close()
        }.join()
        assertTrue(engine.closed)
        backend.close()
        assertEquals(1, engine.closeCount)
    }

    @Test
    fun rejectedFeedReportsFailureInsteadOfDroppingAudio() = runBlocking {
        val failure = IllegalStateException("feed failed")
        val backend = MoonshineBackend(MoonshineModelVariant.Small, FakeMoonshineEngine(failure))
        try {
            assertTrue(runCatching { backend.acceptAudio(floatArrayOf(1f)) }.isFailure)
            backend.startStreaming({})
            backend.acceptAudio(floatArrayOf(1f))
            assertOriginalFailure(failure, runCatching { backend.finishStreaming() }.exceptionOrNull())
            assertOriginalFailure(failure, runCatching { backend.acceptAudio(floatArrayOf(2f)) }.exceptionOrNull())
        } finally {
            backend.close()
        }
        assertTrue(runCatching { backend.acceptAudio(floatArrayOf(3f)) }.isFailure)
    }

    @Test
    fun failedFeedCannotReturnPartialAsFinal() = runBlocking {
        val failure = IllegalStateException("feed failed")
        val engine = FakeMoonshineEngine(feedFailure = failure)
        val backend = MoonshineBackend(MoonshineModelVariant.Small, engine)
        try {
            backend.startStreaming({})
            backend.acceptAudio(floatArrayOf(0.1f))
            val result = runCatching { backend.finishStreaming() }
            assertOriginalFailure(failure, result.exceptionOrNull())
        } finally {
            backend.close()
        }
    }

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
        backend.close()
    }
}

// Coroutine debug mode may copy an exception to recover its stack, retaining the
// original as its cause. Require both the original failure and unchanged type.
private fun assertOriginalFailure(expected: Throwable, actual: Throwable?) {
    assertEquals(expected.javaClass, actual?.javaClass)
    assertTrue(generateSequence(actual) { it.cause }.any { it === expected })
}

private class FakeMoonshineEngine(
    private val feedFailure: Throwable? = null,
    private val stopFailure: Throwable? = null
) : MoonshineEngine {
    var samples = floatArrayOf()
    val streamedSamples = mutableListOf<Float>()
    var closed = false
    var closeCount = 0
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
        feedFailure?.let { throw it }
    }

    override fun stop() {
        stopFailure?.let { throw it }
        onTranscript?.invoke("final words", true)
    }
    override fun close() { closed = true; closeCount++ }
}
