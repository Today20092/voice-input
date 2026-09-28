package org.futo.voiceinput

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
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

    @Test fun imeReservesItsExistingBottomPaddingWithLongText() {
        val text = "A long provisional transcript. ".repeat(100)
        val state = RecognitionUiState().start("Orukeet").recording().partial(text)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Box(Modifier.width(600.dp).height(360.dp).testTag("available-area")) {
                    RecognizerInputMethodWindow(switchBack = {}, allowClick = true) {
                        RecognitionContent(state)
                    }
                }
            }
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.cancel),
            useUnmergedTree = true).assertIsDisplayed()
        val transcript = compose.onNodeWithText(text, useUnmergedTree = true)
        transcript.performScrollTo().assertIsDisplayed()
        val available = compose.onNodeWithTag("available-area").fetchSemanticsNode().boundsInRoot
        val bottom = transcript.fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(bottom <= available.bottom - with(compose.density) { 64.dp.toPx() })
        screenshot("ime-large-text")
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
        val bitmap = compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir(null), "share07-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
