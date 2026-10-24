package org.futo.voiceinput

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.futo.voiceinput.diagnostics.DiagnosticEvent
import org.futo.voiceinput.diagnostics.DiagnosticMetric
import org.futo.voiceinput.diagnostics.DiagnosticSession
import org.futo.voiceinput.harper.HarperCleanupResult
import org.futo.voiceinput.harper.HarperEnglishGate
import org.futo.voiceinput.harper.HarperNative
import org.futo.voiceinput.harper.HarperTranscriptCleaner
import org.futo.voiceinput.s1.S1MiniCleanupResult
import org.futo.voiceinput.s1.S1MiniTranscriptCleaner
import org.futo.voiceinput.s1.S1MiniTranscriptCapture
import org.futo.voiceinput.s1.S1MiniTranscriptDelivery
import org.futo.voiceinput.settings.*

internal data class FinalTranscript(val text: String, val validEmpty: Boolean, val harper: HarperCleanupResult,
    val capture: S1MiniTranscriptCapture? = null)

/** Prepares text only; callers retain ownership of history replacement and delivery. */
internal object TranscriptFinalizer {
    suspend fun clean(
        context: Context, raw: String, backend: SpeechBackendType,
        detectedLanguage: String?, forcedLanguage: String?, vocabulary: String,
        report: DiagnosticSession?, onCleaning: suspend () -> Unit = {}
    ): FinalTranscript {
        val started = SystemClock.elapsedRealtime()
        report?.event(DiagnosticEvent.CLEANUP_STARTED)
        val s1 = S1MiniTranscriptCleaner.clean(context, raw, backend, detectedLanguage, forcedLanguage, onCleaning)
        currentCoroutineContext().ensureActive()
        report?.event(DiagnosticEvent.CLEANUP_FINISHED, mapOf(
            DiagnosticMetric.DURATION_MS to SystemClock.elapsedRealtime() - started,
            DiagnosticMetric.APPLIED to if (s1.applied) 1L else 0L))
        val harperStarted = SystemClock.elapsedRealtime()
        val enabled = context.getSetting(HARPER_ENABLED)
        val english = enabled && HarperEnglishGate.isEstablishedEnglish(
            backend, detectedLanguage, forcedLanguage,
            context.getSetting(NEMOTRON_PROFILE), context.getSetting(NEMOTRON_MULTILINGUAL_LANGUAGE),
            context.getSetting(LANGUAGE_TOGGLES), context.getSetting(HARPER_EXPLICIT_ENGLISH))
        val result = withContext(Dispatchers.Default) { finish(s1, vocabulary, enabled, english) }
        report?.event(DiagnosticEvent.HARPER_FINISHED, mapOf(
            DiagnosticMetric.DURATION_MS to SystemClock.elapsedRealtime() - harperStarted,
            DiagnosticMetric.APPLIED to if (result.harper.edits > 0) 1L else 0L,
            DiagnosticMetric.HARPER_EDITS to result.harper.edits.toLong(),
            DiagnosticMetric.HARPER_OUTCOME to result.harper.outcome.toLong()))
        val capture = try {
            if (s1.diagnosticReportId != null && context.getSetting(S1_MINI_TRANSCRIPT_DIAGNOSTICS)) {
                S1MiniTranscriptCapture.create(s1.diagnosticReportId, System.currentTimeMillis(), raw,
                    s1.text.takeIf { s1.applied }, result.text, s1.fallbackCategory,
                    S1MiniTranscriptDelivery.Prepared)
            } else null
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (_: Throwable) { null }
        return result.copy(capture = capture)
    }

    // The native boundary varies in JVM tests; cleanup guards and vocabulary stay real.
    internal fun finish(
        s1: S1MiniCleanupResult, vocabulary: String, harperEnabled: Boolean, english: Boolean,
        native: (ByteArray, ByteArray) -> ByteArray? = { text, words -> HarperNative.clean(text, words) }
    ): FinalTranscript {
        val harper = HarperTranscriptCleaner.clean(s1.text, vocabulary, harperEnabled, english, native)
        return FinalTranscript(PersonalVocabulary.apply(harper.text, vocabulary), s1.validEmpty, harper)
    }
}
