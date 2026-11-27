package org.futo.voiceinput

import org.futo.voiceinput.settings.MANUALLY_SELECT_LANGUAGE
import org.futo.voiceinput.settings.SpeechBackendType
import org.junit.Assert.*
import org.junit.Test

class LanguagePickerTest {
    @Test fun onlyWhisperAsksWhenTheToggleWasNeverSaved() {
        assertTrue(MANUALLY_SELECT_LANGUAGE.default)
        SpeechBackendType.values().forEach {
            assertEquals(it == SpeechBackendType.WhisperGGML, manualLanguageSelectionDefault(it))
        }
    }
}
