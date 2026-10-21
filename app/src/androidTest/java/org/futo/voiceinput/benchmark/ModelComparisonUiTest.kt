package org.futo.voiceinput.benchmark

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.settings.THEME_KEY
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.settings.setSetting
import org.futo.voiceinput.theme.ThemeOption
import org.futo.voiceinput.theme.presets.ClassicMaterialLight
import org.futo.voiceinput.theme.presets.DevThemeYellow
import org.futo.voiceinput.theme.presets.VoiceInputTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ModelComparisonUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var layoutLabel: String? = null

    private fun withComparison(theme: ThemeOption, width: Int? = null, fontScale: Float = 1f,
        block: (ModelComparisonActivity, BenchmarkViewModel) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val previous = runBlocking { context.getSetting(THEME_KEY) }
        try {
            runBlocking { context.setSetting(THEME_KEY, theme.key) }
            ActivityScenario.launch<ModelComparisonActivity>(Intent(context, ModelComparisonActivity::class.java)).use { scenario ->
                lateinit var activity: ModelComparisonActivity
                lateinit var model: BenchmarkViewModel
                scenario.onActivity {
                    activity = it; model = ViewModelProvider(it)[BenchmarkViewModel::class.java]
                    if (width != null) {
                        layoutLabel = "${width}dp-${fontScale}x"
                        it.setContent {
                            val density = minOf(LocalDensity.current.density, it.resources.displayMetrics.widthPixels.toFloat() / width)
                            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                                Box(Modifier.width(width.dp).testTag("comparison-layout")) { it.ComparisonPage() }
                            }
                        }
                    }
                }
                compose.waitUntil(30000) { !model.busy }
                block(activity, model)
            }
        } finally { runBlocking { context.setSetting(THEME_KEY, previous) } }
    }

    private fun capture(activity: ModelComparisonActivity, name: String) {
        compose.waitForIdle()
        val config = activity.resources.configuration
        val label = layoutLabel ?: "${config.screenWidthDp}dp-${config.fontScale}"
        val root = if (layoutLabel == null) compose.onRoot() else compose.onNodeWithTag("comparison-layout")
        File(activity.filesDir, "comparison-$name-$label.png").outputStream().use {
            check(root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun assertSingleLineLabel(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("Button label must not split: $text", 1, layouts.single().lineCount)
    }

    @Test fun darkDraftGroupsActionsAndModels() = withComparison(DevThemeYellow) { activity, model ->
        compose.onNodeWithText("Model comparison").assertIsDisplayed()
        compose.onNodeWithText("Import audio").performScrollTo().assertIsEnabled()
        capture(activity, "dark-audio")
        compose.onNodeWithText("Compare selected models").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Recording tips and file limits").performScrollTo().performClick()
        compose.onNodeWithText("Up to 8 recordings", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Moonshine", substring = false).performScrollTo().performClick()
        val candidate = model.models.first { it.managed?.runtimeId == "moonshine" }
        compose.onNodeWithText(candidate.name).performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertTrue(candidate.id in model.selected) }
        capture(activity, "dark-models")
    }

    @Test fun lightResultsKeepTranscriptsAndExportsDiscoverable() = withComparison(ClassicMaterialLight) { activity, model ->
        val candidate = model.models.first()
        val clip = BenchmarkClip("preview", "original", "canonical", 16000, "hello world", "reference")
        val result = BenchmarkCaseResult(candidate.id, status = "completed", initializationNs = 1000000L,
            observations = mutableListOf(DecodeObservation(clip.id, "first", 0, 2000000L,
                "hello word", BenchmarkScoring.score(clip.reference, "hello word"))), identity = candidate.identity(activity))
        compose.runOnIdle {
            model.report = BenchmarkReport(status = "completed", clips = listOf(clip), selectedModelIds = listOf(candidate.id),
                installedBefore = emptyList(), cases = mutableListOf(result))
        }
        compose.onNodeWithText("Recognized: hello word").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("First transcription: 2.0 ms").performScrollTo().assertIsDisplayed()
        capture(activity, "light-results")
        compose.onNodeWithText("Every transcript and timing").performScrollTo().performClick()
        compose.onNodeWithText("Startup inclusive: 3.0 ms").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Export JSON").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Export Markdown").performScrollTo().assertIsDisplayed()
        capture(activity, "light-exports")
    }

    @Test fun errorsAndProgressStayNearTheHeader() = withComparison(DevThemeYellow) { activity, model ->
        compose.runOnIdle {
            model.report = BenchmarkReport(clips = emptyList(), selectedModelIds = emptyList(), installedBefore = emptyList())
            model.error = "Cannot import audio. Choose another recording."; model.busy = true; model.progress = "Comparing models"
        }
        compose.onNodeWithText("Cannot import audio. Choose another recording.").assertIsDisplayed()
        compose.onNodeWithText("Cancel comparison").performScrollTo().assertIsEnabled()
        capture(activity, "error-progress")
        compose.runOnIdle { model.busy = false }
    }

    @Test fun referenceEditingKeepsKeyboardFocusAndBackNavigation() = withComparison(DevThemeYellow) { activity, _ ->
        val passage = "Please compare this recording. ".repeat(30)
        compose.onNodeWithText("Passage").performScrollTo().performClick()
            .performTextReplacement(passage)
        compose.onNodeWithText("Passage").assertIsFocused()
        capture(activity, "editing")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Import audio").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Model comparison").performClick()
        compose.waitUntil(5000) { activity.isFinishing }
    }

    @Test fun recordingKeepsPassageInPlace() = checkRecordingPassagePosition()

    @Test fun recordingKeepsLargeTextPassageInPlace() = checkRecordingPassagePosition(320, 2f)

    private fun checkRecordingPassagePosition(width: Int? = null, fontScale: Float = 1f) =
        withComparison(DevThemeYellow, width, fontScale) { activity, model ->
            val passage = compose.onNodeWithText("Passage")
            passage.performScrollTo()
            val before = passage.fetchSemanticsNode().boundsInRoot
            compose.runOnIdle { model.busy = true }
            assertEquals(before, passage.fetchSemanticsNode().boundsInRoot)
            compose.runOnIdle {
                model.recording = true
                model.progress = "Recording. Stop after reading the passage. Limit: 180 seconds."
            }
            assertEquals(before, passage.fetchSemanticsNode().boundsInRoot)
            capture(activity, "recording-passage")
            compose.onNodeWithText("Stop recording").performScrollTo().assertIsEnabled().performClick()
            compose.runOnIdle { assertFalse(model.recording); model.busy = false }
        }

    @Test fun whistleSelectionRequiresAudioAndASupportedLanguage() = withComparison(DevThemeYellow) { activity, model ->
        compose.runOnIdle { model.selected = emptySet(); model.language = "fr" }
        compose.onNodeWithText("Whistle", substring = false).performScrollTo().performClick()
        compose.onNode(hasText("Whistle") and isToggleable()).performScrollTo().assertIsOff().performClick().assertIsOn()
        capture(activity, "whistle-selected")
        compose.onNodeWithText("Compare selected models").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { model.clips = listOf(BenchmarkClip("readiness", "original", "canonical", 16000, null, "reference")) }
        compose.onNodeWithText("Compare selected models").performScrollTo().assertIsEnabled()
        compose.runOnIdle { model.language = "ar" }
        compose.onNode(hasText("Whistle") and isToggleable()).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Compare selected models").performScrollTo().assertIsNotEnabled()
    }

    @Test fun comparisonTextMeetsContrastInBothThemes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (theme in listOf(DevThemeYellow, ClassicMaterialLight, VoiceInputTheme)) {
            val colors = theme.obtainColors(context)
            for ((foreground, background) in listOf(colors.onSurface to colors.surfaceContainer,
                colors.onSurfaceVariant to colors.surfaceContainer, colors.onSurfaceVariant to colors.background,
                colors.onSecondaryContainer to colors.secondaryContainer, colors.onErrorContainer to colors.errorContainer,
                colors.onSecondary to colors.secondary)) {
                val a = foreground.luminance(); val b = background.luminance()
                assertTrue("Insufficient comparison text contrast in ${theme.key}",
                    (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f) >= 4.5f)
            }
        }
    }

    @Test fun narrowLargeTextKeepsChoicesAndEditingAccessible() = withComparison(DevThemeYellow, 320, 2f) { activity, _ ->
        compose.onNodeWithText("Import audio").performScrollTo().assertIsEnabled()
        assertSingleLineLabel("Record")
        assertSingleLineLabel("Import audio")
        capture(activity, "narrow-audio")
        compose.onNodeWithText("Select all").performScrollTo()
        assertSingleLineLabel("Select all")
        assertSingleLineLabel("Installed only")
        compose.onNodeWithText("Whistle", substring = false).performScrollTo().performClick()
        compose.onNode(hasText("Whistle") and isToggleable()).performScrollTo().assertIsEnabled().performClick().assertIsOn()
        capture(activity, "narrow-models")
        compose.onNodeWithText("Compare selected models").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Passage").performScrollTo().performClick()
            .performTextReplacement("A longer passage for checking text editing. ".repeat(30))
        compose.onNodeWithText("Passage").assertIsFocused()
        capture(activity, "narrow-editing")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
    }

    @Test fun wideLightLayoutKeepsAudioAndModelChoicesReadable() = withComparison(ClassicMaterialLight, 640) { activity, _ ->
        compose.onNodeWithText("Import audio").assertIsDisplayed()
        capture(activity, "wide-audio")
        compose.onNodeWithText("Whistle", substring = false).performScrollTo().performClick()
        compose.onNode(hasText("Whistle") and isToggleable()).performScrollTo().assertIsEnabled()
        capture(activity, "wide-models")
    }
}
