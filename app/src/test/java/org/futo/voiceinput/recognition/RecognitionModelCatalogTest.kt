package org.futo.voiceinput.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecognitionModelCatalogTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun catalogHasCompleteImmutableManifests() {
        val models = RecognitionModelCatalog.models

        assertEquals(3, RecognitionModelCatalog.cards.size)
        assertEquals("moonshine-small", RecognitionModelCatalog.defaultModel.id)
        assertTrue(models.isNotEmpty())
        models.forEach { model ->
            assertTrue(model.version.isNotBlank())
            assertTrue(model.artifacts.isNotEmpty())
            assertEquals(model.artifacts.sumOf { it.sizeBytes }, model.transferBytes)
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
    fun selectedPackageCannotBeDeletedButInactivePackageCan() {
        val modelPackage = testPackage()
        var released = false
        val store = RecognitionModelStore(temporaryFolder.root) { released = true }
        val packageDir = store.modelDirectory(modelPackage).apply { mkdirs() }
        File(packageDir, "model.bin").writeText("valid")
        assertTrue(store.completeInstall(modelPackage))

        assertThrows(SelectedModelDeletionException::class.java) {
            store.delete(modelPackage, selectedModelId = modelPackage.id)
        }
        assertTrue(store.isInstalled(modelPackage))

        store.delete(modelPackage, selectedModelId = "another-model")
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
    fun completionMarkerIsBoundToPackageIdentityAndVersion() {
        val modelPackage = testPackage()
        val store = RecognitionModelStore(temporaryFolder.root)
        val packageDir = store.modelDirectory(modelPackage).apply { mkdirs() }
        File(packageDir, "model.bin").writeText("valid")
        File(packageDir, modelPackage.completionMarker).writeText("test-package@older")

        assertFalse(store.isInstalled(modelPackage))
        assertFalse(File(packageDir, modelPackage.completionMarker).exists())
    }

    private fun testPackage() = RecognitionModel(
        id = "test-package",
        version = "1",
        runtimeId = "test",
        variantId = null,
        directoryName = "test-package-1",
        source = "Test source",
        sourceUrl = "https://example.com/source",
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
