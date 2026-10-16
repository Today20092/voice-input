package org.futo.voiceinput.benchmark

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BenchmarkRunnerTest {
    private val clip = BenchmarkClip("test", "original", "canonical", 2, "hello", "reference")

    @Test fun partialRunMedianAveragesTheMiddlePair() {
        assertEquals(25L, listOf(40L, 10L, 20L, 30L).medianNs())
        assertEquals(30L, listOf(50L, 10L, 20L, 40L, 30L).medianNs())
        assertEquals(Long.MAX_VALUE - 1, listOf(Long.MAX_VALUE, Long.MAX_VALUE - 2).medianNs())
    }

    @Test fun timingWarmupCopiesAndRelease() = runBlocking {
        var now = 0L
        var calls = 0
        var closed = false
        var saves = 0
        val samples = floatArrayOf(0.25f, -0.25f)
        val result = BenchmarkCaseResult("fake")
        BenchmarkRunner(clock = { now }).runCase(result, listOf(clip to samples), { object : BenchmarkRecognizer {
            override suspend fun load() { now += 10 }
            override suspend fun transcribe(samples: FloatArray): String {
                calls++
                assertArrayEquals(floatArrayOf(0.25f, -0.25f), samples, 0f)
                samples[0] = 99f
                now += 20
                return "hello"
            }
            override suspend fun close() { closed = true; now += 30 }
        } }, { saves++; now += 1000 })
        assertEquals(7, calls)
        assertEquals(6, result.observations.size)
        assertEquals(listOf("first") + List(5) { "warm" }, result.observations.map { it.phase })
        assertEquals(10L, result.initializationNs)
        assertEquals(30L, result.releaseCallNs)
        assertTrue(result.observations.all { it.elapsedNs == 20L })
        assertTrue(saves >= 8)
        assertTrue(closed)
        assertEquals("completed", result.status)
        assertEquals(0.25f, samples[0], 0f)
    }

    @Test fun failuresKeepPartialResultsAndOtherModelsCanRun() = runBlocking {
        var owned = false
        suspend fun attempt(id: String, fail: Boolean): BenchmarkCaseResult {
            val result = BenchmarkCaseResult(id)
            var calls = 0
            BenchmarkRunner().runCase(result, listOf(clip to floatArrayOf(0f, 0f)), { object : BenchmarkRecognizer {
                override suspend fun load() { assertFalse(owned); owned = true }
                override suspend fun transcribe(samples: FloatArray): String {
                    calls++; if (fail && calls == 3) error("private dictated words must not enter reason")
                    return "hello"
                }
                override suspend fun close() { owned = false }
            } }, {})
            return result
        }
        val failed = attempt("bad", true)
        assertEquals("failed", failed.status)
        assertEquals(1, failed.observations.size)
        assertFalse(failed.reason!!.contains("private"))
        assertEquals("completed", attempt("good", false).status)
        assertFalse(owned)
    }

    @Test fun cancellationClosesAndIsNotScoredAsSuccess() = runBlocking {
        var closed = false
        val result = BenchmarkCaseResult("cancel")
        try {
            BenchmarkRunner().runCase(result, listOf(clip to floatArrayOf(0f, 0f)), { object : BenchmarkRecognizer {
                override suspend fun load() {}
                override suspend fun transcribe(samples: FloatArray): String = throw CancellationException()
                override suspend fun close() { closed = true }
            } }, {})
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals("cancelled", result.status)
        assertTrue(result.observations.isEmpty())
        assertTrue(closed)
    }

    @Test fun differentModelsReceiveTheSameRecordingAndKeepTheirOwnOutputs() = runBlocking {
        val audio = floatArrayOf(0.5f, -0.5f)
        val outputs = listOf("hello", "yellow")
        val results = outputs.mapIndexed { index, output ->
            val result = BenchmarkCaseResult("model-$index")
            BenchmarkRunner().runCase(result, listOf(clip to audio), { object : BenchmarkRecognizer {
                override suspend fun load() {}
                override suspend fun transcribe(samples: FloatArray): String {
                    assertArrayEquals(floatArrayOf(0.5f, -0.5f), samples, 0f)
                    samples.fill(0f)
                    return output
                }
                override suspend fun close() {}
            } }, {})
            result
        }
        results.zip(outputs).forEach { (result, output) ->
            assertEquals("completed", result.status)
            assertEquals(6, result.observations.size)
            assertTrue(result.observations.all { it.transcript == output })
        }
        assertEquals(0.0, results[0].observations.first().accuracy.words!!.rate, 0.0)
        assertEquals(1.0, results[1].observations.first().accuracy.words!!.rate, 0.0)
        assertArrayEquals(floatArrayOf(0.5f, -0.5f), audio, 0f)
    }
}
