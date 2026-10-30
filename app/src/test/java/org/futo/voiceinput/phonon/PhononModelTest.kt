package org.futo.voiceinput.phonon

import org.futo.voiceinput.recognition.*
import org.futo.voiceinput.settings.SpeechBackendType
import org.futo.voiceinput.settings.toSpeechBackendType
import org.futo.voiceinput.settings.pages.presentRecognitionModel
import org.futo.voiceinput.settings.pages.recognitionLanguageGuidance
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhononModelTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun optionalModelUsesSharedSelectionAndPresentation() {
        val model = PhononModel.model
        assertEquals(SpeechBackendType.Phonon, model.runtimeId.toSpeechBackendType())
        assertEquals("orukeet", RecognitionModelCatalog.defaultModel.runtimeId)
        assertEquals(listOf(model), RecognitionModelCatalog.cards.single { it.id == "phonon-2" }.models)
        val lifecycle = RecognitionModelLifecycle(RecognitionModelStore(temporaryFolder.root))
        val selection = lifecycle.selectionFor(model)
        assertEquals(model, lifecycle.readiness(selection)?.model)
        assertFalse(lifecycle.readiness(selection)!!.isReady)
        assertEquals(TranscriptionBehavior.FINAL_ONLY, model.transcription)
        assertEquals(186_618_134L, model.transferBytes)
        assertEquals(255_400_263L, model.artifacts.sumOf { it.sizeBytes })
        assertEquals("c_weights", model.archiveRoot)
        val presentation = presentRecognitionModel(model, installed = false, selected = false)
        assertTrue(presentation.compactSummary.contains("186.6 MB download"))
        assertEquals("255.4 MB", presentation.fields.toMap()["Model size"])
        assertTrue(presentation.fields.toMap().getValue("Runtime").contains("phonon-2-c"))
        assertTrue(presentation.description.contains("experimental"))
        assertTrue(recognitionLanguageGuidance(model).startsWith("English only"))
        assertEquals(2, presentation.informationLinks.size)
        assertTrue(presentation.informationLinks.last().url.endsWith(model.version))
    }
}
