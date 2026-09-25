package org.futo.voiceinput.diagnostics

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `standard records exclude error messages and unrecognized identifiers`() {
        val store = DiagnosticStore(temporary.newFolder())
        store.record(DiagnosticEvent.SESSION_FAILED, sessionId = "private dictated text",
            model = "private model URL", error = IllegalStateException("private dictated text"))
        val record = store.snapshot().records.single()
        assertNull(record.sessionId)
        assertNull(record.model)
        assertEquals("illegal_state", record.failure?.kind)
        assertFalse(DiagnosticStore.json.encodeToString(DiagnosticRecord.serializer(), record)
            .contains("private"))
    }

    @Test fun `opting out survives restart and clear does not reenable collection`() {
        val directory = temporary.newFolder()
        var clock = 100_000L
        var store = DiagnosticStore(directory, now = { clock })
        store.record(DiagnosticEvent.APP_STARTED)
        store.startDetailed()
        store.setEnabled(false)
        store = DiagnosticStore(directory, now = { clock })
        store.record(DiagnosticEvent.MANAGED_CRASH, error = RuntimeException("secret"))
        assertEquals(1, store.snapshot().records.size)
        assertEquals(0L, store.detailedRemainingMs())
        store.clear()
        assertFalse(store.policy().enabled)
        assertTrue(store.snapshot().records.isEmpty())
        clock += 100
        store.setEnabled(true)
        assertEquals(clock, store.policy().collectSinceMs)
    }

    @Test fun `detailed collection expires and can be stopped early`() {
        var clock = 100_000L
        val store = DiagnosticStore(temporary.newFolder(), now = { clock })
        store.record(DiagnosticEvent.PARTIAL, detailed = true)
        assertTrue(store.snapshot().records.isEmpty())
        store.startDetailed()
        store.record(DiagnosticEvent.PARTIAL, detailed = true)
        clock += DiagnosticStore.DETAILED_MS
        store.record(DiagnosticEvent.PARTIAL, detailed = true)
        assertEquals(1, store.snapshot().records.size)
        assertEquals(0L, store.detailedRemainingMs())
        store.startDetailed()
        store.stopDetailed()
        assertEquals(0L, store.detailedRemainingMs())
    }

    @Test fun `retention and size caps remove old evidence while keeping recent records`() {
        var clock = 100_000L
        val directory = temporary.newFolder()
        val store = DiagnosticStore(directory, now = { clock }, maxBytes = 2048, retentionMs = 1000)
        store.record(DiagnosticEvent.APP_STARTED)
        clock += 1001
        assertTrue(store.snapshot().records.isEmpty())
        repeat(100) { store.record(DiagnosticEvent.PARTIAL, metrics = mapOf(DiagnosticMetric.CHARACTERS to it.toLong())) }
        assertTrue(directory.listFiles()!!.filter { it.extension == "jsonl" }.sumOf { it.length() } <= 2048)
        assertEquals(99L, store.snapshot().records.last().metrics[DiagnosticMetric.CHARACTERS])
        assertTrue(store.snapshot().records.size < 100)
    }

    @Test fun `corrupted records do not prevent exporting valid evidence`() {
        val directory = temporary.newFolder()
        val store = DiagnosticStore(directory)
        store.record(DiagnosticEvent.APP_STARTED)
        directory.listFiles()!!.first { it.extension == "jsonl" }.appendText("{broken\n")
        store.record(DiagnosticEvent.RECORDER_STARTED)
        val snapshot = store.snapshot()
        assertEquals(2, snapshot.records.size)
        assertEquals(1, snapshot.unreadableRecords)
    }
}
