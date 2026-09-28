package org.futo.voiceinput.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.futo.voiceinput.recognition.RecognitionModelStore
import org.futo.voiceinput.sha256
import java.io.File

class DownloadRetryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun retrySkipsFilesThatAlreadyValidated() {
        val complete = ModelInfo("complete", "url", size = 10)
        val partial = ModelInfo("partial", "url", size = 20)

        assertEquals(
            listOf(partial),
            incompleteDownloads(listOf(complete, partial)) { it === complete }
        )
    }

    @Test
    fun nemotronRecoversCompletedRuntimeFilesWithoutTestRecordings() {
        // Use tiny, genuinely hashed files instead of downloading model weights.
        val model = RecognitionModelCatalog.nemotronMultilingual.let { catalog ->
            catalog.copy(artifacts = catalog.artifacts.map {
                it.copy(sizeBytes = 5, sha256 = "ec654fac9599f62e79e2706abef23dfb7c07c08185aa86db4d8695f0b718d1b3")
            })
        }
        val store = RecognitionModelStore(temporaryFolder.root)
        val directory = store.modelDirectory(model).apply { mkdirs() }
        listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt")
            .forEach { File(directory, it).writeText("valid") }
        val requests = model.artifacts.map {
            ModelInfo(it.name, it.url, File(directory, it.name), it.sha256, it.sizeBytes, it.sizeBytes)
        }
        fun pending() = incompleteDownloads(requests) {
            it.targetFile.isFile && it.targetFile.length() == it.expectedSize &&
                sha256(it.targetFile) == it.sha256
        }

        assertFalse(store.isInstalled(model))
        assertTrue(pending().isEmpty())
        assertTrue(store.completeInstall(model))
        assertTrue(store.isInstalled(model, verifyHashes = true))
        assertFalse(File(directory, "test_wavs").exists())

        File(directory, "tokens.txt").writeText("wrong")
        assertEquals(listOf("tokens.txt"), pending().map { it.name })
        assertFalse(store.completeInstall(model))
        assertFalse(store.isInstalled(model))
        File(directory, "tokens.txt").writeText("no")
        assertEquals(listOf("tokens.txt"), pending().map { it.name })
        assertFalse(store.completeInstall(model))
        assertEquals("valid", File(directory, "encoder.int8.onnx").readText())
    }
}
