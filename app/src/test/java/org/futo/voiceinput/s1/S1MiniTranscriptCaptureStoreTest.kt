package org.futo.voiceinput.s1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.io.IOException
import org.junit.Assert.assertThrows

class S1MiniTranscriptCaptureStoreTest {
    @Test
    fun abandonedWritesAreRemovedAndCorruptCapturesShareTheTenFileLimit() {
        val directory = Files.createTempDirectory("s1-transcript-interruption").toFile()
        try {
            directory.resolve("interrupted.json.tmp").writeText("Avery Support says cobalt orchard 472")
            repeat(12) { index ->
                directory.resolve("corrupt-$index.json").apply {
                    writeText("broken")
                    assertTrue(setLastModified(1_000L + index))
                }
            }

            val result = S1MiniTranscriptCaptureStore.load(directory, 2_000L)

            assertFalse(directory.resolve("interrupted.json.tmp").exists())
            assertEquals(10, result.unreadableCount)
            assertEquals(10, directory.listFiles()!!.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun oversizedCorruptionIsDeletedWithoutReadingItsContents() {
        val directory = Files.createTempDirectory("s1-transcript-oversized").toFile()
        try {
            val oversized = directory.resolve("oversized.json")
            java.io.RandomAccessFile(oversized, "rw").use { it.setLength(3L * 1024 * 1024 + 1) }
            S1MiniTranscriptCaptureStore.load(directory, 1_000L)
            assertFalse(oversized.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun acceptedCaptureRetainsAllBoundedStagesWithEscapedCharacters() {
        val directory = Files.createTempDirectory("s1-transcript-escaped").toFile()
        try {
            val text = "\u0001".repeat(100_000)
            val capture = S1MiniTranscriptCapture.create(
                "escaped", 1_000L, text, text, text, null
            )
            S1MiniTranscriptCaptureStore.save(directory, capture, 1_000L)

            val stored = S1MiniTranscriptCaptureStore.load(directory, 1_000L).captures.single()
            assertEquals(text, stored.raw.text)
            assertEquals(text, stored.cleaned.text)
            assertEquals(text, stored.prepared.text)
            assertEquals(text, stored.finalDelivered.text)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun inaccessibleCaptureDirectoryIsAnErrorInsteadOfAnEmptyList() {
        val file = Files.createTempFile("s1-transcript-not-directory", ".tmp").toFile()
        try {
            assertThrows(IOException::class.java) {
                S1MiniTranscriptCaptureStore.load(file, 1_000L)
            }
            assertThrows(IOException::class.java) { S1MiniTranscriptCaptureStore.clear(file) }
            assertTrue(file.exists())
        } finally {
            file.delete()
        }
    }

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
            assertEquals((11 downTo 2).map { "report-$it" },
                S1MiniTranscriptCaptureStore.load(directory, 2_000L).captures.map { it.reportId })

            val expiredAt = 1_011L + S1MiniTranscriptCapture.RETENTION_MS
            assertTrue(S1MiniTranscriptCaptureStore.load(directory, expiredAt).captures.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun loadExpiresOldCorruptionButKeepsRecentCorruptionAndClearSurvivesReopening() {
        val directory = Files.createTempDirectory("s1-transcript-reopen").toFile()
        val now = S1MiniTranscriptCapture.RETENTION_MS + 10_000L
        try {
            S1MiniTranscriptCaptureStore.save(directory, capture("expired", 1L), 1L)
            S1MiniTranscriptCaptureStore.save(directory, capture("retained", now), 1L)
            directory.resolve("recent.json").writeText("broken")
            directory.resolve("old.json").apply {
                writeText("broken")
                assertTrue(setLastModified(1L))
            }
            val reopenedDirectory = java.io.File(directory.absolutePath)
            val loaded = S1MiniTranscriptCaptureStore.load(reopenedDirectory, now)
            assertEquals(listOf("retained"), loaded.captures.map { it.reportId })
            assertEquals(1, loaded.unreadableCount)
            assertFalse(directory.resolve("old.json").exists())
            assertEquals(2, directory.listFiles()!!.size)
            S1MiniTranscriptCaptureStore.clear(reopenedDirectory)
            assertFalse(S1MiniTranscriptCaptureStore.load(directory, now).hasExportableEvidence)
            S1MiniTranscriptCaptureStore.save(directory, capture("new", now), now)
            assertEquals("new", S1MiniTranscriptCaptureStore.load(directory, now).captures.single().reportId)
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
