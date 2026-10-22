package org.futo.voiceinput.downloader

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.futo.voiceinput.parakeet.OrukeetModel
import org.futo.voiceinput.recognition.RecognitionModelStore
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class OrukeetInstallTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun publishedArchiveFinishesFreshInstallationWithVisibleProgress() {
        assumeTrue("Opt in with -e orukeetInstall true and seed the pinned archive",
            InstrumentationRegistry.getArguments().getString("orukeetInstall") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = OrukeetModel.recognitionModel
        val archive = requireNotNull(model.archive)
        val packed = File(context.filesDir, ".${archive.sha256}.archive.download")
        assertEquals(archive.sizeBytes, packed.length())
        val store = RecognitionModelStore(context.filesDir)
        assertFalse("Use a fresh test installation", store.modelDirectory(model).exists())
        try {
            ActivityScenario.launch<DownloadActivity>(context.recognitionModelDownloadIntent(model)
                .putExtra(EXTRA_PRESERVE_MODEL_SELECTION, true)).use { scenario ->
                compose.waitUntil(30000) { compose.onAllNodesWithText("Continue").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Continue").performClick()
                compose.waitUntil(180000) {
                    compose.onAllNodesWithText("Download received. Installing model files:", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                scenario.onActivity { assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0) }
                compose.waitUntil(600000) { store.isInstalled(model) }
                assertTrue("All installed files must match the pinned hashes", store.isInstalled(model, verifyHashes = true))
                assertFalse("Successful installation removes the saved archive", packed.exists())
            }
        } finally {
            store.modelDirectory(model).deleteRecursively()
            store.stagingDirectory(model).deleteRecursively()
            packed.delete()
        }
    }
}
