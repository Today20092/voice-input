package org.futo.voiceinput.s1

import org.futo.voiceinput.settings.SpeechBackendType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class S1MiniEnglishGateTest {
    private fun established(
        backend: SpeechBackendType,
        detected: String? = null,
        forced: String? = null,
        profile: String = "balanced",
        nemotronLanguage: String = "en",
        enabledWhisperLanguages: Set<String> = setOf("en")
    ) = S1MiniEnglishGate.isEstablishedEnglish(
        backend, detected, forced, profile, nemotronLanguage, enabledWhisperLanguages
    )

    @Test
    fun englishOnlyBackendsAlwaysQualify() {
        assertTrue(established(SpeechBackendType.Moonshine))
        assertTrue(established(SpeechBackendType.Parakeet))
        assertTrue(established(SpeechBackendType.ParakeetUnified))
    }

    @Test
    fun multilingualNemotronRequiresEnglishSelectionOrDetection() {
        assertTrue(established(SpeechBackendType.Nemotron, profile = "multilingual", nemotronLanguage = "en"))
        assertTrue(established(SpeechBackendType.Nemotron, detected = "EN", profile = "multilingual", nemotronLanguage = "auto"))
        assertFalse(established(SpeechBackendType.Nemotron, detected = "es", profile = "multilingual", nemotronLanguage = "auto"))
    }

    @Test
    fun whisperAutoWithoutDetectionOnlyQualifiesWhenEnglishIsTheSoleLanguage() {
        assertTrue(established(SpeechBackendType.WhisperGGML, enabledWhisperLanguages = setOf("en")))
        assertFalse(established(SpeechBackendType.WhisperGGML, enabledWhisperLanguages = setOf("en", "es")))
        assertFalse(established(SpeechBackendType.WhisperGGML, forced = "fr"))
    }
}
