package org.futo.voiceinput.benchmark

import org.junit.Assert.*
import org.junit.Test

class ComparisonResultsTest {
    private val clips = listOf(
        BenchmarkClip("a", "o", "c", 16000, "ten words", "r"),
        BenchmarkClip("b", "o", "c", 16000, "one word", "r")
    )
    private fun observation(id: String, phase: String, ms: Long, words: EditScore) =
        DecodeObservation(id, phase, 1, ms * 1_000_000, "text", Accuracy(words = words))
    private fun result(id: String = "orukeet") = BenchmarkCaseResult(id, status = "completed", initializationNs = 100_000_000,
        sampledPeakPssKb = 204800, observations = mutableListOf(
            observation("a", "first", 20, EditScore(1, 0, 0, 10)),
            observation("a", "warm", 2, EditScore(0, 0, 0, 10)),
            observation("a", "warm", 4, EditScore(0, 0, 0, 10)),
            observation("b", "warm", 6, EditScore(1, 0, 0, 1)),
            observation("b", "warm", 8, EditScore(1, 0, 0, 1))))
    private fun report(result: BenchmarkCaseResult) = BenchmarkReport(clips = clips, selectedModelIds = listOf(result.modelId),
        installedBefore = emptyList(), cases = mutableListOf(result), warmRepetitions = 2)

    @Test fun timingSeparatesLoadedSuiteFromLoadAndFirstRecordingAndWeightsWordErrors() {
        val summary = comparisonSummaries(report(result()), emptyList()).single()
        assertEquals(10.0, summary.readyMs!!, 0.0001)
        assertEquals(120.0, summary.startupMs!!, 0.0001)
        assertEquals(200.0 / 11, summary.wordErrorPercent!!, 0.0001)
        assertEquals(200.0, summary.ramMiB!!, 0.0001)
    }

    @Test fun workerMetricsAreNotRankedAsLoadedOrAsCompleteProcessMemory() {
        val summary = comparisonSummaries(report(result("whistle")), emptyList()).single()
        assertNull(summary.readyMs)
        assertNull(summary.ramMiB)
        assertEquals(120.0, summary.startupMs!!, 0.0001)
        assertNotNull(summary.wordErrorPercent)
    }

    @Test fun incompleteAndUnscoredInputsNeverBecomeZeroOrComparableMeasurements() {
        val partial = result().apply { observations.removeLast() }
        assertNull(comparisonSummaries(report(partial), emptyList()).single().readyMs)
        partial.status = "failed"
        val failed = comparisonSummaries(report(partial), emptyList()).single()
        assertNull(failed.startupMs); assertNull(failed.wordErrorPercent); assertNull(failed.ramMiB)
        val unverified = report(result()).copy(clips = clips.map { it.copy(referenceVerified = false) })
        assertNull(comparisonSummaries(unverified, emptyList()).single().wordErrorPercent)
    }

    @Test fun zeroErrorsAndErrorsOverOneHundredPercentArePreserved() {
        val zero = result().apply { observations.replaceAll { it.copy(accuracy = Accuracy(words = EditScore(0, 0, 0, 1))) } }
        assertEquals(0.0, comparisonSummaries(report(zero), emptyList()).single().wordErrorPercent!!, 0.0)
        val high = result().apply { observations.replaceAll { it.copy(accuracy = Accuracy(words = EditScore(0, 0, 3, 1))) } }
        assertEquals(300.0, comparisonSummaries(report(high), emptyList()).single().wordErrorPercent!!, 0.0)
    }

    @Test fun sortingUsesMeasuredValuesAndKeepsMissingValuesLast() {
        val base = comparisonSummaries(report(result()), emptyList()).single()
        val fast = base.copy(name = "fast", readyMs = 1.0, modelBytes = 50)
        val small = base.copy(name = "small", readyMs = 2.0, modelBytes = 10)
        val unknown = base.copy(name = "unknown", readyMs = null, modelBytes = null)
        val rows = listOf(unknown, small, fast)
        assertEquals(listOf("fast", "small", "unknown"), rows.ordered(ResultOrder.SPEED, false).map { it.name })
        assertEquals(listOf("small", "fast", "unknown"), rows.ordered(ResultOrder.SIZE, false).map { it.name })
        assertEquals(rows, rows.ordered(ResultOrder.RUN, false))
    }
}
