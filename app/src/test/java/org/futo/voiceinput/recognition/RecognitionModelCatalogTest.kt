package org.futo.voiceinput.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class RecognitionModelCatalogTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun catalogHasCompleteImmutableManifests() {
        val models = RecognitionModelCatalog.models

        assertEquals(6, RecognitionModelCatalog.cards.size)
        assertEquals("moonshine-small", RecognitionModelCatalog.defaultModel.id)
        assertTrue(models.isNotEmpty())
        models.forEach { model ->
            assertTrue(model.version.isNotBlank())
            assertTrue(model.artifacts.isNotEmpty())
            assertEquals(model.archive?.sizeBytes ?: model.artifacts.sumOf { it.sizeBytes }, model.transferBytes)
            model.artifacts.forEach { artifact ->
                assertTrue(artifact.url.startsWith("https://"))
                assertFalse(artifact.url.contains("/main/"))
                if (artifact.url.contains("download.moonshine.ai")) {
                    assertTrue(artifact.url.contains("?generation="))
                }
                assertEquals(64, artifact.sha256.length)
                assertTrue(artifact.sizeBytes > 0L)
            }
        }

        val nemotron = RecognitionModelCatalog.nemotronEnglishBalanced
        assertEquals("nemotron", nemotron.runtimeId)
        assertEquals("balanced", nemotron.variantId)
        assertEquals(TranscriptionBehavior.LIVE, nemotron.transcription)
        assertEquals(PerformanceClass.BALANCED, nemotron.performanceClass)
        assertEquals(4, nemotron.artifacts.size)
        assertEquals(
            "0ae73a41cd51599dc7cac9ac083d9d35de53d762ca45923505fde47a3751814b",
            nemotron.archive?.sha256
        )

        val unified = RecognitionModelCatalog.parakeetUnified
        val unifiedCard = RecognitionModelCatalog.cards.single { it.id == "parakeet-unified" }
        assertEquals("parakeet_unified", unified.runtimeId)
        assertEquals("sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-streaming-560ms", unified.directoryName)
        assertEquals(TranscriptionBehavior.LIVE, unified.transcription)
        assertEquals("English", unified.recognitionLanguages)
        assertEquals(4, unified.artifacts.size)
        assertEquals(663_048_978, unified.transferBytes)
        assertEquals(null, unified.archive)
        assertTrue(unified.artifacts.all { it.url.contains(unified.version) })
        assertEquals(listOf(unified), unifiedCard.models)
        assertTrue(unifiedCard.description.contains("buffered", ignoreCase = true))
        assertFalse(unifiedCard.description.contains("cache-aware", ignoreCase = true))
        assertFalse(unifiedCard.description.contains("80 ms", ignoreCase = true))

        val multilingual = RecognitionModelCatalog.nemotronMultilingual
        val multilingualCard = RecognitionModelCatalog.cards.single { it.id == "nemotron-multilingual" }
        assertEquals("nemotron", multilingual.runtimeId)
        assertEquals("multilingual", multilingual.variantId)
        assertEquals("2026-06-11", multilingual.version)
        assertEquals(6, multilingual.artifacts.size)
        assertEquals(683_164_180, multilingual.transferBytes)
        assertEquals(null, multilingual.archive)
        assertTrue(multilingual.artifacts.all { it.url.contains("ab43d895f5985b1bbab8b6eac8607fcdc05343f3") })
        assertTrue(multilingual.source.contains("OpenMDW 1.1"))
        assertEquals(listOf(multilingual), multilingualCard.models)
    }

    @Test
    fun downloadableModelsExposeSourceNotices() {
        RecognitionModelCatalog.models.forEach { model ->
            assertTrue(model.source.isNotBlank())
        }
    }

    @Test
    fun installIsMarkedOnlyAfterEveryArtifactValidates() {
        val modelPackage = testPackage()
        val store = RecognitionModelStore(temporaryFolder.root)
        val packageDir = store.modelDirectory(modelPackage).apply { mkdirs() }

        assertFalse(store.completeInstall(modelPackage))
        File(packageDir, "model.bin").writeText("no")
        assertFalse(store.completeInstall(modelPackage))
        File(packageDir, "model.bin").writeText("wrong")
        assertFalse(store.completeInstall(modelPackage))
        assertFalse(store.isInstalled(modelPackage))

        File(packageDir, "model.bin").writeText("valid")
        assertTrue(store.completeInstall(modelPackage))
        assertTrue(store.isInstalled(modelPackage))
    }

    @Test
    fun selectedPackageCannotBeDeletedButInactivePackageCan() = kotlinx.coroutines.runBlocking {
        val modelPackage = testPackage()
        var released = false
        val store = RecognitionModelStore(temporaryFolder.root)
        val packageDir = store.modelDirectory(modelPackage).apply { mkdirs() }
        File(packageDir, "model.bin").writeText("valid")
        assertTrue(store.completeInstall(modelPackage))

        val selectedDeletion = runCatching {
            store.delete(modelPackage, selectedModelId = modelPackage.id) { released = true }
        }.exceptionOrNull()
        assertTrue(selectedDeletion is SelectedModelDeletionException)
        assertTrue(store.isInstalled(modelPackage))

        store.delete(modelPackage, selectedModelId = "another-model") { released = true }
        assertTrue(released)
        assertFalse(packageDir.exists())
    }

    @Test
    fun selectionRequiresACompletedInstall() {
        val modelPackage = testPackage()
        val store = RecognitionModelStore(temporaryFolder.root)
        var selected = false

        assertThrows(IllegalStateException::class.java) {
            store.select(modelPackage) { selected = true }
        }
        assertFalse(selected)

        val packageDir = store.modelDirectory(modelPackage).apply { mkdirs() }
        File(packageDir, "model.bin").writeText("valid")
        assertTrue(store.completeInstall(modelPackage))
        store.select(modelPackage) { selected = true }
        assertTrue(selected)
    }

    @Test
    fun unifiedPackageUsesTheSharedInstallSelectionAndDeletionFlow() = kotlinx.coroutines.runBlocking {
        val model = RecognitionModelCatalog.parakeetUnified
        val store = RecognitionModelStore(temporaryFolder.root)
        val directory = store.modelDirectory(model).apply { mkdirs() }
        model.artifacts.forEach { artifact ->
            RandomAccessFile(File(directory, artifact.name), "rw").use {
                it.setLength(artifact.sizeBytes)
            }
        }
        File(directory, model.completionMarker).writeText("${model.id}@${model.version}")

        assertTrue(store.isInstalled(model))
        var selected = false
        store.select(model) { selected = true }
        assertTrue(selected)
        val selectedDeletion = runCatching {
            store.delete(model, selectedModelId = model.id) {}
        }.exceptionOrNull()
        assertTrue(selectedDeletion is SelectedModelDeletionException)

        store.delete(model, selectedModelId = "moonshine-small") {}
        assertFalse(directory.exists())
    }

    @Test
    fun completionMarkerIsBoundToPackageIdentityAndVersion() {
        val modelPackage = testPackage()
        val store = RecognitionModelStore(temporaryFolder.root)
        val packageDir = store.modelDirectory(modelPackage).apply { mkdirs() }
        File(packageDir, "model.bin").writeText("valid")
        File(packageDir, modelPackage.completionMarker).writeText("test-package@older")

        assertFalse(store.isInstalled(modelPackage))
        assertTrue(File(packageDir, modelPackage.completionMarker).exists())
    }

    private fun testPackage() = RecognitionModel(
        id = "test-package",
        version = "1",
        runtimeId = "test",
        variantId = null,
        directoryName = "test-package-1",
        source = "Test source",
        displayName = "Test",
        description = "Test package",
        transcription = TranscriptionBehavior.FINAL_ONLY,
        recognitionLanguages = "English",
        performanceClass = PerformanceClass.LIGHT,
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
