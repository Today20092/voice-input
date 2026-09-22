package org.futo.voiceinput.s1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class S1MiniTranscriptCaptureStoreTest {
    @Test
    fun failedCleanupKeepsActualStagesAndMarksCleanupNotProduced() {
        val directory = Files.createTempDirectory("s1-transcript-capture").toFile()
        try {
            val capture = S1MiniTranscriptCapture.create(
                reportId = "report-1",
                capturedAtEpochMs = 1_000L,
                rawTranscript = "hello comma world",
                cleanedTranscript = null,
                finalDeliveredTranscript = "hello, world",
                failureOrBypassReason = "cpu_unavailable"
            )

            S1MiniTranscriptCaptureStore.save(directory, capture, nowEpochMs = 1_000L)
            val result = S1MiniTranscriptCaptureStore.load(directory, nowEpochMs = 1_000L)

            assertEquals(0, result.unreadableCount)
            assertEquals(1, result.captures.size)
            assertEquals("hello comma world", result.captures.single().raw.text)
            assertEquals(
                S1MiniTranscriptStageStatus.NotProduced,
                result.captures.single().cleaned.status
            )
            assertNull(result.captures.single().cleaned.text)
            assertEquals("hello, world", result.captures.single().finalDelivered.text)
            assertEquals("cpu_unavailable", result.captures.single().failureOrBypassReason)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun unreadableCapturesAloneRemainExportableAsAnOmissionReport() {
        val result = S1MiniTranscriptCaptureLoadResult(
            captures = emptyList(),
            unreadableCount = 2
        )

        assertTrue(result.hasExportableEvidence)
    }

    @Test
    fun retainsLatestTenAndExpiresCapturesAfterSevenDays() {
        val directory = Files.createTempDirectory("s1-transcript-retention").toFile()
        try {
            repeat(12) { index ->
                val capturedAt = 1_000L + index
                S1MiniTranscriptCaptureStore.save(
                    directory,
                    capture("report-$index", capturedAt),
                    nowEpochMs = capturedAt
                )
            }
            assertEquals(10, S1MiniTranscriptCaptureStore.load(directory, 2_000L).captures.size)

            val expiredAt = 1_011L + S1MiniTranscriptCapture.RETENTION_MS
            assertTrue(S1MiniTranscriptCaptureStore.load(directory, expiredAt).captures.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun unreadableCaptureIsReportedWithoutHidingValidCaptures() {
        val directory = Files.createTempDirectory("s1-transcript-corrupt").toFile()
        try {
            S1MiniTranscriptCaptureStore.save(directory, capture("valid", 1_000L), 1_000L)
            directory.resolve("broken.json").writeText("not-json")

            val result = S1MiniTranscriptCaptureStore.load(directory, 2_000L)

            assertEquals(1, result.captures.size)
            assertEquals(1, result.unreadableCount)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun truncatesOversizedStagesAndSupportsIndividualDeletion() {
        val directory = Files.createTempDirectory("s1-transcript-delete").toFile()
        try {
            val oversized = "x".repeat(100_001)
            val capture = S1MiniTranscriptCapture.create(
                reportId = "large",
                capturedAtEpochMs = 1_000L,
                rawTranscript = oversized,
                cleanedTranscript = oversized,
                finalDeliveredTranscript = oversized,
                failureOrBypassReason = null
            )
            S1MiniTranscriptCaptureStore.save(directory, capture, 1_000L)

            val stored = S1MiniTranscriptCaptureStore.load(directory, 1_000L).captures.single()
            assertEquals(100_000, stored.raw.text?.length)
            assertEquals(100_001, stored.raw.originalCharacters)
            assertTrue(stored.raw.truncated)
            assertTrue(S1MiniTranscriptCaptureStore.delete(directory, "large", 1_000L))
            assertFalse(S1MiniTranscriptCaptureStore.delete(directory, "large", 1_000L))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun capture(reportId: String, capturedAtEpochMs: Long) =
        S1MiniTranscriptCapture.create(
            reportId = reportId,
            capturedAtEpochMs = capturedAtEpochMs,
            rawTranscript = "raw",
            cleanedTranscript = "cleaned",
            finalDeliveredTranscript = "final",
            failureOrBypassReason = null
        )
}
