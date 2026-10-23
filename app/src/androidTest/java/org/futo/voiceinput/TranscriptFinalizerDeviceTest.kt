package org.futo.voiceinput

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.settings.*
import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptFinalizerDeviceTest {
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
