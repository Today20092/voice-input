package org.futo.voiceinput.experiment

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PublicParakeetBackendTest {
    @Test fun canceledLoadReleasesItsSessionAndInvalidModelNeverEntersNativeCode() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var frees = 0
        val backend = PublicParakeetBackend {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            object : PublicParakeetSession {
                override fun reset() = Unit
                override fun transcribe(samples: FloatArray) = "unused"
                override fun cancel() = Unit
                override fun close() { frees++ }
            }
        }
        val loading = launch(Dispatchers.Default) { backend.loadForExperiment() }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            loading.cancel()
        } finally {
            release.countDown()
            loading.join()
            backend.close()
        }
        assertEquals(1, frees)
        val invalid = java.io.File.createTempFile("share09-invalid", ".gguf")
        try {
            for (language in listOf("en", "fr")) {
                val candidate = PublicParakeetBackend(invalid, language)
                try { candidate.loadForExperiment(); fail("Invalid model accepted") }
                catch (_: IllegalArgumentException) { }
                finally { candidate.close() }
            }
        } finally { invalid.delete() }
    }

    @Test fun serialRunsPreserveCompleteAudioAndFailuresDoNotBecomeText() = runBlocking {
        var opens = 0
        var frees = 0
        var resets = 0
        val original = FloatArray(16000 * 121) { if (it % 2 == 0) 0.25f else -0.25f }
        val backend = PublicParakeetBackend {
            opens++
            object : PublicParakeetSession {
                override fun reset() { resets++ }
                override fun transcribe(samples: FloatArray): String {
                    assertArrayEquals(original, samples, 0f)
                    if (resets == 2) error("incomplete output")
                    return "final text"
                }
                override fun cancel() = Unit
                override fun close() { frees++ }
            }
        }
        backend.loadForExperiment()
        backend.loadForExperiment()
        assertEquals("final text", backend.transcribe(original))
        try { backend.transcribe(original); fail("Failure was swallowed") }
        catch (expected: IllegalStateException) { assertEquals("incomplete output", expected.message) }
        assertEquals("final text", backend.transcribe(original))
        for (invalid in listOf(floatArrayOf(), floatArrayOf(Float.NaN), floatArrayOf(1.1f))) {
            try { backend.transcribe(invalid); fail("Invalid PCM accepted") }
            catch (_: IllegalArgumentException) { }
        }
        backend.close()
        backend.close()
        assertEquals(1, opens)
        assertEquals(1, frees)
        assertEquals(3, resets)
        try { backend.loadForExperiment(); fail("Closed backend reopened") }
        catch (_: IllegalStateException) { }
    }

    @Test fun cancellationWaitsForWorkerBeforeCloseAndNeverDeliversText() = runBlocking {
        val entered = CountDownLatch(1)
        val aborted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = AtomicBoolean()
        val delivered = AtomicBoolean()
        val backend = PublicParakeetBackend {
            object : PublicParakeetSession {
                override fun reset() = Unit
                override fun transcribe(samples: FloatArray): String {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    check(!closed.get())
                    return "must not be delivered"
                }
                override fun cancel() { aborted.countDown() }
                override fun close() { closed.set(true) }
            }
        }
        backend.loadForExperiment()
        val run = launch(Dispatchers.Default) {
            backend.transcribe(floatArrayOf(0.1f))
            delivered.set(true)
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            run.cancel()
            assertTrue(aborted.await(5, TimeUnit.SECONDS))
            val closing = async(Dispatchers.Default) { backend.close() }
            assertFalse(closed.get())
            release.countDown()
            withTimeout(5000) { run.join(); closing.await() }
            assertTrue(closed.get())
            assertFalse(delivered.get())
        } finally {
            release.countDown()
            run.cancelAndJoin()
            backend.close()
        }
    }
}
