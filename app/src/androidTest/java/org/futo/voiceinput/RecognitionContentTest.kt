package org.futo.voiceinput

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class RecognitionContentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun compactImeHidesPreviewAndKeepsControlsReachableWithLargeText() {
        val text = "A long provisional transcript. ".repeat(100)
        val state = mutableStateOf(RecognitionUiState().start("Orukeet").recording().partial(text))
        var stopped = 0
        var canceled = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Box(Modifier.width(280.dp).height(360.dp).testTag("available-area")) {
                    RecognizerInputMethodWindow(switchBack = { canceled++ }, allowClick = state.value.isRecording,
                        onFinish = { stopped++ }) {
                        RecognitionContent(state.value, compact = true, onFinish = { stopped++ })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.cancel),
            useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(text, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Model: Orukeet", useUnmergedTree = true).assertIsDisplayed()
        val finish = compose.onNodeWithContentDescription(compose.activity.getString(R.string.finish_recording),
            useUnmergedTree = true)
        finish.assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, stopped); assertEquals(text, state.value.partialText) }
        val panel = compose.onNodeWithTag("ime-panel", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(panel.height < with(compose.density) { 240.dp.toPx() })
        assertEquals(with(compose.density) { 280.dp.toPx() }, panel.width, 1f)
        val finishBounds = compose.onNodeWithContentDescription(compose.activity.getString(R.string.finish_recording))
            .fetchSemanticsNode().boundsInRoot
        assertTrue(finishBounds.width >= with(compose.density) { 48.dp.toPx() })
        assertTrue(finishBounds.height >= with(compose.density) { 48.dp.toPx() })
        Log.i("Ticket27", "Compact 2x text panel: ${panel.width / compose.density.density} x ${panel.height / compose.density.density} dp")
        screenshot("ime-large-text")
        compose.runOnIdle { state.value = state.value.processing("Processing") }
        finish.assertDoesNotExist()
        compose.onNodeWithText("Processing", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(text, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.cancel),
            useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1, canceled); assertEquals(1, stopped) }
    }

    @Test fun waveformTextCaptionAndStatusCoexistAcrossUpdates() {
        val state = mutableStateOf(RecognitionUiState().start("Orukeet").recording())
        compose.setContent { MaterialTheme { RecognitionContent(state.value) } }
        compose.runOnIdle {
            state.value = state.value.partial("Still speaking")
                .waveform(listOf(-0.5f to 0.5f), MagnitudeState.TALKING)
                .status("Catching up", streaming = true)
        }
        compose.onNodeWithTag("recognition-waveform").assertIsDisplayed()
        compose.onNodeWithText("Still speaking").assertIsDisplayed()
        compose.onNodeWithText("Model: Orukeet").assertIsDisplayed()
        compose.onNodeWithText("Catching up").assertIsDisplayed()
        screenshot("catching-up")
        compose.onNodeWithText("Still speaking").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                compose.activity.getString(R.string.recognizer_provisional_transcript))
        )

        compose.runOnIdle { state.value = state.value.processing("Processing") }
        compose.onNodeWithText("Still speaking").assertIsDisplayed()
        compose.onNodeWithText("Processing").assertIsDisplayed()
        screenshot("processing")
        compose.onNodeWithTag("recognition-waveform").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.end() }
        compose.onNodeWithText("Still speaking").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.start("Moonshine").recording() }
        compose.onNodeWithText("Model: Moonshine").assertIsDisplayed()
        compose.onNodeWithText("Still speaking").assertDoesNotExist()
    }

    @Test fun compactWaveformRespondsToAudioAndReturnsToSilence() {
        val state = mutableStateOf(RecognitionUiState().start("Orukeet").recording())
        compose.setContent { MaterialTheme { RecognitionContent(state.value, compact = true) } }
        val waveform = compose.onNodeWithTag("recognition-waveform")
        val silence = waveform.captureToImage().asAndroidBitmap()
        compose.runOnIdle { state.value = state.value.waveform(List(200) { -0.15f to 0.2f }, MagnitudeState.TALKING) }
        val speech = waveform.captureToImage().asAndroidBitmap()
        assertTrue(!silence.sameAs(speech))
        compose.runOnIdle { state.value = state.value.waveform(List(200) { 0f to 0f }, MagnitudeState.NOT_TALKED_YET) }
        assertTrue(silence.sameAs(waveform.captureToImage().asAndroidBitmap()))
    }

    @Test fun compactImeUsesFullWidthWithoutGrowingWithLiveText() {
        val state = mutableStateOf(RecognitionUiState().start("Orukeet").recording()
            .waveform(List(200) { -0.1f to 0.15f }, MagnitudeState.TALKING))
        compose.setContent {
            Box(Modifier.fillMaxWidth().height(300.dp).testTag("available-area")) {
                RecognizerInputMethodWindow(switchBack = {}, allowClick = true) {
                    RecognitionContent(state.value, compact = true)
                }
            }
        }
        val panel = compose.onNodeWithTag("ime-panel", useUnmergedTree = true)
        val before = panel.fetchSemanticsNode().boundsInRoot
        val available = compose.onNodeWithTag("available-area").fetchSemanticsNode().boundsInRoot
        assertEquals(available.width, before.width, 1f)
        compose.runOnIdle { state.value = state.value.partial("Synthetic speech for layout testing. ".repeat(100)) }
        assertEquals(before.height, panel.fetchSemanticsNode().boundsInRoot.height, 1f)
        Log.i("Ticket27", "Compact normal panel: ${before.width / compose.density.density} x ${before.height / compose.density.density} dp")
        screenshot("ime-recording")
    }

    @Test fun compactImePromptsRemainActionable() {
        var action = 0
        val prompt = mutableStateOf(0)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Box(Modifier.width(280.dp).height(180.dp)) {
                    RecognizerInputMethodWindow(switchBack = {}) {
                        when (prompt.value) {
                            0 -> RecognizeMicError { action++ }
                            1 -> RecognizeModelDownloadRequired("Download a recognition model to continue") { action++ }
                            else -> RecognizeFailure("Recognition failed", { action++ })
                        }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.open_voice_input_settings))
            .performScrollTo().assertIsDisplayed().performClick()
        screenshot("ime-permission")
        compose.runOnIdle { prompt.value = 1 }
        compose.onNodeWithText(compose.activity.getString(R.string.download_model)).performScrollTo().assertIsDisplayed().performClick()
        screenshot("ime-download")
        compose.runOnIdle { prompt.value = 2 }
        compose.onNodeWithText(compose.activity.getString(R.string.model_options)).performScrollTo().assertIsDisplayed().performClick()
        screenshot("ime-error")
        compose.runOnIdle { assertEquals(3, action) }
    }

    @Test fun longTextScrollsAtLargeFontWithoutStoppingAndCancelStaysReachable() {
        val text = (1..100).joinToString(" ") { "word$it" }
        val state = mutableStateOf(RecognitionUiState().start("Orukeet").recording().partial(text))
        var stopped = 0
        var canceled = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Box(Modifier.width(280.dp).height(320.dp)) {
                    RecognizeWindow(forceNoUnpaidNotice = true, allowClick = state.value.isRecording,
                        onClose = { canceled++ }, onFinish = { stopped++ }) {
                        RecognitionContent(state.value)
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Cancel", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(text, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text, useUnmergedTree = true).performTouchInput { swipeUp() }
        screenshot("activity-large-text")
        compose.runOnIdle { assertEquals(0, stopped) }
        compose.onNodeWithContentDescription("Cancel", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1, canceled) }
        compose.onNodeWithTag("recognition-waveform", useUnmergedTree = true).performScrollTo()
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, stopped) }
        compose.runOnIdle { state.value = state.value.processing("Processing") }
        compose.onNodeWithText("Processing", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Processing", useUnmergedTree = true).performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, stopped) }
        compose.onNodeWithContentDescription("Cancel", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        val bitmap = (if (name.startsWith("ime-")) compose.onNodeWithTag("ime-panel", useUnmergedTree = true)
            else compose.onAllNodes(isRoot()).onLast()).captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir(null), "share07-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
