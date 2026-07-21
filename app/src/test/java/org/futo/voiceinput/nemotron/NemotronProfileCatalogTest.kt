package org.futo.voiceinput.nemotron

import org.futo.voiceinput.recognition.RecognitionModelArtifact
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.futo.voiceinput.recognition.RecognitionModelStore
import org.futo.voiceinput.recognition.TranscriptionBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NemotronProfileCatalogTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun cardGroupsThreeCompleteProfileManifests() {
        val card = RecognitionModelCatalog.cards.single { it.id == "nemotron" }

        assertEquals(3, card.models.size)
        assertEquals(listOf("low_latency", "balanced", "accuracy"), card.models.map { it.variantId })
        assertTrue(card.models.all { it.runtimeId == "nemotron" })
        assertTrue(card.models.all { it.transcription == TranscriptionBehavior.LIVE })
        assertTrue(card.models.all { it.artifacts.size == 4 })
        assertEquals(listOf("80 ms", "160 ms", "560 ms"), card.models.map {
            Regex("\\d+ ms").find(it.description)?.value
        })
        assertEquals(
            listOf(
                "caaf92069dbd1ca054f8e17cab179813bc28b4585f5c392540357ece4722333d",
                "0ae73a41cd51599dc7cac9ac083d9d35de53d762ca45923505fde47a3751814b",
                "78e2b79fcf7271553a74402a76b771b09ea40117a39566a79f52235b23db6358"
            ),
            card.models.map { it.archive?.sha256 }
        )
    }

    @Test
    fun profilesKeepIndependentInstallAndDeletionState() {
        val lowLatency = testPackage("low-latency", "low-latency-1")
        val accuracy = testPackage("accuracy", "accuracy-1")
        val store = RecognitionModelStore(temporaryFolder.root)
        listOf(lowLatency, accuracy).forEach { model ->
            File(store.modelDirectory(model).apply { mkdirs() }, "model.bin").writeText("valid")
            assertTrue(store.completeInstall(model))
        }

        store.delete(accuracy, selectedModelId = lowLatency.id)

        assertTrue(store.isInstalled(lowLatency))
        assertFalse(store.isInstalled(accuracy))
    }

    @Test
    fun switchingProfilesRetainsBothInstalledPackages() {
        val lowLatency = testPackage("low-latency", "low-latency-1")
        val accuracy = testPackage("accuracy", "accuracy-1")
        val store = RecognitionModelStore(temporaryFolder.root)
        listOf(lowLatency, accuracy).forEach { model ->
            File(store.modelDirectory(model).apply { mkdirs() }, "model.bin").writeText("valid")
            assertTrue(store.completeInstall(model))
        }
        var selected = lowLatency.id

        store.select(accuracy) { selected = accuracy.id }

        assertEquals(accuracy.id, selected)
        assertTrue(store.isInstalled(lowLatency))
        assertTrue(store.isInstalled(accuracy))
    }

    private fun testPackage(id: String, directoryName: String) = RecognitionModel(
        id = id,
        version = "1",
        runtimeId = "nemotron",
        variantId = id,
        directoryName = directoryName,
        source = "Test source",
        sourceUrl = "https://example.com/source",
        displayName = id,
        description = "Test package",
        transcription = TranscriptionBehavior.LIVE,
        recognitionLanguages = "English",
        performanceClass = org.futo.voiceinput.recognition.PerformanceClass.BALANCED,
        artifacts = listOf(
            RecognitionModelArtifact(
                name = "model.bin",
                url = "https://example.com/revision/model.bin",
                sizeBytes = 5,
                sha256 = "ec654fac9599f62e79e2706abef23dfb7c07c08185aa86db4d8695f0b718d1b3"
            )
        )
    )
}
