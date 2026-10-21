package org.futo.voiceinput.benchmark

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.launch
import org.futo.voiceinput.phonon.PhononBackend
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

class ModelComparisonFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun everyCatalogEntryHasABenchmarkBackendIncludingPhonon() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val models = BenchmarkModel.all()
        models.forEach { assertNotNull("Missing backend for ${it.id}", it.backend(context, "en")) }
        assertTrue(models.first { it.managed?.runtimeId == "phonon" }.backend(context) is PhononBackend)
        val whistle = models.single { it.id == "whistle" }
        assertTrue(whistle.backend(context) is org.futo.voiceinput.whistle.WhistleBackend)
        val identity = whistle.identity(context, "fr")
        assertEquals("auto (recording language fr)", identity["language"])
        assertTrue(identity.getValue("timingNotice").contains("worker startup"))
        assertTrue(identity.getValue("memoryNotice").contains("excludes the Whistle worker"))
    }

    @Test fun draftRemovalAndDownloadHandoffSurviveRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File.createTempFile("comparison-flow", ".wav", context.cacheDir)
        val bytes = ByteBuffer.allocate(44 + 3200).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + 3200).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(3200)
        source.writeBytes(bytes.array())
        var model: BenchmarkViewModel? = null
        try {
            ActivityScenario.launch<ModelComparisonActivity>(Intent(context, ModelComparisonActivity::class.java)).use { scenario ->
                scenario.onActivity { model = ViewModelProvider(it)[BenchmarkViewModel::class.java] }
                val state = requireNotNull(model)
                compose.waitUntil(10000) { !state.busy }
                repeat(2) {
                    scenario.onActivity { state.import(Uri.fromFile(source), "actual spoken words") }
                    compose.waitUntil(10000) { !state.busy }
                    assertNull(state.error)
                }
                val retained = state.clips.last()
                val removed = state.clips.first()
                scenario.onActivity { state.removeClip(removed.id) }
                compose.waitUntil(10000) { !state.busy }
                assertEquals(listOf(retained), state.clips)
                assertFalse(state.benchmark.store.audioFile(state.draftId, removed.id).exists())
                scenario.onActivity { state.import(Uri.fromFile(source), "third recording") }
                compose.waitUntil(10000) { !state.busy }
                assertNull(state.error)
                assertEquals(2, state.clips.size)
                assertNotEquals(retained.id, state.clips.last().id)
                assertArrayEquals(FloatArray(1600), state.benchmark.store.readAudio(state.draftId, retained), 0f)

                val completed = AtomicReference<Boolean?>(null)
                scenario.onActivity {
                    state.viewModelScope.launch { completed.set(state.awaitDownload(state.models.first()) {}) }
                }
                scenario.recreate()
                scenario.onActivity {
                    val recreated = ViewModelProvider(it)[BenchmarkViewModel::class.java]
                    assertSame(state, recreated)
                    recreated.completeDownload(true)
                }
                compose.waitUntil(5000) { completed.get() != null }
                assertEquals(true, completed.get())
                assertEquals(2, state.clips.size)
                compose.onNodeWithText("Model comparison").assertIsDisplayed()
            }
        } finally {
            model?.let { it.benchmark.store.delete(it.draftId) }
            source.delete()
        }
    }

    @Test fun savedComparisonShowsEachModelsTranscriptAndTiming() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val models = BenchmarkModel.all().take(2)
        val clip = BenchmarkClip("test", "original", "canonical", 16000, "hello world", "reference")
        val report = BenchmarkReport(status = "completed", clips = listOf(clip),
            selectedModelIds = models.map { it.id }, installedBefore = emptyList())
        models.forEachIndexed { index, model ->
            val transcript = if (index == 0) "hello world" else "hello word"
            report.cases.add(BenchmarkCaseResult(model.id, status = "completed", initializationNs = 1000000L,
                observations = mutableListOf(DecodeObservation("test", "first", 0, 2000000L,
                    transcript, BenchmarkScoring.score(clip.reference, transcript)))))
        }
        val store = BenchmarkStore(File(context.filesDir, "model-benchmarks"))
        store.save(report)
        try {
            ActivityScenario.launch<ModelComparisonActivity>(Intent(context, ModelComparisonActivity::class.java)).use { scenario ->
                lateinit var model: BenchmarkViewModel
                scenario.onActivity { model = ViewModelProvider(it)[BenchmarkViewModel::class.java] }
                compose.waitUntil(10000) { !model.busy }
                scenario.onActivity { model.show(report) }
                compose.onNodeWithText("Recognized: hello world").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Recognized: hello word").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Export JSON").performScrollTo().assertIsDisplayed()
                assertTrue(store.markdown(store.load(report.runId)).contains("hello word"))
            }
        } finally { store.delete(report.runId) }
    }

    @Test fun completedRecordingsCanBeReusedWithoutChangingPriorResults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = BenchmarkStore(File(context.filesDir, "model-benchmarks"))
        val source = BenchmarkReport(status = "completed", clips = emptyList(), selectedModelIds = listOf("whistle"), installedBefore = emptyList())
        val original = File(store.directory(source.runId), "reuse.original").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val samples = floatArrayOf(-0.5f, 0f, 0.5f)
        val clip = BenchmarkClip("reuse", BenchmarkStore.hashFile(original), store.saveAudio(source.runId, "reuse", samples),
            samples.size, "Saved spoken words", "reference", language = "fr")
        val completed = source.copy(clips = listOf(clip), cases = mutableListOf(BenchmarkCaseResult("whistle", status = "completed")))
        store.save(completed)
        var newId: String? = null
        try {
            ActivityScenario.launch<ModelComparisonActivity>(Intent(context, ModelComparisonActivity::class.java)).use { scenario ->
                lateinit var model: BenchmarkViewModel
                scenario.onActivity { model = ViewModelProvider(it)[BenchmarkViewModel::class.java] }
                compose.waitUntil(10000) { !model.busy }
                scenario.onActivity { model.show(store.load(source.runId)) }
                val directoriesBefore = store.root.listFiles()!!.map { it.name }.toSet()
                original.writeBytes(byteArrayOf(9))
                scenario.onActivity { model.reuseRecordings() }
                compose.waitUntil(10000) { !model.busy }
                assertNotNull(model.error)
                assertEquals(source.runId, model.report!!.runId)
                assertEquals(directoriesBefore, store.root.listFiles()!!.map { it.name }.toSet())
                original.writeBytes(byteArrayOf(1, 2, 3))
                compose.onNodeWithText("Compare these recordings again").performScrollTo().performClick()
                compose.waitUntil(10000) { !model.busy }
                assertNull(model.error)
                assertNull(model.report)
                assertNotEquals(source.runId, model.draftId)
                newId = model.draftId
                assertEquals(listOf(clip), model.clips)
                assertEquals("fr", model.language)
                assertEquals(setOf("whistle"), model.selected)
                assertArrayEquals(samples, store.readAudio(model.draftId, clip), 0f)
                assertArrayEquals(original.readBytes(), File(store.directory(model.draftId), "reuse.original").readBytes())
                assertEquals("completed", store.load(source.runId).cases.single().status)
                assertEquals("prepared", store.load(model.draftId).status)
                // Each comparison owns its audio, so deleting old results cannot break a reused draft.
                store.delete(source.runId)
                assertArrayEquals(samples, store.readAudio(model.draftId, clip), 0f)
                scenario.recreate()
                assertEquals(listOf(clip), store.load(model.draftId).clips)
            }
        } finally {
            if (File(store.root, source.runId).exists()) store.delete(source.runId)
            newId?.let { store.delete(it) }
        }
    }
}
