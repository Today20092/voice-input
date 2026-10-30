package org.futo.voiceinput.harper

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal object HarperNative {
    init { System.loadLibrary("harper") }
    external fun clean(text: ByteArray, vocabulary: ByteArray): ByteArray?
}

data class HarperCleanupResult(val text: String, val edits: Int = 0, val outcome: Int = UNCHANGED) {
    companion object {
        const val APPLIED = 0
        const val DISABLED = 1
        const val LANGUAGE_BYPASS = 2
        const val TOO_LONG = 3
        const val UNAVAILABLE = 4
        const val UNCHANGED = 5
    }
}

object HarperTranscriptCleaner {
    @Serializable
    private data class NativeResult(val text: String, val edits: Int)

    // Production and CI cross the same cleanup interface. Only the native adapter varies.
    internal fun clean(
        text: String, vocabulary: String, enabled: Boolean, english: Boolean,
        native: (ByteArray, ByteArray) -> ByteArray? = { input, dictionary -> HarperNative.clean(input, dictionary) }
    ): HarperCleanupResult {
        if (!enabled) return HarperCleanupResult(text, outcome = HarperCleanupResult.DISABLED)
        if (!english || HarperEnglishGate.containsNonLatinLetters(text)) {
            return HarperCleanupResult(text, outcome = HarperCleanupResult.LANGUAGE_BYPASS)
        }
        if (text.isBlank()) return HarperCleanupResult(text)
        if (text.length > 10_000 || vocabulary.length > 25_000) {
            return HarperCleanupResult(text, outcome = HarperCleanupResult.TOO_LONG)
        }
        return try {
            val bytes = native(text.toByteArray(Charsets.UTF_8), vocabulary.toByteArray(Charsets.UTF_8))
                ?: return HarperCleanupResult(text, outcome = HarperCleanupResult.UNAVAILABLE)
            val result = Json.decodeFromString<NativeResult>(bytes.toString(Charsets.UTF_8))
            if (result.text.isBlank() || result.edits < 0) return HarperCleanupResult(text, outcome = HarperCleanupResult.UNAVAILABLE)
            HarperCleanupResult(result.text, if (result.text == text) 0 else result.edits,
                if (result.text == text) HarperCleanupResult.UNCHANGED else HarperCleanupResult.APPLIED)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: LinkageError) {
            HarperCleanupResult(text, outcome = HarperCleanupResult.UNAVAILABLE)
        } catch (_: OutOfMemoryError) {
            HarperCleanupResult(text, outcome = HarperCleanupResult.UNAVAILABLE)
        } catch (_: Exception) {
            // No transcript or vocabulary in Logcat or exception messages.
            HarperCleanupResult(text, outcome = HarperCleanupResult.UNAVAILABLE)
        }
    }
}
