package org.futo.voiceinput.asr4all

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.s1.S1MiniTranscriptCleaner
import org.futo.voiceinput.settings.S1_MINI_ENABLED
import org.futo.voiceinput.settings.SpeechBackendType
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.settings.setSetting
import org.junit.Assert.*
import org.junit.Test

class Asr4allCleanupTest {
    @Test fun builtInCleanupBypassesS1WithoutChangingTheSavedPreference() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val saved = context.getSetting(S1_MINI_ENABLED)
        try {
            context.setSetting(S1_MINI_ENABLED, true)
            var cleaningCalled = false
            val result = S1MiniTranscriptCleaner.clean(context, "Hello, world.", SpeechBackendType.Asr4all,
                "en", null) { cleaningCalled = true }
            assertEquals("Hello, world.", result.text)
            assertFalse(result.applied)
            assertFalse(cleaningCalled)
            assertEquals("built_in_cleanup_bypass", result.fallbackCategory)
            assertTrue(context.getSetting(S1_MINI_ENABLED))
        } finally { context.setSetting(S1_MINI_ENABLED, saved) }
    }
}
