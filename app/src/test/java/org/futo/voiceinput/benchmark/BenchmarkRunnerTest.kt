package org.futo.voiceinput.benchmark

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class BenchmarkRunnerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val clip = BenchmarkClip("test", "original", "canonical", 2, "hello", "reference")

    @Test fun partialRunMedianAveragesTheMiddlePair() {
        assertEquals(25L, listOf(40L, 10L, 20L, 30L).medianNs())
        assertEquals(30L, listOf(50L, 10L, 20L, 40L, 30L).medianNs())
        assertEquals(Long.MAX_VALUE - 1, listOf(Long.MAX_VALUE, Long.MAX_VALUE - 2).medianNs())
    }

    @Test fun twoWarmRepeatsCoverEachClipAndKeepFirstDecodeSeparate() = runBlocking {
        var calls = 0
        val result = BenchmarkCaseResult("short")
        val clips = (1..3).map { clip.copy(id = "clip-$it") }
        BenchmarkRunner(warmRepetitions = 2).runCase(result, clips, { floatArrayOf(0f, 0f) }, { object : BenchmarkRecognizer {
            override suspend fun load() {}
            override suspend fun transcribe(samples: FloatArray): String { calls++; return "hello" }
            override suspend fun close() {}
        } }, {})
        assertEquals("completed", result.status)
        assertEquals(10, calls)
        assertEquals(1, result.observations.count { it.phase == "first" })
        clips.forEach { clip ->
            assertEquals(listOf(1, 2), result.observations.filter {
                it.clipId == clip.id && it.phase == "warm"
            }.map { it.repetition })
        }
    }

    @Test fun timingWarmupCopiesAndRelease() = runBlocking {
        var now = 0L
        var calls = 0
        var closed = false
        var saves = 0
        val samples = floatArrayOf(0.25f, -0.25f)
        val result = BenchmarkCaseResult("fake")
        BenchmarkRunner(clock = { now }).runCase(result, listOf(clip), { now += 500; samples }, { object : BenchmarkRecognizer {
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
            BenchmarkRunner().runCase(result, listOf(clip), { floatArrayOf(0f, 0f) }, { object : BenchmarkRecognizer {
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
        val loaded = mutableListOf<String>()
        val result = BenchmarkCaseResult("cancel")
        try {
            BenchmarkRunner().runCase(result, listOf(clip, clip.copy(id = "later")), {
                loaded.add(it.id)
                floatArrayOf(0f, 0f)
            }, { object : BenchmarkRecognizer {
                override suspend fun load() {}
                override suspend fun transcribe(samples: FloatArray): String = throw CancellationException()
                override suspend fun close() { closed = true }
            } }, {})
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals("cancelled", result.status)
        assertTrue(result.observations.isEmpty())
        assertTrue(closed)
        assertEquals(listOf("test"), loaded)
    }

    @Test fun differentModelsReceiveTheSameRecordingAndKeepTheirOwnOutputs() = runBlocking {
        val audio = floatArrayOf(0.5f, -0.5f)
        val store = BenchmarkStore(temporary.newFolder())
        val report = BenchmarkReport(clips = emptyList(), selectedModelIds = emptyList(), installedBefore = emptyList())
        val storedClip = clip.copy(canonicalSha256 = store.saveAudio(report.runId, clip.id, audio))
        val outputs = listOf("hello", "yellow")
        val results = outputs.mapIndexed { index, output ->
            val result = BenchmarkCaseResult("model-$index")
            BenchmarkRunner().runCase(result, listOf(storedClip), { store.readAudio(report.runId, it) }, { object : BenchmarkRecognizer {
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

    @Test fun laterRecordingIsReadOnlyAfterCurrentPassesAndReadFailurePreservesEvidence() = runBlocking {
        val store = BenchmarkStore(temporary.newFolder())
        val report = BenchmarkReport(clips = emptyList(), selectedModelIds = listOf("test"), installedBefore = emptyList())
        val clips = (1..2).map { index ->
            clip.copy(id = "clip-$index", canonicalSha256 = store.saveAudio(report.runId, "clip-$index", floatArrayOf(0.5f, -0.5f)))
        }
        val result = BenchmarkCaseResult("test")
        val saved = report.copy(clips = clips, cases = mutableListOf(result))
        var closed = false
        var calls = 0
        BenchmarkRunner(warmRepetitions = 2).runCase(result, clips, { store.readAudio(report.runId, it) }, { object : BenchmarkRecognizer {
            override suspend fun load() {}
            override suspend fun transcribe(samples: FloatArray): String {
                assertArrayEquals(floatArrayOf(0.5f, -0.5f), samples, 0f)
                samples.fill(99f)
                if (++calls == 1) assertTrue(store.audioFile(report.runId, clips[1].id).delete())
                return "hello"
            }
            override suspend fun close() { closed = true }
        } }, { store.save(saved) })
        assertEquals("failed", result.status)
        assertTrue(closed)
        val restored = store.load(report.runId).cases.single()
        assertEquals(listOf("first", "warm", "warm"), restored.observations.map { it.phase })
        assertTrue(restored.observations.all { it.clipId == "clip-1" })
        assertEquals("failed", restored.status)
    }
}
