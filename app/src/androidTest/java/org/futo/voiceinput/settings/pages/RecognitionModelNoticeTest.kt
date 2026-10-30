package org.futo.voiceinput.settings.pages

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.futo.voiceinput.recognition.*
import org.futo.voiceinput.backend.SpeechBackend
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import java.io.File
import java.security.MessageDigest
import org.futo.voiceinput.theme.UixThemeAuto
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecognitionModelNoticeTest {
    @get:Rule val compose = createComposeRule()
    private val old = RecognitionModelCatalog.moonshineSmall.copy(version = "fixture-old")
    private val next = old.copy(version = "fixture-new", supportedOlderVersions = listOf(old))

    @Test fun noticesFollowRepairUpgradeDismissalAndActivationWithoutAutomaticDownload() {
        val state = mutableStateOf(RecognitionModelReadiness(next, false,
            repairReason = RecognitionModelRepairReason.MISSING))
        val dismissed = mutableStateOf(false)
        val requested = mutableListOf<RecognitionModel>()
        compose.setContent {
            UixThemeAuto {
                RecognitionModelNotice(state.value, dismissed.value, { requested.add(it) }) { dismissed.value = true }
            }
        }
        compose.onNodeWithText("Download model").assertIsDisplayed()
        assertEquals(emptyList<RecognitionModel>(), requested)
        compose.onNodeWithText("Download model").performClick()
        assertEquals(listOf(next), requested)
        compose.runOnIdle { state.value = RecognitionModelReadiness(next, true, old, next) }
        compose.onNodeWithText("Update model").assertIsDisplayed()
        compose.onNodeWithText("Dismiss").performClick()
        compose.onNodeWithText("Update model").assertDoesNotExist()
        compose.runOnIdle {
            dismissed.value = false
            state.value = RecognitionModelReadiness(next, true, next)
        }
        compose.onNodeWithText("Update model").assertDoesNotExist()
        compose.onNodeWithText("Download model").assertDoesNotExist()
        compose.runOnIdle {
            state.value = RecognitionModelReadiness(next, false,
                repairReason = RecognitionModelRepairReason.INVALID_OR_INCOMPATIBLE)
        }
        compose.onNodeWithText("Repair model").assertIsDisplayed()
        assertEquals(listOf(next), requested)
    }

    @Test fun androidFixtureInterruptionReloadAndDeletionPublishRealReadinessTransitions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "ticket03-update-${System.nanoTime()}").apply { mkdirs() }
        fun fixture(version: String, content: String) = old.copy(
            id = "update-fixture", runtimeId = "fixture", variantId = null,
            directoryName = "fixture", displayName = "Update fixture", version = version,
            artifacts = listOf(RecognitionModelArtifact("model.bin", "https://example.com/$version/model.bin",
                content.toByteArray().size.toLong(), MessageDigest.getInstance("SHA-256")
                    .digest(content.toByteArray()).joinToString("") { "%02x".format(it) })))
        val first = fixture("1", "old")
        val second = fixture("2", "new").copy(supportedOlderVersions = listOf(first))
        val store = RecognitionModelStore(root)
        val lifecycle = RecognitionModelLifecycle(store, listOf(second))
        val selection = RecognitionModelSelection("fixture")
        try {
            File(store.modelDirectory(first).apply { mkdirs() }, "model.bin").writeText("old")
            org.junit.Assert.assertTrue(store.completeInstall(first))
            compose.setContent {
                val revision by RecognitionModelLifecycle.invalidations.collectAsState()
                val readiness = remember(revision) { lifecycle.readiness(selection) }
                UixThemeAuto { RecognitionModelNotice(readiness, false, { error("Unexpected download") }, {}) }
            }
            compose.onNodeWithText("Update model").assertIsDisplayed()
            runBlocking {
                File(store.stagingDirectory(second).apply { mkdirs() }, "model.bin").writeText("new")
                val recording = lifecycle.acquireSession(first)
                val interrupted = launch(start = CoroutineStart.UNDISPATCHED) {
                    lifecycle.activateInstallation(second) { error("Update changed selection") }
                }
                org.junit.Assert.assertTrue(interrupted.isActive)
                interrupted.cancel()
                interrupted.join()
                recording.close()
                assertEquals("1", RecognitionModelStore(root).installedVersion(second)?.version)
            }
            compose.onNodeWithText("Update model").assertIsDisplayed()
            runBlocking {
                lifecycle.activateInstallation(second) { error("Update changed selection") }
                val backend = lifecycle.acquireRuntime(selection) { readiness ->
                    assertEquals("2", readiness?.installedModel?.version)
                    PayloadBackend(File(store.modelDirectory(requireNotNull(readiness?.installedModel)), "model.bin").readText())
                }
                assertEquals("new", backend.transcribe(floatArrayOf()))
                lifecycle.releaseRuntime(backend) { backend.close() }
            }
            compose.onNodeWithText("Update model").assertDoesNotExist()
            compose.onNodeWithText("Download model").assertDoesNotExist()
            lifecycle.invalidateInstallation(second)
            compose.onNodeWithText("Repair model").assertIsDisplayed()
            runBlocking {
                File(store.stagingDirectory(second).apply { mkdirs() }, "model.bin").writeText("new")
                lifecycle.activateInstallation(second) {}
            }
            compose.onNodeWithText("Repair model").assertDoesNotExist()
            runBlocking { lifecycle.delete(second, null) }
            compose.onNodeWithText("Download model").assertIsDisplayed()
        } finally { root.deleteRecursively() }
    }

    private class PayloadBackend(private val payload: String) : SpeechBackend {
        override suspend fun load(context: Context) = Unit
        override suspend fun transcribe(samples: FloatArray) = payload
        override suspend fun close() = Unit
    }
}
