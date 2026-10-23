package org.futo.voiceinput.settings.pages

import android.content.ContextWrapper
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.s1.S1MiniDiagnostics
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.text.DateFormat
import java.util.Date

class TranscriptCaptureUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var directory: File
    private lateinit var context: ContextWrapper

    @Before fun prepareStorage() {
        directory = File(compose.activity.cacheDir, "capture-ui-${System.nanoTime()}").apply { mkdirs() }
        context = object : ContextWrapper(compose.activity) {
            override fun getCacheDir(): File = File(directory, "cache")
            override fun getFilesDir(): File {
                check(Looper.myLooper() != Looper.getMainLooper()) { "Capture disk access on main thread" }
                return directory
            }
        }
    }

    @After fun removeStorage() {
        directory.deleteRecursively()
    }

    private fun show() {
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                MaterialTheme { DiagnosticsScreen() }
            }
        }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun storageErrorIsVisibleAndRetryLoadsAfterRepair() {
        val blocker = File(directory, "s1-transcript-captures").apply { writeText("blocked") }
        show()
        awaitText("Unable to load captured transcripts.")
        compose.onNodeWithText("0 captured run(s)").assertDoesNotExist()
        check(blocker.delete())
        compose.onNodeWithText("Retry").performScrollTo().performClick()
        awaitText("0 captured run(s)")
        compose.onNodeWithText("Unable to load captured transcripts.").assertDoesNotExist()
    }

    @Test fun resumeViewingDeletingAndClearingUseBackgroundStorage() {
        val now = System.currentTimeMillis()
        runBlocking {
            S1MiniDiagnostics.recordTranscript(context, "first", "raw", "clean", "final", null, now)
        }
        show()
        awaitText("1 captured run(s)")
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        runBlocking {
            S1MiniDiagnostics.recordTranscript(context, "second", "raw", "clean", "final", null, now - 60_000L)
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        awaitText("2 captured run(s)")
        compose.onNodeWithText(DateFormat.getDateTimeInstance().format(Date(now)))
            .performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()
        awaitText("1 captured run(s)")
        compose.onNodeWithText("Clear captured transcripts").performScrollTo().performClick()
        awaitText("0 captured run(s)")
    }
}
