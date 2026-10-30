package org.futo.voiceinput.recognition

import android.content.Context
import org.futo.voiceinput.backend.SpeechBackend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.futo.voiceinput.sha256
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecognitionModelUpdateTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun fixture(version: String, content: String): RecognitionModel {
        val payload = temporaryFolder.newFile().apply { writeText(content) }
        return RecognitionModel("fixture", version, "fixture", null, "fixture", "Fixture",
            displayName = "Fixture", description = "Pinned test fixture",
            transcription = TranscriptionBehavior.FINAL_ONLY, recognitionLanguages = "English",
            performanceClass = PerformanceClass.LIGHT,
            artifacts = listOf(RecognitionModelArtifact("model.bin", "https://example.com/$version/model.bin",
                payload.length(), sha256(payload))))
    }

    private fun install(store: RecognitionModelStore, model: RecognitionModel, content: String) {
        val directory = store.modelDirectory(model).apply { mkdirs() }
        File(directory, "model.bin").writeText(content)
        assertTrue(store.completeInstall(model))
    }

    private fun stage(store: RecognitionModelStore, model: RecognitionModel, content: String) {
        File(store.stagingDirectory(model).apply { mkdirs() }, "model.bin").writeText(content)
    }

    @Test fun olderPinnedVersionRemainsUsableUntilExplicitActivationAndNextLoadSeesNewFiles() = runBlocking {
        val old = fixture("1", "old")
        val successor = fixture("2", "new").copy(supportedOlderVersions = listOf(old))
        val store = RecognitionModelStore(temporaryFolder.root)
        install(store, old, "old")
        val lifecycle = RecognitionModelLifecycle(store, listOf(successor))
        val selection = RecognitionModelSelection("fixture")
        val before = requireNotNull(lifecycle.readiness(selection))
        assertTrue(before.isReady)
        assertEquals("1", before.installedModel?.version)
        assertEquals(successor, before.optionalUpgrade)
        stage(store, successor, "new")
        assertEquals("old", File(store.modelDirectory(old), "model.bin").readText())
        val revision = RecognitionModelLifecycle.invalidations.value
        lifecycle.activateInstallation(successor) { fail("An update changed the selection") }
        val after = requireNotNull(lifecycle.readiness(selection))
        assertEquals("2", after.installedModel?.version)
        assertNull(after.optionalUpgrade)
        assertEquals("new", File(store.modelDirectory(successor), "model.bin").readText())
        assertFalse(store.stagingDirectory(successor).exists())
        assertFalse(File(temporaryFolder.root, ".fixture.previous").exists())
        assertTrue(RecognitionModelLifecycle.invalidations.value > revision)
    }

    @Test fun corruptOrInterruptedCandidatePreservesWorkingInstallationAndPublishesFailure() = runBlocking {
        val old = fixture("1", "old")
        val next = fixture("2", "new").copy(supportedOlderVersions = listOf(old))
        val store = RecognitionModelStore(temporaryFolder.root)
        install(store, old, "old")
        val lifecycle = RecognitionModelLifecycle(store, listOf(next))
        stage(store, next, "bad")
        val revision = RecognitionModelLifecycle.invalidations.value
        assertTrue(runCatching { lifecycle.activateInstallation(next) {} }.isFailure)
        assertTrue(RecognitionModelLifecycle.invalidations.value > revision)
        assertEquals(old, store.installedVersion(next, true))
        assertEquals("old", File(store.modelDirectory(old), "model.bin").readText())
        store.stagingDirectory(next).deleteRecursively()
        assertEquals(old, RecognitionModelStore(temporaryFolder.root).installedVersion(next))
    }

    @Test fun failedRenameRollsBackAndProcessInterruptionRestoresPreviousVersion() {
        val old = fixture("1", "old")
        val next = fixture("2", "new").copy(supportedOlderVersions = listOf(old))
        val store = RecognitionModelStore(temporaryFolder.root, moveDirectory = { from, to ->
            if (from.name.endsWith(".staging")) false else from.renameTo(to)
        })
        install(store, old, "old")
        stage(store, next, "new")
        assertTrue(store.validateStaged(next))
        assertThrows(IllegalStateException::class.java) { store.activateStaged(next) }
        assertEquals(old, store.installedVersion(next, true))
        val backup = File(temporaryFolder.root, ".fixture.previous")
        assertTrue(store.modelDirectory(old).renameTo(backup))
        File(temporaryFolder.root, ".fixture.activating").writeText("2")
        // Simulate a process dying after publishing the new directory, before committing it.
        assertTrue(store.stagingDirectory(next).renameTo(store.modelDirectory(next)))
        assertEquals(old, RecognitionModelStore(temporaryFolder.root).installedVersion(next))
        assertFalse(backup.exists())
    }

    @Test fun cheapReadinessUsesPinnedOlderManifestWithoutHashingAndNoSuccessorMeansSilence() {
        val old = fixture("1", "old")
        val next = fixture("2", "new").copy(supportedOlderVersions = listOf(old))
        install(RecognitionModelStore(temporaryFolder.root), old, "old")
        val cheap = RecognitionModelStore(temporaryFolder.root) { error("Readiness hashed model contents") }
        assertEquals(old, cheap.installedVersion(next))
        val lifecycle = RecognitionModelLifecycle(cheap, listOf(old))
        assertNull(lifecycle.readiness(RecognitionModelSelection("fixture"))?.optionalUpgrade)
        assertTrue(RecognitionModelCatalog.models.all { it.supportedOlderVersions.isEmpty() })
    }

    @Test fun activationWaitsForRecordingIncludingBeforeRuntimeLoadAndCancellationRetainsOldVersion() = runBlocking {
        val old = fixture("1", "old")
        val next = fixture("2", "new").copy(supportedOlderVersions = listOf(old))
        val store = RecognitionModelStore(temporaryFolder.root)
        install(store, old, "old")
        stage(store, next, "new")
        val lifecycle = RecognitionModelLifecycle(store, listOf(next))
        val recording = lifecycle.acquireSession(old)
        try {
            val canceledUpdate = launch(start = CoroutineStart.UNDISPATCHED) {
                lifecycle.activateInstallation(next) { fail("Update changed selection") }
            }
            assertTrue(canceledUpdate.isActive)
            assertEquals(old, store.installedVersion(next))
            canceledUpdate.cancel()
            canceledUpdate.join()
            assertEquals(old, store.installedVersion(next))
            val update = launch(start = CoroutineStart.UNDISPATCHED) {
                lifecycle.activateInstallation(next) { fail("Update changed selection") }
            }
            // A second recording cannot lose its installation while the first one ends.
            val secondRecording = lifecycle.acquireSession(old)
            recording.close()
            yield()
            assertTrue(update.isActive)
            assertEquals(old, store.installedVersion(next))
            secondRecording.close()
            update.join()
            assertEquals("2", store.installedVersion(next)?.version)
        } finally { recording.close() }
    }

    @Test fun unrelatedVersionFailureCannotInvalidateWorkingOlderInstallation() {
        val old = fixture("1", "old")
        val next = fixture("2", "new").copy(supportedOlderVersions = listOf(old))
        val store = RecognitionModelStore(temporaryFolder.root)
        install(store, old, "old")
        store.invalidate(next)
        assertEquals(old, store.installedVersion(next))
    }

    @Test fun failedOlderRuntimeLoadInvalidatesTheAttemptedInstallation() = runBlocking {
        val old = fixture("1", "old")
        val next = fixture("2", "new").copy(supportedOlderVersions = listOf(old))
        val store = RecognitionModelStore(temporaryFolder.root)
        install(store, old, "old")
        val lifecycle = RecognitionModelLifecycle(store, listOf(next))
        val selection = RecognitionModelSelection("fixture")
        val revision = RecognitionModelLifecycle.invalidations.value
        assertTrue(runCatching {
            lifecycle.acquireRuntime(selection) { error("injected older runtime load failure") }
        }.isFailure)
        assertTrue(RecognitionModelLifecycle.invalidations.value > revision)
        val readiness = requireNotNull(lifecycle.readiness(selection))
        assertFalse(readiness.isReady)
        assertNull(readiness.optionalUpgrade)
        assertEquals(RecognitionModelRepairReason.INVALID_OR_INCOMPATIBLE, readiness.repairReason)
        assertEquals("old", File(store.modelDirectory(old), "model.bin").readText())
    }

    @Test fun canceledOrOomRuntimeLoadPreservesTheInstalledVersion() = runBlocking {
        val old = fixture("1", "old")
        val next = fixture("2", "new").copy(supportedOlderVersions = listOf(old))
        val store = RecognitionModelStore(temporaryFolder.root)
        install(store, old, "old")
        val lifecycle = RecognitionModelLifecycle(store, listOf(next))
        for (failure in listOf(kotlinx.coroutines.CancellationException("injected"), OutOfMemoryError("injected"))) {
            val revision = RecognitionModelLifecycle.invalidations.value
            val result = runCatching {
                lifecycle.acquireRuntime(RecognitionModelSelection("fixture")) { throw failure }
            }
            assertSame(failure, result.exceptionOrNull())
            assertEquals(old, store.installedVersion(next))
            assertEquals(revision, RecognitionModelLifecycle.invalidations.value)
        }
    }

    @Test fun backendAcquisitionUsesInstalledVersionAndNextLoadUsesActivatedPayload() = runBlocking {
        val old = fixture("1", "old")
        val next = fixture("2", "new").copy(supportedOlderVersions = listOf(old))
        val store = RecognitionModelStore(temporaryFolder.root)
        install(store, old, "old")
        stage(store, next, "new")
        val lifecycle = RecognitionModelLifecycle(store, listOf(next))
        val selection = RecognitionModelSelection("fixture")
        val versions = mutableListOf<String>()
        suspend fun load() = lifecycle.acquireRuntime(selection) { readiness ->
            val installed = requireNotNull(readiness?.installedModel)
            versions += installed.version
            FixtureBackend(File(store.modelDirectory(installed), "model.bin").readText())
        }
        val first = load()
        val update = launch(start = CoroutineStart.UNDISPATCHED) { lifecycle.activateInstallation(next) {} }
        assertTrue(update.isActive)
        assertEquals("old", first.transcribe(floatArrayOf()))
        assertTrue(runCatching { lifecycle.releaseRuntime(first) { error("close failed") } }.isFailure)
        yield()
        assertTrue(update.isActive)
        assertEquals(old, store.installedVersion(next))
        lifecycle.releaseRuntime(first) { first.close() }
        update.join()
        val second = load()
        assertEquals("new", second.transcribe(floatArrayOf()))
        assertEquals(listOf("1", "2"), versions)
        lifecycle.releaseRuntime(second) { second.close() }
    }

    @Test fun selectionDeleteAndFailedDeleteInvalidateObserversAcrossLifecycleInstances() = runBlocking {
        val model = fixture("1", "old")
        val store = RecognitionModelStore(temporaryFolder.root)
        install(store, model, "old")
        val writer = RecognitionModelLifecycle(store, listOf(model))
        val observer = RecognitionModelLifecycle(RecognitionModelStore(temporaryFolder.root), listOf(model))
        var revision = RecognitionModelLifecycle.invalidations.value
        writer.select(model) { assertEquals(RecognitionModelSelection("fixture"), it) }
        assertTrue(RecognitionModelLifecycle.invalidations.value > revision)
        revision = RecognitionModelLifecycle.invalidations.value
        assertTrue(runCatching { writer.delete(model, model.id) }.isFailure)
        assertTrue(RecognitionModelLifecycle.invalidations.value > revision)
        assertTrue(observer.readiness(RecognitionModelSelection("fixture"))!!.isReady)
        revision = RecognitionModelLifecycle.invalidations.value
        writer.delete(model, null)
        assertTrue(RecognitionModelLifecycle.invalidations.value > revision)
        assertFalse(observer.readiness(RecognitionModelSelection("fixture"))!!.isReady)
    }

    @Test fun loadFailurePublishesRepairOnlyAfterVersionBoundInvalidationSettles() {
        val model = fixture("1", "old")
        val store = RecognitionModelStore(temporaryFolder.root)
        install(store, model, "old")
        val lifecycle = RecognitionModelLifecycle(store, listOf(model))
        val revision = RecognitionModelLifecycle.invalidations.value
        lifecycle.invalidateInstallation(model)
        assertTrue(RecognitionModelLifecycle.invalidations.value > revision)
        val readiness = requireNotNull(lifecycle.readiness(RecognitionModelSelection("fixture")))
        assertFalse(readiness.isReady)
        assertEquals(RecognitionModelRepairReason.INVALID_OR_INCOMPATIBLE, readiness.repairReason)
        assertEquals("old", File(store.modelDirectory(model), "model.bin").readText())
    }

    private class FixtureBackend(private val payload: String) : SpeechBackend {
        override suspend fun load(context: Context) = Unit
        override suspend fun transcribe(samples: FloatArray) = payload
        override suspend fun close() = Unit
    }
}
