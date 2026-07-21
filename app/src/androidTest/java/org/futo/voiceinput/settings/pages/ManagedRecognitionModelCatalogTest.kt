package org.futo.voiceinput.settings.pages

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.futo.voiceinput.theme.UixThemeAuto
import org.junit.Rule
import org.junit.Test

class ManagedRecognitionModelCatalogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun nemotronProfilesAreGroupedUnderOneCard() {
        compose.setContent {
            UixThemeAuto { ManagedRecognitionModelCatalog() }
        }

        compose.onAllNodesWithText("Nemotron").assertCountEquals(1)
        compose.onNodeWithText("Low latency").assertIsDisplayed()
        compose.onNodeWithText("Balanced").assertIsDisplayed()
        compose.onNodeWithText("Accuracy").assertIsDisplayed()
        compose.onNodeWithText("80 ms", substring = true).assertIsDisplayed()
        compose.onNodeWithText("160 ms", substring = true).assertIsDisplayed()
        compose.onNodeWithText("560 ms", substring = true).assertIsDisplayed()
    }

    @Test
    fun parakeetUnifiedIsADistinctBufferedModel() {
        compose.setContent {
            UixThemeAuto { ManagedRecognitionModelCatalog() }
        }

        compose.onNodeWithText("Parakeet TDT").assertIsDisplayed()
        compose.onNodeWithText("Parakeet Unified").assertIsDisplayed()
        compose.onNodeWithText("Parakeet Unified EN 0.6B").assertIsDisplayed()
        compose.onNodeWithText("560 ms buffered live English", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Nemotron is preferred", substring = true).assertIsDisplayed()
    }
}
