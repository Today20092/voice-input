package org.futo.voiceinput.settings.pages

import org.futo.voiceinput.ENGLISH_MODELS
import org.futo.voiceinput.MULTILINGUAL_MODELS
import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.futo.voiceinput.recognition.TranscriptionBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPresentationTest {
    @Test
    fun everyModelHasTheSameCompleteDetailFields() {
        val presentations = RecognitionModelCatalog.cards.flatMap { it.models }.map {
            presentRecognitionModel(it, installed = false, selected = false)
        } + ENGLISH_MODELS.map {
            presentWhisperModel(it, "English", installed = false, selected = false)
        } + MULTILINGUAL_MODELS.map {
            presentWhisperModel(it, "Multilingual", installed = false, selected = false)
        }
        val labels = listOf(
            "Transcription", "Languages", "Language selection", "Download size", "Model size",
            "Status", "Performance class", "Source", "License/attribution", "Version", "Model files"
        )
        presentations.forEach { presentation ->
            assertEquals(presentation.title, labels, presentation.fields.map { it.first })
            assertTrue(presentation.title, presentation.description.isNotBlank())
            assertTrue(presentation.title, presentation.fields.all { it.second.isNotBlank() })
            assertTrue(presentation.title, presentation.informationLinks.isNotEmpty())
            assertTrue(presentation.title, presentation.informationLinks.all {
                it.label.isNotBlank() && it.url.startsWith("https://") && !it.url.contains("/resolve/")
            })
        }
    }

    @Test
    fun catalogDisplayGroupsByBehaviorThenSmallestDownloadWithoutDroppingModels() {
        val cards = modelCardsForDisplay()
        assertEquals(RecognitionModelCatalog.cards.map { it.id }.toSet(), cards.map { it.id }.toSet())
        assertEquals(
            RecognitionModelCatalog.models.map { it.id }.toSet(),
            cards.flatMap { it.models }.map { it.id }.toSet()
        )
        assertEquals(cards.map { it.transcription.ordinal }.sorted(), cards.map { it.transcription.ordinal })
        cards.groupBy { it.transcription }.values.forEach { group ->
            val sizes = group.map { card -> card.models.minOfOrNull { it.transferBytes } ?: 0L }
            assertEquals(sizes.sorted(), sizes)
        }
        assertEquals("moonshine", cards.first().id)
        assertEquals(
            listOf("Low latency", "Balanced", "Accuracy"),
            cards.single { it.id == "nemotron" }.models.map { it.displayName }
        )
    }

    @Test
    fun catalogDistinguishesLiveBufferedLiveAndFinalOnlyRecognition() {
        val cards = RecognitionModelCatalog.cards.associateBy { it.id }

        assertEquals(TranscriptionBehavior.LIVE, cards.getValue("moonshine").transcription)
        assertEquals(TranscriptionBehavior.LIVE, cards.getValue("nemotron").transcription)
        assertEquals(
            TranscriptionBehavior.BUFFERED_LIVE,
            cards.getValue("parakeet-unified").transcription
        )
        assertEquals(TranscriptionBehavior.FINAL_ONLY, cards.getValue("parakeet").transcription)
        assertEquals(TranscriptionBehavior.FINAL_ONLY, cards.getValue("whisper").transcription)
    }

    @Test
    fun managedRowKeepsTechnicalAttributionBehindDetails() {
        val presentation = presentRecognitionModel(
            RecognitionModelCatalog.nemotronEnglishBalanced,
            installed = true,
            selected = true
        )

        assertEquals("Balanced", presentation.title)
        assertEquals(
            "Live transcription • English\n" +
                "Download 463.9 MB • Installed 661.9 MB • Selected",
            presentation.summary
        )
        assertFalse(presentation.summary.contains("NVIDIA"))
        assertEquals("English • 463.9 MB download\nSelected", presentation.compactSummary)
        assertTrue(presentation.details.contains("Source: NVIDIA Nemotron via k2-fsa/sherpa-onnx"))
        assertTrue(presentation.details.contains("License/attribution: NVIDIA Open Model License"))
        assertTrue(presentation.details.contains("Version: 2026-04-25"))
        assertTrue(presentation.details.contains("Performance class: Balanced"))
    }

    @Test
    fun whisperVariantsExposeFinalOnlyMetadataAndExactSelection() {
        val english = presentWhisperModel(
            model = ENGLISH_MODELS[1],
            languages = "English",
            installed = false,
            selected = true
        )
        val multilingual = presentWhisperModel(
            model = MULTILINGUAL_MODELS[1],
            languages = "Multilingual",
            installed = true,
            selected = false
        )

        assertEquals("English-74 (slower, more accurate)", english.title)
        assertEquals(
            "Final-only transcription • English\n" +
                "Download 81.8 MB • Installed 81.8 MB • Selected • Download required",
            english.summary
        )
        assertEquals(
            "Final-only transcription • Multilingual\n" +
                "Download 81.8 MB • Installed 81.8 MB • Installed",
            multilingual.summary
        )
        assertTrue(english.details.contains("Source: FUTO Voice Input legacy model catalog"))
        assertTrue(english.details.contains("License/attribution: OpenAI Whisper and whisper.cpp (MIT)"))
    }

    @Test
    fun selectedNemotronProfileKeepsItsFamilyNameOutsideTheCatalog() {
        listOf(
            RecognitionModelCatalog.nemotronEnglishLowLatency to "Nemotron • Low latency",
            RecognitionModelCatalog.nemotronEnglishBalanced to "Nemotron • Balanced",
            RecognitionModelCatalog.nemotronEnglishAccuracy to "Nemotron • Accuracy",
            RecognitionModelCatalog.nemotronMultilingual to "Nemotron 3.5 Multilingual"
        ).forEach { (model, expected) ->
            assertEquals(expected, selectedRecognitionModelSummary(
                runtimeId = "nemotron",
                managedModel = model,
                englishModel = ENGLISH_MODELS[0],
                multilingualModel = MULTILINGUAL_MODELS[0],
                multilingualEnabled = false
            ))
        }
    }

    @Test
    fun selectedVariantSummaryNamesEverySelectedWhisperVariant() {
        assertEquals(
            "Whisper • English-39 (default) + Multilingual-74 (default)",
            selectedRecognitionModelSummary(
                runtimeId = "whisper_ggml",
                managedModel = null,
                englishModel = ENGLISH_MODELS[0],
                multilingualModel = MULTILINGUAL_MODELS[1],
                multilingualEnabled = true
            )
        )
        assertEquals(
            "Moonshine Medium",
            selectedRecognitionModelSummary(
                runtimeId = "moonshine",
                managedModel = RecognitionModelCatalog.moonshineMedium,
                englishModel = ENGLISH_MODELS[0],
                multilingualModel = MULTILINGUAL_MODELS[0],
                multilingualEnabled = false
            )
        )
    }
}
