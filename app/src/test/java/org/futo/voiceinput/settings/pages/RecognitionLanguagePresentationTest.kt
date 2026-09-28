package org.futo.voiceinput.settings.pages

import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionLanguagePresentationTest {
    @Test
    fun multilingualGuidanceMatchesTheRuntimeLanguageControls() {
        listOf("parakeet", "orukeet").forEach {
            assertEquals(
                "25 European languages. The model recognizes the spoken language automatically; no manual language selector is available.",
                recognitionLanguageGuidance(requireNotNull(RecognitionModelCatalog.modelFor(it)))
            )
        }
        assertEquals(
            "28 languages and Auto-detect. Choose a recognition language or Auto-detect.",
            recognitionLanguageGuidance(RecognitionModelCatalog.nemotronMultilingual)
        )
        assertEquals(
            "14 languages, including Arabic and English. Choose the language before dictating. Automatic language detection is not available, and mixed-language speech may be inaccurate.",
            recognitionLanguageGuidance(RecognitionModelCatalog.cohereTranscribe)
        )
        val tdt = RecognitionModelCatalog.cards.single { it.id == "parakeet" }
        assertEquals("25 European languages", tdt.recognitionLanguages)
        assertTrue(tdt.description.contains("multilingual"))
    }

    @Test
    fun englishModelsDoNotOfferLanguageDetectionOrSelection() {
        listOf("moonshine", "nemotron", "parakeet_unified").forEach { runtime ->
            RecognitionModelCatalog.models.filter {
                it.runtimeId == runtime && it.variantId != "multilingual"
            }.forEach { model ->
                assertEquals(
                    "English only. Language selection and automatic language detection are not available.",
                    recognitionLanguageGuidance(model)
                )
            }
        }
    }
}
