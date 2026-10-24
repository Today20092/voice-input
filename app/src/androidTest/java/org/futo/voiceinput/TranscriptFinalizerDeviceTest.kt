package org.futo.voiceinput

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.settings.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class TranscriptFinalizerDeviceTest {
    @Test fun diagnosticResourceFailureDoesNotReplaceMissingModelFallback() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(base.cacheDir, "diagnostic-isolation-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = directory
            override fun getApplicationInfo(): ApplicationInfo = throw IllegalStateException("resource unavailable")
        }
        val enabled = base.getSetting(S1_MINI_ENABLED)
        try {
            base.setSetting(S1_MINI_ENABLED, true)
            val result = org.futo.voiceinput.s1.S1MiniTranscriptCleaner.clean(context, "Keep this text",
                SpeechBackendType.Phonon, "en", null) { }
            assertEquals("Keep this text", result.text)
            assertEquals("model_not_installed", result.fallbackCategory)
        } finally {
            base.setSetting(S1_MINI_ENABLED, enabled)
            directory.deleteRecursively()
        }
    }

    @Test fun sharedEntryUsesCurrentCleanupPreferencesAndDetectedLanguage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val s1 = context.getSetting(S1_MINI_ENABLED)
        val harper = context.getSetting(HARPER_ENABLED)
        try {
            context.setSetting(S1_MINI_ENABLED, false)
            context.setSetting(HARPER_ENABLED, true)
            val cleaned = TranscriptFinalizer.clean(context, "i  am here .", SpeechBackendType.Phonon,
                "en", null, "", null)
            assertEquals("I am here.", cleaned.text)
            val nonEnglish = TranscriptFinalizer.clean(context, "bonjour john", SpeechBackendType.Phonon,
                "fr", null, "john => Jon", null)
            assertEquals("bonjour Jon", nonEnglish.text)
            context.setSetting(HARPER_ENABLED, false)
            val disabled = TranscriptFinalizer.clean(context, "i  am here .", SpeechBackendType.Phonon,
                "en", null, "", null)
            assertEquals("i  am here .", disabled.text)
        } finally {
            context.setSetting(S1_MINI_ENABLED, s1)
            context.setSetting(HARPER_ENABLED, harper)
        }
    }
}
