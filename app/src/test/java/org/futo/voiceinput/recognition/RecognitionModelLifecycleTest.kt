package org.futo.voiceinput.recognition

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.backend.SpeechBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecognitionModelLifecycleTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun selectedManagedModelsUseTheSameValidatedReadinessCheck() {
        val models = listOf(
            model("moonshine", "small"),
            model("nemotron", "balanced"),
            model("parakeet", null),
            model("parakeet_unified", null)
        )
        val lifecycle = RecognitionModelLifecycle(
            RecognitionModelStore(temporaryFolder.root),
            models
        )
        val selections = listOf(
            RecognitionModelSelection("moonshine", moonshineVariantId = "small"),
            RecognitionModelSelection("nemotron", nemotronVariantId = "balanced"),
            RecognitionModelSelection("parakeet"),
            RecognitionModelSelection("parakeet_unified")
        )

        selections.forEachIndexed { index, selection ->
            val missing = requireNotNull(lifecycle.readiness(selection, verifyHashes = true))
            assertEquals(models[index], missing.model)
            assertFalse(missing.isReady)

            install(models[index])
            assertTrue(
                requireNotNull(lifecycle.readiness(selection, verifyHashes = true)).isReady
            )

            File(temporaryFolder.root, "${models[index].directoryName}/model.bin").writeText("bad")
            assertFalse(
                requireNotNull(lifecycle.readiness(selection, verifyHashes = true)).isReady
            )
        }
    }

    @Test
    fun bundledModelIsReadyWithoutDownloadedArtifacts() {
        val model = model("parakeet", null)
        val lifecycle = RecognitionModelLifecycle(
            RecognitionModelStore(temporaryFolder.root),
            listOf(model),
            isBundled = { it.id == model.id }
        )

        assertTrue(
            requireNotNull(lifecycle.readiness(RecognitionModelSelection("parakeet"))).isReady
        )
    }

    @Test
    fun modelSelectionCarriesOnlyItsRuntimeVariant() {
        val lifecycle = RecognitionModelLifecycle(
            RecognitionModelStore(temporaryFolder.root),
            emptyList()
        )

        assertEquals(
            RecognitionModelSelection("moonshine", moonshineVariantId = "small"),
            lifecycle.selectionFor(model("moonshine", "small"))
        )
        assertEquals(
            RecognitionModelSelection("nemotron", nemotronVariantId = "balanced"),
            lifecycle.selectionFor(model("nemotron", "balanced"))
        )
        assertEquals(
            RecognitionModelSelection("parakeet"),
            lifecycle.selectionFor(model("parakeet", null))
        )
    }

    @Test
    fun failedBackendLoadClosesTheBackend() = runBlocking {
        val backend = FailingBackend()

        val failure = runCatching {
            backend.loadOrCloseOnFailure { error("load failed") }
        }.exceptionOrNull()

        assertTrue(backend.closed)
        assertEquals("load failed", failure?.message)
    }

    private fun install(model: RecognitionModel) {
        val directory = File(temporaryFolder.root, model.directoryName).apply { mkdirs() }
        File(directory, "model.bin").writeText("valid")
        File(directory, model.completionMarker).writeText("${model.id}@${model.version}")
    }

    private fun model(runtimeId: String, variantId: String?) = RecognitionModel(
        id = "$runtimeId-${variantId ?: "default"}",
        version = "1",
        runtimeId = runtimeId,
        variantId = variantId,
        directoryName = "$runtimeId-${variantId ?: "default"}",
        source = "Test",
        displayName = runtimeId,
        description = "Test model",
        transcription = TranscriptionBehavior.LIVE,
        recognitionLanguages = "English",
        performanceClass = PerformanceClass.LIGHT,
        artifacts = listOf(
            RecognitionModelArtifact(
                name = "model.bin",
                url = "https://example.com/model.bin",
                sizeBytes = 5,
                sha256 = "ec654fac9599f62e79e2706abef23dfb7c07c08185aa86db4d8695f0b718d1b3"
            )
        )
    )

    private class FailingBackend : SpeechBackend {
        var closed = false

        override suspend fun load(context: Context) = error("unused")
        override suspend fun transcribe(samples: FloatArray) = ""
        override suspend fun close() {
            closed = true
        }
    }
}
