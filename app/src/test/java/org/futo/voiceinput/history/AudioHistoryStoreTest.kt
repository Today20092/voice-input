package org.futo.voiceinput.history

import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AudioHistoryStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun previewIsBoundedWithoutTruncatingTheSavedTranscript() {
        val store = AudioHistoryStore(temporary.newFolder())
        val capture = store.begin().apply { append(shortArrayOf(1), 1); close() }
        assertNull(store.entries().single().preview)
        val text = "A long transcript. ".repeat(1000)
        store.saveTranscript(capture.id, text)
        assertEquals(text.take(240) + "…", store.entries().single().preview)
        assertEquals(text, store.transcript(capture.id))
        store.saveTranscript(capture.id, "Short transcript")
        assertEquals("Short transcript", store.entries().single().preview)
    }

    @Test fun clearPreservesCaptureAndReplayAndDoesNotResurrectDeletedText() = runBlocking {
        val store = AudioHistoryStore(temporary.newFolder())
        val saved = store.begin().apply { append(shortArrayOf(1), 1); close() }
        store.saveTranscript(saved.id, "Saved text")
        val replay = store.begin().apply { append(shortArrayOf(2), 1); close() }
        val recording = store.begin().apply { append(shortArrayOf(3), 1) }
        try {
            store.retranscribe(replay.id) {
                assertEquals(AudioHistoryStore.ClearResult(1, 2, 0), store.clear())
                assertEquals(setOf(recording.id, replay.id), store.entries().map { it.id }.toSet())
                store.saveTranscript(saved.id, "A late write must not restore a cleared entry")
                assertNull(store.transcript(saved.id))
                "Replayed text"
            }
        } finally { recording.close() }
        assertEquals("Replayed text", store.transcript(replay.id))
        assertEquals(AudioHistoryStore.ClearResult(2, 0, 0), store.clear())
        assertTrue(store.entries().isEmpty())
    }

    @Test fun clearReportsFailuresAndContinuesDeletingOtherEntries() {
        val root = temporary.newFolder()
        val store = AudioHistoryStore(root)
        val blocked = store.begin().apply { append(shortArrayOf(1), 1); close() }
        val removable = store.begin().apply { append(shortArrayOf(2), 1); close() }
        store.saveTranscript(removable.id, "Remove this text")
        // A nonempty directory at the temporary-file path simulates a deletion failure.
        val obstacle = File(root, "${blocked.id}.txt.tmp").apply { mkdir() }
        File(obstacle, "child").writeText("Keep the directory nonempty")
        assertEquals(AudioHistoryStore.ClearResult(1, 0, 1), store.clear())
        assertEquals(blocked.id, store.entries().single().id)
        assertNull(store.transcript(removable.id))
    }

    @Test fun repeatedCloseCannotReleaseANewerReplayPin() = runBlocking {
        val store = AudioHistoryStore(temporary.newFolder())
        val capture = store.begin(1000).apply { append(shortArrayOf(1), 1); close() }
        store.retranscribe(capture.id) {
            capture.close()
            capture.close()
            store.purge(1, 1000 + 2 * 3_600_000L)
            assertTrue(store.entries().single().busy)
            assertFalse(store.delete(capture.id))
            "Recovered text"
        }
        assertTrue(store.delete(capture.id))
    }

    @Test fun replayOwnsPinAndReplacesTextOnlyOnSuccess() = runBlocking {
        val store = AudioHistoryStore(temporary.newFolder())
        val capture = store.begin(1000).apply { append(shortArrayOf(Short.MAX_VALUE), 1); close() }
        store.saveTranscript(capture.id, "Previous text")
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                store.retranscribe(capture.id) {
                    assertFalse(store.delete(capture.id))
                    error("Recognition failed")
                }
            }
        }
        assertEquals("Previous text", store.transcript(capture.id))
        assertFalse(store.entries().single().busy)
        launch {
            store.retranscribe(capture.id) {
                currentCoroutineContext().cancel()
                "Cancelled attempt"
            }
        }.join()
        assertEquals("Previous text", store.transcript(capture.id))
        assertFalse(store.entries().single().busy)
        assertEquals("Recovered text", store.retranscribe(capture.id) { samples ->
            assertArrayEquals(floatArrayOf(1f), samples, 0f)
            store.purge(1, 1000 + 2 * 3_600_000L)
            assertTrue(store.entries().single().busy)
            "Recovered text"
        })
        assertEquals("Recovered text", store.transcript(capture.id))
        assertFalse(store.entries().single().busy)
    }

    @Test fun capturesIncrementallyAndReadsAnInterruptedRecording() = runBlocking {
        val root = temporary.newFolder()
        val store = AudioHistoryStore(root)
        val capture = store.begin(1000)
        try {
            capture.append(shortArrayOf(Short.MIN_VALUE, 0, Short.MAX_VALUE, 100), 3)
            // Bytes are recoverable before close, rather than only after recognition succeeds.
            assertEquals(6L, File(root, "${capture.id}.pcm").length())
            assertTrue(store.entries().single().busy)
            assertFalse(store.delete(capture.id))
            capture.finishWriting()
            // Keep the recording protected while its original transcript is being produced.
            assertTrue(store.entries().single().busy)
            assertFalse(store.delete(capture.id))
            assertThrows(IllegalStateException::class.java) {
                runBlocking { store.retranscribe(capture.id) { "Must not run" } }
            }
        } finally { capture.close() }
        // A killed process can leave an incomplete final sample. Preserve every complete sample.
        File(root, "${capture.id}.pcm").appendBytes(byteArrayOf(12))
        val reopened = AudioHistoryStore(root)
        reopened.retranscribe(capture.id) { samples ->
            assertArrayEquals(floatArrayOf(Short.MIN_VALUE.toFloat() / Short.MAX_VALUE, 0f, 1f), samples, 0f)
            "Recovered words"
        }
        assertFalse(reopened.entries().single().busy)
        reopened.saveTranscript(capture.id, "Second attempt")
        assertEquals("Second attempt", reopened.transcript(capture.id))
    }

    @Test fun retentionDeletesAudioAndTextButPreservesActiveCaptureAndReplay() = runBlocking {
        val store = AudioHistoryStore(temporary.newFolder())
        val old = store.begin(1000).apply { append(shortArrayOf(1), 1); close() }
        store.saveTranscript(old.id, "Old text")
        val active = store.begin(1001).apply { append(shortArrayOf(2), 1) }
        val now = 1000 + 2 * 3_600_000L
        val recent = store.begin(now).apply { append(shortArrayOf(3), 1); close() }
        store.retranscribe(old.id) {
            store.purge(2, now)
            assertEquals(3, store.entries().size)
            "Old text"
        }
        store.purge(2, now)
        assertNull(store.transcript(old.id))
        assertEquals(setOf(active.id, recent.id), store.entries().map { it.id }.toSet())
        active.close()
        store.purge(1, now)
        assertEquals(recent.id, store.entries().single().id)
        store.delete(recent.id)
        store.saveTranscript(recent.id, "Do not resurrect deleted audio")
        assertNull(store.transcript(recent.id))
        assertTrue(store.entries().isEmpty())
    }

    @Test fun emptyCapturesAreRemovedAndInvalidIdsAreRejected() {
        val store = AudioHistoryStore(temporary.newFolder())
        store.begin().close()
        assertTrue(store.entries().isEmpty())
        assertThrows(IllegalArgumentException::class.java) { store.delete("../other") }
        assertThrows(IllegalArgumentException::class.java) { store.saveTranscript("../other", "text") }
    }

    @Test fun failedTranscriptWritePreservesPreviousTextAndAudio() {
        val root = temporary.newFolder()
        val store = AudioHistoryStore(root)
        val capture = store.begin().apply { append(shortArrayOf(10), 1); close() }
        store.saveTranscript(capture.id, "Keep this text")
        // Simulate an unavailable write target without depending on filesystem permissions.
        File(root, "${capture.id}.txt.tmp").mkdir()
        assertThrows(Exception::class.java) { store.saveTranscript(capture.id, "Replacement") }
        assertEquals("Keep this text", store.transcript(capture.id))
        assertEquals(2L, File(root, "${capture.id}.pcm").length())
    }
}
