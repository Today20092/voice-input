package org.futo.voiceinput.benchmark

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BenchmarkReferenceReviewTest {
    @Test fun reviewScoresSavedOutputsWithoutChangingMeasurementsOrOriginalEvidence() {
        val clip = BenchmarkClip("clip", "original", "canonical", 16000, "hello world", "reference", referenceVerified = false)
        val observation = DecodeObservation("clip", "warm", 1, 123L, "hello", Accuracy(unavailable = "Unverified"))
        val result = BenchmarkCaseResult("model", status = "completed", initializationNs = 456L,
            observations = mutableListOf(observation))
        val report = BenchmarkReport(clips = listOf(clip), selectedModelIds = listOf("model"),
            installedBefore = emptyList(), cases = mutableListOf(result))
        val reviewed = report.withVerifiedSpokenReferences()
        assertTrue(reviewed.clips.single().referenceVerified)
        assertFalse(report.clips.single().referenceVerified)
        assertEquals("Unverified", report.cases.single().observations.single().accuracy.unavailable)
        val measured = reviewed.cases.single().observations.single()
        assertEquals(123L, measured.elapsedNs)
        assertEquals(456L, reviewed.cases.single().initializationNs)
        assertEquals(1, measured.accuracy.words!!.deletions)
        assertEquals(0.5, measured.accuracy.words!!.rate, 0.0)
        assertEquals(report.runId, reviewed.runId)
        assertEquals(clip.canonicalSha256, reviewed.clips.single().canonicalSha256)
        assertTrue(Json { encodeDefaults = true }.encodeToString(reviewed).contains("\"rate\":0.5"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun reviewRequiresAReferenceForEveryRecording() {
        BenchmarkReport(clips = listOf(BenchmarkClip("clip", "original", "canonical", 16000, null, "")),
            selectedModelIds = emptyList(), installedBefore = emptyList()).withVerifiedSpokenReferences()
    }
}
