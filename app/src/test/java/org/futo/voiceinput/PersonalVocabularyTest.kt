package org.futo.voiceinput

import org.futo.voiceinput.settings.SpeechBackendType
import org.futo.voiceinput.settings.toSpeechBackendType
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalVocabularyTest {
    @Test
    fun explicitAliasesAndVocabularyTermsCorrectTranscript() {
        val vocabulary = """
            photo => FUTO
            Moonshine
            Parakeet TDT
        """.trimIndent()

        assertEquals(
            "FUTO runs Moonshine and Parakeet TDT.",
            PersonalVocabulary.apply("photo runs Moonshne and parakeet tdt.", vocabulary)
        )
    }

    @Test
    fun shortWordsAreNotFuzzilyReplaced() {
        assertEquals("cut the paper", PersonalVocabulary.apply("cut the paper", "cat"))
    }

    @Test
    fun legacyCommaSeparatedTermsStillWork() {
        assertEquals(
            "FUTO uses OpenAI and FUTO",
            PersonalVocabulary.apply("futo uses Open AI and futo", "FUTO, OpenAI")
        )
    }

    @Test
    fun moonshineBackendIdIsParsed() {
        assertEquals(SpeechBackendType.Moonshine, "moonshine".toSpeechBackendType())
    }
}
