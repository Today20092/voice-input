package org.futo.voiceinput

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.history.AudioHistoryStore
import org.futo.voiceinput.s1.S1MiniCleanupResult
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class TranscriptFinalizerTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun cleanupPrecedesVocabularyForBothS1SuccessAndFallback() {
        for (s1 in listOf(
            S1MiniCleanupResult("i met john", true),
            S1MiniCleanupResult("i met john", false, fallbackCategory = "service_failure")
        )) {
            val result = TranscriptFinalizer.finish(s1, "John => Jon", true, true) { text, _ ->
                assertEquals("i met john", text.toString(Charsets.UTF_8))
                """{"text":"I met John.","edits":2}""".toByteArray()
            }
            assertEquals("I met Jon.", result.text)
            assertFalse(result.validEmpty)
        }
    }

    @Test fun disabledAndNonEnglishHarperStillApplyPersonalVocabulary() {
        for ((enabled, english) in listOf(false to true, true to false)) {
            val result = TranscriptFinalizer.finish(S1MiniCleanupResult("bonjour john", false),
                "john => Jon", enabled, english) { _, _ -> error("Native must be bypassed") }
            assertEquals("bonjour Jon", result.text)
        }
    }

    @Test fun validEmptyS1OutputStaysEmptyWithoutRevivingRawText() {
        val result = TranscriptFinalizer.finish(S1MiniCleanupResult("", true, validEmpty = true),
            "john => Jon", true, true) { _, _ -> error("Empty text needs no native cleanup") }
        assertEquals("", result.text)
        assertTrue(result.validEmpty)
        assertFalse(TranscriptFinalizer.finish(S1MiniCleanupResult("", false), "", false, false).validEmpty)
    }

    @Test fun unavailableHarperKeepsS1ResultAndVocabulary() {
        val result = TranscriptFinalizer.finish(S1MiniCleanupResult("Hello john.", true),
            "john => Jon", true, true) { _, _ -> throw UnsatisfiedLinkError() }
        assertEquals("Hello Jon.", result.text)
    }

    @Test fun historyStoresSharedResultButPreservesPreviousTranscriptOnCancellationOrFailure() = runBlocking {
        val store = AudioHistoryStore(temporary.newFolder())
        val recording = store.begin().apply { append(shortArrayOf(1), 1); close() }
        store.saveTranscript(recording.id, "Previous text")
        assertThrows(CancellationException::class.java) {
            runBlocking {
                store.retranscribe(recording.id) {
                    TranscriptFinalizer.finish(S1MiniCleanupResult("hello", false), "", true, true) { _, _ ->
                        throw CancellationException()
                    }.text
                }
            }
        }
        assertEquals("Previous text", store.transcript(recording.id))
        assertThrows(IllegalStateException::class.java) {
            runBlocking { store.retranscribe(recording.id) { error("Recognition failed") } }
        }
        assertEquals("Previous text", store.transcript(recording.id))
        val final = store.retranscribe(recording.id) {
            TranscriptFinalizer.finish(S1MiniCleanupResult("i met john", false), "John => Jon", true, true) { _, _ ->
                """{"text":"I met John.","edits":2}""".toByteArray()
            }.text
        }
        assertEquals("I met Jon.", final)
        assertEquals(final, store.transcript(recording.id))
        store.retranscribe(recording.id) {
            TranscriptFinalizer.finish(S1MiniCleanupResult("", true, validEmpty = true), "", true, true).text
        }
        assertEquals("", store.transcript(recording.id))
    }
}
