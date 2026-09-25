package org.futo.voiceinput.diagnostics

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipFile

class DiagnosticArchiveTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `bug report contains readable summary and structured records without private files`() {
        val output = temporary.newFile("report.zip")
        val snapshot = DiagnosticSnapshot(listOf(
            DiagnosticRecord(timestampMs = 1000, event = DiagnosticEvent.SESSION_STARTED,
                sessionId = "a-session"),
            DiagnosticRecord(timestampMs = 1100, event = DiagnosticEvent.MODEL_LOAD_FAILED,
                sessionId = "a-session", failure = DiagnosticFailure.from(IllegalStateException("SECRET")))
        ), 1)
        DiagnosticArchive.write(output, snapshot, "{}", "{}", "It stopped", "Finish dictation", emptyMap(), 2)
        ZipFile(output).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertEquals(setOf("README.txt", "summary.txt", "environment.json", "settings.json", "events.jsonl", "notes.txt"), names)
            val summary = zip.getInputStream(zip.getEntry("summary.txt")).bufferedReader().readText()
            assertTrue(summary.contains("MODEL_LOAD_FAILED"))
            assertTrue(summary.contains("Unreadable records skipped: 1"))
            assertTrue(summary.contains("Dropped events: 2"))
            val contents = zip.entries().asSequence().joinToString { zip.getInputStream(it).bufferedReader().readText() }
            assertFalse(contents.contains("SECRET"))
            assertTrue(contents.contains("It stopped"))
        }
    }

    @Test fun `combined export retains S1 timing but excludes transcript files and free form native output`() {
        val output = temporary.newFile("combined.zip")
        DiagnosticArchive.write(output, DiagnosticSnapshot(emptyList(), 0), "{}", "{}", "", "", mapOf(
            "transcripts.jsonl" to "SECRET DICTATION",
            "../private.txt" to "SECRET FILE",
            "runs.jsonl" to """{"totalMs":120,"runtimeSelected":"cpu","nativeMetricsJson":"SECRET","backendLoaderErrors":["SECRET"],"transcript":"SECRET"}""",
            "benchmark.json" to """{"measurementsMs":{"cpu/4":500,"SECRET":1},"failures":{"cpu/2":"SECRET"}}"""
        ), 0)
        ZipFile(output).use { zip ->
            val contents = zip.entries().asSequence().joinToString { zip.getInputStream(it).bufferedReader().readText() }
            assertFalse(contents.contains("SECRET"))
            assertTrue(contents.contains("120"))
            assertTrue(contents.contains("cpu/4"))
            assertNotNull(zip.getEntry("s1/runs.jsonl"))
            assertNotNull(zip.getEntry("s1/benchmark.json"))
            assertNull(zip.getEntry("transcripts.jsonl"))
            assertNull(zip.getEntry("notes.txt"))
        }
    }

    @Test fun `cleanup and failed benchmark evidence survive the privacy projection`() {
        val output = temporary.newFile("failed.zip")
        DiagnosticArchive.write(output, DiagnosticSnapshot(emptyList(), 0), "{}", "{}", "", "", mapOf(
            "runs.jsonl" to """{"outcome":"valid_empty","errorCategory":"bind_failed"}""",
            "benchmark.json" to """{"measurementsMs":{},"failures":{"cpu/4":"timeout","opencl/4":"process_died","cpu/2":"SECRET"}}"""
        ), 0)
        ZipFile(output).use { zip ->
            val all = zip.entries().asSequence().joinToString { zip.getInputStream(it).bufferedReader().readText() }
            assertTrue(all.contains("valid_empty"))
            assertTrue(all.contains("bind_failed"))
            assertTrue(all.contains("timeout"))
            assertTrue(all.contains("process_died"))
            assertTrue(all.contains("unclassified"))
            assertFalse(all.contains("SECRET"))
        }
    }

    @Test fun `prepared report expires when its oldest evidence expires`() {
        val directory = temporary.newFolder()
        val report = java.io.File(directory, "voice-input-diagnostics-1000.zip")
        DiagnosticArchive.write(report, DiagnosticSnapshot(listOf(
            DiagnosticRecord(timestampMs = 1000, event = DiagnosticEvent.APP_STARTED)
        ), 0), "{}", "{}", "", "", emptyMap(), 0)
        DiagnosticArchive.purgePrepared(directory, nowMs = 1000 + DiagnosticStore.RETENTION_MS + 1)
        assertFalse(report.exists())
    }

    @Test fun `prepared reports share a size budget and retain newest first`() {
        val directory = temporary.newFolder()
        val oldest = java.io.File(directory, "voice-input-diagnostics-1000.zip").apply { writeBytes(ByteArray(1024 * 1024)) }
        val middle = java.io.File(directory, "voice-input-diagnostics-2000.zip").apply { writeBytes(ByteArray(1024 * 1024)) }
        val newest = java.io.File(directory, "voice-input-diagnostics-3000.zip").apply { writeBytes(ByteArray(1024 * 1024)) }
        DiagnosticArchive.purgePrepared(directory)
        assertFalse(oldest.exists())
        assertTrue(middle.exists())
        assertTrue(newest.exists())
        assertTrue(directory.listFiles()!!.sumOf { it.length() } <= DiagnosticArchive.CACHE_BYTES)
    }
}
