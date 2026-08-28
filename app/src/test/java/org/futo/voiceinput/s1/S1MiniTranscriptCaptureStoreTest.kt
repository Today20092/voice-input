package org.futo.voiceinput.s1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            assertEquals("not_produced", result.captures.single().cleaned.status)
            assertNull(result.captures.single().cleaned.text)
            assertEquals("hello, world", result.captures.single().finalDelivered.text)
            assertEquals("cpu_unavailable", result.captures.single().failureOrBypassReason)
        } finally {
            directory.deleteRecursively()
        }
    }
}
