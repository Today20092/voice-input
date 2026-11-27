package org.futo.voiceinput

import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.MANUALLY_SELECT_LANGUAGE
import org.futo.voiceinput.settings.SpeechBackendType
import org.futo.voiceinput.settings.SPEECH_BACKEND
import org.futo.voiceinput.settings.toSpeechBackendType
import org.junit.Assert.*
import org.junit.Test

class LanguagePickerTest {
    @Test fun manualSelectionIsOnByDefaultButTheDefaultSetupNeverAsks() {
        assertTrue(MANUALLY_SELECT_LANGUAGE.default)
        assertFalse(needsLanguagePicker(SPEECH_BACKEND.default.toSpeechBackendType(),
            MANUALLY_SELECT_LANGUAGE.default, LANGUAGE_TOGGLES.default))
    }

    @Test fun onlyWhisperWithSeveralLanguagesAndManualSelectionAsks() {
        val two = setOf("en", "fr")
        SpeechBackendType.values().forEach {
            assertEquals(it == SpeechBackendType.WhisperGGML, needsLanguagePicker(it, true, two))
        }
        assertFalse(needsLanguagePicker(SpeechBackendType.WhisperGGML, false, two))
        assertFalse(needsLanguagePicker(SpeechBackendType.WhisperGGML, true, setOf("en")))
    }
}
