package org.futo.voiceinput.asr4all

import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.futo.voiceinput.recognition.RecognitionModelLifecycle
import org.futo.voiceinput.recognition.RecognitionModelSelection
import org.futo.voiceinput.recognition.RecognitionModelStore
import org.futo.voiceinput.recognition.TranscriptionBehavior
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Asr4allContractTest {
    @Test fun catalogAndSelectionRetainAllThreePinnedVariants() {
        val models = Asr4allModels.models
        assertEquals(setOf("s", "m", "l"), models.map { it.variantId }.toSet())
        assertEquals(models, RecognitionModelCatalog.cards.single { it.id == "asr4all" }.models)
        val lifecycle = RecognitionModelLifecycle(RecognitionModelStore(File("unused-test-models")))
        models.forEach { model ->
            assertEquals(TranscriptionBehavior.LIVE, model.transcription)
            assertEquals("English", model.recognitionLanguages)
            assertEquals(model, lifecycle.readiness(lifecycle.selectionFor(model))?.model)
            assertTrue(model.artifacts.all { it.url.contains("/resolve/${model.version}/") })
        }
        assertEquals("m", lifecycle.readiness(RecognitionModelSelection("asr4all"))?.model?.variantId)
        assertEquals("m", lifecycle.readiness(RecognitionModelSelection("asr4all", asr4allVariantId = "s-plus"))?.model?.variantId)
    }

    @Test fun pauseBucketsMatchReferenceThresholds() {
        assertEquals(listOf(0, 1, 2, 2, 3, 4, 5, 6, 7, 8, 8),
            listOf(0, 1, 2, 3, 4, 8, 16, 32, 64, 128, 200).map(::pauseBucket))
    }

    @Test fun summaryUsesPlainLanguageAndDetailsExplainBuiltInCleanup() {
        Asr4allModels.models.forEach { model ->
            val presentation = org.futo.voiceinput.settings.pages.presentRecognitionModel(model, false, false)
            assertFalse(presentation.description.contains("ExecuTorch"))
            assertFalse(presentation.description.contains("PCEC"))
            assertTrue(presentation.description.length < 120)
            assertTrue(presentation.details.contains("S1-mini is skipped"))
            assertTrue(presentation.details.contains("XNNPACK INT8"))
        }
    }

    @Test fun pcecCollapsesRepeatedSlotsAndRendersControlsAndSentenceCase() {
        val controls = mapOf("cap" to 6, "allcaps" to 7, "period" to 8, "comma" to 9, "question" to 10)
        val vocab = listOf(" hello", " world", " nasa", " is", " here", " ali")
        assertEquals("Hello, world. NASA is here? Ali", renderPcec(vocab, 11, controls,
            listOf(0, 0, 9, 11, 1, 8, 7, 2, 3, 4, 10, 6, 5)))
        assertEquals("Hello hello", renderPcec(vocab, 11, controls, listOf(0, 11, 0)))
        assertEquals("", renderPcec(vocab, 11, controls, listOf(11, 8, 6)))
    }
}
