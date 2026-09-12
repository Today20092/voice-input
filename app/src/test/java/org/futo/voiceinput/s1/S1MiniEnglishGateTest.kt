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
    fun allBackendsAllowEnglishDictationWithoutLanguageMetadata() {
        SpeechBackendType.entries.forEach { backend ->
            assertTrue(backend.id, established(backend))
            assertTrue(backend.id, established(backend, detected = ""))
            assertTrue(backend.id, established(backend, detected = "EN"))
        }
    }

    @Test
    fun knownNonEnglishInputIsBypassedForEveryBackend() {
        SpeechBackendType.entries.forEach { backend ->
            assertFalse(backend.id, established(backend, detected = "fr"))
            assertFalse(backend.id, established(backend, forced = "fr"))
        }
    }

    @Test
    fun multilingualNemotronRequiresEnglishSelectionOrDetection() {
        assertTrue(established(SpeechBackendType.Nemotron, profile = "multilingual", nemotronLanguage = "en"))
        assertTrue(established(SpeechBackendType.Nemotron, detected = "EN", profile = "multilingual", nemotronLanguage = "auto"))
        assertFalse(established(SpeechBackendType.Nemotron, detected = "es", profile = "multilingual", nemotronLanguage = "auto"))
        assertTrue(established(SpeechBackendType.Nemotron, profile = "multilingual", nemotronLanguage = "auto"))
        assertFalse(established(SpeechBackendType.Nemotron, profile = "multilingual", nemotronLanguage = "fr"))
    }

    @Test
    fun whisperAllowsUnknownEnglishUnlessLanguagesExplicitlyExcludeIt() {
        assertTrue(established(SpeechBackendType.WhisperGGML, enabledWhisperLanguages = setOf("en")))
        assertTrue(established(SpeechBackendType.WhisperGGML, enabledWhisperLanguages = setOf("en", "es")))
        assertFalse(established(SpeechBackendType.WhisperGGML, enabledWhisperLanguages = setOf("fr", "es")))
        assertFalse(established(SpeechBackendType.WhisperGGML, forced = "fr"))
    }
}
