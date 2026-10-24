package org.futo.voiceinput.s1

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.TranscriptFinalizer
import org.futo.voiceinput.settings.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class S1MiniShortTranscriptCleanupTest {
    @Test fun optInBypassKeepsLanguageModelAndVocabularyBoundaries() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(base.cacheDir, "short-cleanup-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = directory
            override fun getApplicationInfo(): ApplicationInfo = error("Runtime metadata unavailable")
        }
        val enabled = base.getSetting(S1_MINI_ENABLED)
        val skip = base.getSetting(S1_MINI_SKIP_SHORT_TRANSCRIPTS)
        val harper = base.getSetting(HARPER_ENABLED)
        val capture = base.getSetting(S1_MINI_TRANSCRIPT_DIAGNOSTICS)
        try {
            assertFalse(S1_MINI_SKIP_SHORT_TRANSCRIPTS.default)
            base.setSetting(S1_MINI_ENABLED, true)
            base.setSetting(HARPER_ENABLED, false)
            base.setSetting(S1_MINI_TRANSCRIPT_DIAGNOSTICS, true)
            base.setSetting(S1_MINI_SKIP_SHORT_TRANSCRIPTS, false)
            suspend fun clean(text: String = "Sounds good, john", language: String = "en",
                backend: SpeechBackendType = SpeechBackendType.Phonon) =
                S1MiniTranscriptCleaner.clean(context, text, backend, language, null) {
                    fail("These missing-model or bypass cases must not run inference")
                }
            assertEquals("model_not_installed", clean().fallbackCategory)
            base.setSetting(S1_MINI_SKIP_SHORT_TRANSCRIPTS, true)
            assertEquals("short_transcript_bypass", clean().fallbackCategory)
            assertEquals("Sounds good, john", clean().text)
            assertFalse(clean().applied)
            assertEquals("model_not_installed", clean("um, hello").fallbackCategory)
            assertEquals("non_english_bypass", clean(language = "fr").fallbackCategory)
            assertEquals("built_in_cleanup_bypass", clean(backend = SpeechBackendType.Asr4all).fallbackCategory)
            val final = TranscriptFinalizer.clean(context, "Sounds good, john", SpeechBackendType.Phonon,
                "en", null, "john => Jon", null)
            assertEquals("Sounds good, Jon", final.text)
            assertNotNull(final.capture)
            assertEquals("Sounds good, john", final.capture!!.raw.text)
            assertEquals(S1MiniTranscriptStageStatus.NotProduced, final.capture.cleaned.status)
            assertEquals("short_transcript_bypass", final.capture.failureOrBypassReason)
            base.setSetting(S1_MINI_ENABLED, false)
            assertNull(clean().fallbackCategory)
        } finally {
            base.setSetting(S1_MINI_ENABLED, enabled)
            base.setSetting(S1_MINI_SKIP_SHORT_TRANSCRIPTS, skip)
            base.setSetting(HARPER_ENABLED, harper)
            base.setSetting(S1_MINI_TRANSCRIPT_DIAGNOSTICS, capture)
            directory.deleteRecursively()
        }
    }
}
