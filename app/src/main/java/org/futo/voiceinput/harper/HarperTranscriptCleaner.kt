package org.futo.voiceinput.harper

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.futo.voiceinput.settings.HARPER_ENABLED
import org.futo.voiceinput.settings.HARPER_EXPLICIT_ENGLISH
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.NEMOTRON_MULTILINGUAL_LANGUAGE
import org.futo.voiceinput.settings.NEMOTRON_PROFILE
import org.futo.voiceinput.settings.SpeechBackendType
import org.futo.voiceinput.settings.getSetting
import org.json.JSONObject

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
    suspend fun clean(
        context: Context, text: String, vocabulary: String, backend: SpeechBackendType,
        detectedLanguage: String?, forcedLanguage: String?
    ): HarperCleanupResult {
        if (!context.getSetting(HARPER_ENABLED)) return HarperCleanupResult(text, outcome = HarperCleanupResult.DISABLED)
        val english = HarperEnglishGate.isEstablishedEnglish(
            backend, detectedLanguage, forcedLanguage,
            context.getSetting(NEMOTRON_PROFILE), context.getSetting(NEMOTRON_MULTILINGUAL_LANGUAGE),
            context.getSetting(LANGUAGE_TOGGLES), context.getSetting(HARPER_EXPLICIT_ENGLISH)
        )
        if (!english || HarperEnglishGate.containsNonLatinLetters(text)) {
            return HarperCleanupResult(text, outcome = HarperCleanupResult.LANGUAGE_BYPASS)
        }
        if (text.isBlank()) return HarperCleanupResult(text)
        if (text.length > 10_000 || vocabulary.length > 25_000) {
            return HarperCleanupResult(text, outcome = HarperCleanupResult.TOO_LONG)
        }
        return withContext(Dispatchers.Default) {
            try {
                val bytes = HarperNative.clean(text.toByteArray(Charsets.UTF_8), vocabulary.toByteArray(Charsets.UTF_8))
                    ?: return@withContext HarperCleanupResult(text, outcome = HarperCleanupResult.UNAVAILABLE)
                val result = JSONObject(bytes.toString(Charsets.UTF_8))
                val cleaned = result.getString("text")
                if (cleaned.isBlank()) return@withContext HarperCleanupResult(text, outcome = HarperCleanupResult.UNAVAILABLE)
                HarperCleanupResult(cleaned, result.getInt("edits"),
                    if (cleaned == text) HarperCleanupResult.UNCHANGED else HarperCleanupResult.APPLIED)
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
}
