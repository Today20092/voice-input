package org.futo.voiceinput.benchmark

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class LiveComparisonRotationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun activeReadingAndControlledEditorSurviveRecreation(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = BenchmarkModel.all().first { it.whisper?.ggml?.is_builtin_asset == true }
        val report = BenchmarkReport(clips = emptyList(), selectedModelIds = listOf(model.id), installedBefore = listOf(model.id))
        val store = BenchmarkStore(File(context.filesDir, "model-benchmarks"))
        store.save(report)
        try {
            val session = LiveBenchmark.arm(context, report, model, "ime", "before rotation")
            ActivityScenario.launch<LiveComparisonActivity>(
                Intent(context, LiveComparisonActivity::class.java).putExtra("runId", report.runId)
            ).use { scenario ->
                compose.onNodeWithText("Guided live readings").assertIsDisplayed()
                onView(withId(android.R.id.edit)).perform(replaceText("before rotation"))
                scenario.recreate()
                compose.onNodeWithText("Guided live readings").assertIsDisplayed()
                assertSame(session, LiveBenchmark.active)
                onView(withId(android.R.id.edit)).check(matches(withText("before rotation")))
                assumeTrue("Enable the test voice keyboard for the picker check",
                    context.getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.packageName == context.packageName })
                // Start with the keyboard hidden, which previously excluded auxiliary voice entries.
                scenario.onActivity { activity ->
                    val editor = activity.findViewById<android.widget.EditText>(android.R.id.edit)
                    activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(editor.windowToken, 0)
                }
                compose.onNodeWithText("Test keyboard").performScrollTo().performClick()
                compose.waitUntil(timeoutMillis = 5000) {
                    InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                        ?.findAccessibilityNodeInfosByText("Voice Input")?.isNotEmpty() == true
                }
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close()
            }
        } finally {
            LiveBenchmark.end()
            store.delete(report.runId)
        }
    }
}
