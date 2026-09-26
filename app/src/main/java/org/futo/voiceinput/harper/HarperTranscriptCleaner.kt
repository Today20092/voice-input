package org.futo.voiceinput.harper

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.futo.voiceinput.settings.HARPER_ENABLED
import org.futo.voiceinput.settings.HARPER_EXPLICIT_ENGLISH
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.NEMOTRON_MULTILINGUAL_LANGUAGE
import org.futo.voiceinput.settings.NEMOTRON_PROFILE
import org.futo.voiceinput.settings.SpeechBackendType
import org.futo.voiceinput.settings.getSetting

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

    suspend fun clean(
        context: Context, text: String, vocabulary: String, backend: SpeechBackendType,
        detectedLanguage: String?, forcedLanguage: String?
    ): HarperCleanupResult {
        val enabled = context.getSetting(HARPER_ENABLED)
        if (!enabled) return clean(text, vocabulary, enabled = false, english = false)
        val english = HarperEnglishGate.isEstablishedEnglish(
            backend, detectedLanguage, forcedLanguage,
            context.getSetting(NEMOTRON_PROFILE), context.getSetting(NEMOTRON_MULTILINGUAL_LANGUAGE),
            context.getSetting(LANGUAGE_TOGGLES), context.getSetting(HARPER_EXPLICIT_ENGLISH)
        )
        return withContext(Dispatchers.Default) { clean(text, vocabulary, enabled, english) }
    }

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
