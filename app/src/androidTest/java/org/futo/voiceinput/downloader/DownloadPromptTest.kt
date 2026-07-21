package org.futo.voiceinput.downloader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.futo.voiceinput.theme.UixThemeAuto
import org.junit.Rule
import org.junit.Test

class DownloadPromptTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun confirmationShowsSourceSizeNetworkAndStorageGuard() {
        compose.setContent {
            UixThemeAuto {
                DownloadPrompt(
                    models = emptyList(),
                    confirmation = DownloadConfirmation(
                        source = "Test Source",
                        transferBytes = 10_000_000,
                        requiredFreeSpaceBytes = 10_000_000,
                        availableBytes = 5_000_000,
                        cellular = true
                    )
                )
            }
        }

        compose.onNodeWithText("Source: Test Source").assertIsDisplayed()
        compose.onNodeWithText("Download size: 10.0 MB").assertIsDisplayed()
        compose.onNodeWithText("Current network: cellular data").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test
    fun progressShowsFileStatusAndFailure() {
        compose.setContent {
            UixThemeAuto {
                DownloadScreen(
                    models = listOf(
                        ModelInfo("finished.bin", "https://example.com/finished", size = 10, finished = true),
                        ModelInfo("failed.bin", "https://example.com/failed", size = 20, error = true)
                    )
                )
            }
        }

        compose.onNodeWithText("Downloaded 1 of 2 files").assertIsDisplayed()
        compose.onNodeWithText("finished.bin").assertIsDisplayed()
        compose.onNodeWithText("failed.bin").assertIsDisplayed()
        compose.onNodeWithText(
            "Download of one or more resources has failed. Please make sure you're connected to a network, the app has network permission, or try again later."
        ).assertIsDisplayed()
    }
}
