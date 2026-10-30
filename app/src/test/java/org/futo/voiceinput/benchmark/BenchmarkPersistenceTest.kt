package org.futo.voiceinput.benchmark

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BenchmarkPersistenceTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun inputsResultsAndInterruptedStatusSurviveReopening() {
        val store = BenchmarkStore(temporary.root)
        val report = BenchmarkReport(clips = emptyList(), selectedModelIds = listOf("one", "two"), installedBefore = listOf("one"))
        val samples = floatArrayOf(0.25f, -0.5f)
        val hash = store.saveAudio(report.runId, "clip", samples)
        val clip = BenchmarkClip("clip", "source", hash, samples.size, "hello", "reference")
        val running = report.copy(status = "running", clips = listOf(clip))
        val completed = BenchmarkCaseResult("one", status = "completed", initializationNs = 100)
        completed.observations.add(DecodeObservation("clip", "first", 0, 50, "hello", BenchmarkScoring.score("hello", "hello")))
        running.cases.add(completed)
        running.cases.add(BenchmarkCaseResult("two"))
        store.save(running)
        val reopened = BenchmarkStore(temporary.root)
        val recovered = reopened.recoverInterrupted().single()
        assertEquals("interrupted", recovered.status)
        assertEquals("completed", recovered.cases[0].status)
        assertEquals(completed, recovered.cases[0])
        assertEquals("interrupted", recovered.cases[1].status)
        assertArrayEquals(samples, reopened.readAudio(report.runId, clip), 0f)
        assertTrue(reopened.json(recovered).contains("hello"))
        assertTrue(reopened.markdown(recovered).contains("Contains reference text"))
        reopened.audioFile(report.runId, "clip").writeBytes(ByteArray(8))
        try { reopened.readAudio(report.runId, clip); fail("Changed input accepted") } catch (_: IllegalStateException) { }
    }
    @Test fun invalidRunOrClipCannotEscapePrivateRoot() {
        val store = BenchmarkStore(temporary.root)
        try { store.directory("../outside"); fail("Invalid run accepted") } catch (_: IllegalArgumentException) { }
        val report = BenchmarkReport(clips = emptyList(), selectedModelIds = emptyList(), installedBefore = emptyList())
        try { store.audioFile(report.runId, "../outside"); fail("Invalid clip accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun exportedWordsStayLiteralEvenWhenTheyContainMarkdownOrHtml() {
        val words = "```\n<img src=\"https://example.org/pixel\">"
        val clip = BenchmarkClip("clip", "source", "canonical", 16000, words, "reference")
        val report = BenchmarkReport(clips = listOf(clip), selectedModelIds = emptyList(), installedBefore = emptyList())
        assertTrue(BenchmarkStore(temporary.root).markdown(report).contains("````\n$words\n````"))
    }
}
