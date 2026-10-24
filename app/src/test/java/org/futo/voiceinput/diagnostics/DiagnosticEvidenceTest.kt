package org.futo.voiceinput.diagnostics

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipFile

class DiagnosticEvidenceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun sameTimeRecordsHaveAnOrderAndRestartHasADistinctCollector() {
        val directory = temporary.newFolder()
        val first = DiagnosticStore(directory, now = { 1000L })
        first.record(DiagnosticEvent.SESSION_STARTED)
        first.record(DiagnosticEvent.RECORDER_STARTED)
        val before = first.snapshot().records
        assertEquals(listOf(1L, 2L), before.map { it.sequence })
        assertEquals(1, before.map { it.collectorId }.distinct().size)
        val restarted = DiagnosticStore(directory, now = { 1000L })
        restarted.record(DiagnosticEvent.APP_STARTED)
        assertNotEquals(before.first().collectorId, restarted.snapshot().records.last().collectorId)
    }

    @Test fun modeHistorySurvivesRestartAndDetailedDeadlineDoesNotRelabelLaterEvents() {
        val directory = temporary.newFolder()
        var clock = 1000L
        var store = DiagnosticStore(directory, now = { clock })
        store.startDetailed()
        store.record(DiagnosticEvent.FIRST_AUDIO)
        clock += DiagnosticStore.DETAILED_MS + 1
        store = DiagnosticStore(directory, now = { clock })
        store.record(DiagnosticEvent.RECORDING_STOPPED)
        val snapshot = store.snapshot()
        assertEquals(DiagnosticCollectionMode.Detailed, snapshot.records.first().collectionMode)
        assertEquals(DiagnosticCollectionMode.Standard, snapshot.records.last().collectionMode)
        assertEquals(1000L + DiagnosticStore.DETAILED_MS, snapshot.modes.last().detailedUntilMs)
        store.setEnabled(false)
        assertFalse(store.snapshot().modes.last().enabled)
    }

    @Test fun configurationAndCleanupJoinAreSanitizedAtStorageBoundary() {
        val store = DiagnosticStore(temporary.newFolder())
        val report = "11111111-1111-1111-1111-111111111111"
        store.record(DiagnosticEvent.MODEL_LOAD_STARTED, configuration = DiagnosticConfiguration(
            runtime = "orukeet", modelVersion = "a".repeat(40), language = "en", threads = 4))
        store.record(DiagnosticEvent.CLEANUP_FINISHED, reportId = report)
        store.record(DiagnosticEvent.MODEL_LOAD_STARTED, configuration = DiagnosticConfiguration(
            runtime = "SECRET/path", modelVersion = "SECRET/path", language = "SECRET", threads = -1),
            reportId = "SECRET")
        val records = store.snapshot().records
        assertEquals("a".repeat(40), records.first().configuration?.modelVersion)
        assertEquals(report, records[1].reportId)
        val encoded = records.joinToString { DiagnosticStore.json.encodeToString(DiagnosticRecord.serializer(), it) }
        assertFalse(encoded.contains("SECRET"))
        assertNull(records.last().configuration?.threads)
    }

    @Test fun archiveExplainsMissingMetricsAndSeparatesWriterLossFromExportTrimming() {
        val output = temporary.newFile("evidence.zip")
        DiagnosticArchive.write(output, DiagnosticSnapshot(emptyList(), 2), "{}", "{}", "", "", emptyMap(), 3)
        ZipFile(output).use { zip ->
            val readme = zip.getInputStream(zip.getEntry("README.txt")).bufferedReader().readText()
            val summary = zip.getInputStream(zip.getEntry("summary.txt")).bufferedReader().readText()
            assertTrue(readme.contains("Unavailable metrics are omitted, never zero"))
            assertTrue(readme.contains("STOP_REASON"))
            assertTrue(summary.contains("Writer drops (current process): 3"))
            assertTrue(summary.contains("Records omitted for export budget: 0"))
            assertTrue(summary.contains("No session evidence available"))
        }
    }

    @Test fun collectionModesAreBoundedAndOversizeRejectionsRemainVisibleAfterRestart() {
        val directory = temporary.newFolder()
        val store = DiagnosticStore(directory, now = { 1000L }, maxBytes = 128L)
        repeat(50) { store.startDetailed(); store.stopDetailed() }
        store.record(DiagnosticEvent.SESSION_STARTED)
        val reopened = DiagnosticStore(directory, now = { 1000L }, maxBytes = 128L)
        assertEquals(32, reopened.snapshot().modes.size)
        assertEquals(1L, reopened.snapshot().rejectedRecords)
        assertTrue(reopened.snapshot().records.isEmpty())
    }

    @Test fun readableCaptureEvidenceSeparatesRealSamplesFromEmptyRecognition() {
        val output = temporary.newFile("capture.zip")
        val store = DiagnosticStore(temporary.newFolder())
        val session = "11111111-1111-1111-1111-111111111111"
        store.record(DiagnosticEvent.CAPTURE_SUMMARY, session, metrics = mapOf(
            DiagnosticMetric.CAPTURED_SAMPLES to 16000L, DiagnosticMetric.VAD_SPEECH_FRAMES to 20L,
            DiagnosticMetric.DRAIN_SAMPLES to 1600L, DiagnosticMetric.STOP_REASON to 0L))
        store.record(DiagnosticEvent.NO_SPEECH, session)
        DiagnosticArchive.write(output, store.snapshot(), "{}", "{}", "", "", emptyMap(), 0)
        ZipFile(output).use { zip ->
            val summary = zip.getInputStream(zip.getEntry("summary.txt")).bufferedReader().readText()
            assertTrue(summary.contains("Captured samples: 16000"))
            assertTrue(summary.contains("VAD speech frames: 20"))
            assertTrue(summary.contains("Drained samples: 1600"))
            assertTrue(summary.contains("NO_SPEECH"))
        }
    }
}
