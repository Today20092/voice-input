package org.futo.voiceinput.downloader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.futo.voiceinput.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DownloadRetryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ordinaryFileFailureOffersRetry() {
        val model = ModelInfo("encoder.onnx", "https://example.com/model", size = 100, error = true)
        var retries = 0
        compose.setContent { DownloadScreen(listOf(model), onRetry = { retries++ }) }
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.download_retry)
        compose.onNodeWithText(label).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun fileRestartExplanationRemainsVisibleWhileDownloading() {
        val model = ModelInfo("encoder.onnx", "https://example.com/model", size = 100)
        model.restarted = true
        compose.setContent { ModelItem(model, showProgress = true) }
        val explanation = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.download_restarted)
        compose.onNodeWithText(explanation).assertIsDisplayed()
    }

    @Test fun receivedArchiveDoesNotShowFrozenCompletedBarsWhileVerifying() {
        val model = ModelInfo("orukeet.tar.bz2", "https://example.com/model", size = 100, progress = 1f)
        model.verifying = true
        compose.setContent { DownloadScreen(listOf(model)) }
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(2)
    }

    @Test fun unpackingHasItsOwnProgressAfterTheDownloadHasReachedOneHundredPercent() {
        val model = ModelInfo("orukeet.tar.bz2", "https://example.com/model", size = 100, progress = 1f)
        model.verifying = true
        model.installationProgress = 0.25f
        compose.setContent { DownloadScreen(listOf(model)) }
        compose.onNodeWithText("Download received. Installing model files: 25%.", substring = true).assertIsDisplayed()
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.25f, 0f..1f))).assertCountEquals(2)
    }
}
