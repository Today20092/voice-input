package org.futo.voiceinput.harper

import org.futo.voiceinput.settings.SpeechBackendType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarperEnglishGateTest {
    private fun accepts(
        backend: SpeechBackendType, detected: String? = null, forced: String? = null,
        profile: String = "multilingual", language: String = "auto",
        whisper: Set<String> = setOf("en"), explicit: Boolean = false
    ) = HarperEnglishGate.isEstablishedEnglish(backend, detected, forced, profile, language, whisper, explicit)

    @Test fun multilingualModelsNeedEvidenceOrAnExplicitDeclaration() {
        for (backend in listOf(SpeechBackendType.Orukeet, SpeechBackendType.Parakeet, SpeechBackendType.Nemotron)) {
            assertFalse(accepts(backend))
            assertTrue(accepts(backend, detected = "en"))
            assertTrue(accepts(backend, explicit = true))
            assertFalse(accepts(backend, detected = "ar", explicit = true))
        }
    }

    @Test fun selectedAndDetectedLanguagesAreRespected() {
        assertTrue(accepts(SpeechBackendType.Cohere, detected = "en"))
        assertFalse(accepts(SpeechBackendType.Cohere))
        assertTrue(accepts(SpeechBackendType.Orukeet, forced = "en-US"))
        assertFalse(accepts(SpeechBackendType.Orukeet, forced = "de", explicit = true))
        assertFalse(accepts(SpeechBackendType.Nemotron, language = "es", explicit = true))
        assertTrue(accepts(SpeechBackendType.Nemotron, language = "en"))
    }

    @Test fun onlyEnglishOnlyProfilesAreImplicitlyEligible() {
        assertTrue(accepts(SpeechBackendType.Moonshine))
        assertTrue(accepts(SpeechBackendType.ParakeetUnified))
        assertTrue(accepts(SpeechBackendType.Nemotron, profile = "streaming"))
        assertTrue(accepts(SpeechBackendType.WhisperGGML))
        assertFalse(accepts(SpeechBackendType.WhisperGGML, whisper = setOf("en", "ar")))
        assertFalse(accepts(SpeechBackendType.WhisperGGML, whisper = emptySet()))
        assertFalse(accepts(SpeechBackendType.WhisperGGML, whisper = setOf("ar"), explicit = true))
    }

    @Test fun nonLatinTextIsSkippedWithoutConfusingEmojiOrAccentsWithLanguage() {
        assertFalse(HarperEnglishGate.containsNonLatinLetters("😀 I like café and cafe\u0301."))
        assertTrue(HarperEnglishGate.containsNonLatinLetters("Please send مرحبا"))
        assertTrue(HarperEnglishGate.containsNonLatinLetters("你好"))
    }
}
