package org.futo.voiceinput.harper

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarperTranscriptCleanerTest {
    @Test fun disabledUnknownLanguageAndOversizedInputsNeverCallNative() {
        val never: (ByteArray, ByteArray) -> ByteArray? = { _, _ -> error("Native must not run") }
        assertEquals(HarperCleanupResult.DISABLED,
            HarperTranscriptCleaner.clean("i am here", "", false, true, never).outcome)
        assertEquals(HarperCleanupResult.LANGUAGE_BYPASS,
            HarperTranscriptCleaner.clean("i am here", "", true, false, never).outcome)
        assertEquals(HarperCleanupResult.LANGUAGE_BYPASS,
            HarperTranscriptCleaner.clean("hello مرحبا", "", true, true, never).outcome)
        assertEquals(HarperCleanupResult.TOO_LONG,
            HarperTranscriptCleaner.clean("i".repeat(10_001), "", true, true, never).outcome)
    }

    @Test fun nativeAdapterReceivesUtf8AndPersonalVocabulary() {
        val result = HarperTranscriptCleaner.clean("😀 i am here", "Ayoub", true, true) { text, words ->
            assertEquals("😀 i am here", text.toString(Charsets.UTF_8))
            assertEquals("Ayoub", words.toString(Charsets.UTF_8))
            """{"text":"😀 I am here","edits":1}""".toByteArray(Charsets.UTF_8)
        }
        assertEquals("😀 I am here", result.text)
        assertEquals(1, result.edits)
        assertEquals(HarperCleanupResult.APPLIED, result.outcome)
    }

    @Test fun nativeFailuresMalformedAndBlankResultsPreserveOriginal() {
        val original = "Do not change $1,000 or Haithum."
        val adapters: List<(ByteArray, ByteArray) -> ByteArray?> = listOf(
            { _, _ -> null },
            { _, _ -> throw UnsatisfiedLinkError() },
            { _, _ -> throw IllegalStateException() },
            { _, _ -> "not json".toByteArray() },
            { _, _ -> """{"text":"","edits":1}""".toByteArray() }
        )
        for (adapter in adapters) {
            val result = HarperTranscriptCleaner.clean(original, "", true, true, adapter)
            assertEquals(original, result.text)
            assertEquals(HarperCleanupResult.UNAVAILABLE, result.outcome)
        }
    }

    @Test fun cancellationIsNotConvertedIntoSuccessfulDelivery() {
        var cancelled = false
        try {
            HarperTranscriptCleaner.clean("text", "", true, true) { _, _ -> throw CancellationException() }
        } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }
}
