package org.futo.voiceinput.settings.pages

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.harper.HarperCleanupResult
import org.futo.voiceinput.harper.HarperTranscriptCleaner
import org.futo.voiceinput.settings.*
import org.futo.voiceinput.theme.ThemeOption
import org.futo.voiceinput.theme.UixThemeWrapper
import org.futo.voiceinput.theme.presets.ClassicMaterialLight
import org.futo.voiceinput.theme.presets.DevThemeYellow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class TranscriptCleanupUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var saved: Preferences

    @Before fun saveSettings() = runBlocking {
        saved = compose.activity.dataStore.data.first()
        compose.activity.dataStore.edit {
            it[SPEECH_BACKEND.key] = "orukeet"
            it[HARPER_ENABLED.key] = false
            it[HARPER_EXPLICIT_ENGLISH.key] = false
            it[S1_MINI_ENABLED.key] = false
            it[S1_MINI_STYLING.key] = S1MiniStyling.SemiFormal.id
        }
        Unit
    }

    @After fun restoreSettings() = runBlocking {
        compose.activity.dataStore.updateData { saved }
        Unit
    }

    private fun show(theme: ThemeOption = DevThemeYellow, width: Int = 360, fontScale: Float = 1f) {
        val colors = theme.obtainColors(compose.activity)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                UixThemeWrapper(colors) {
                    Surface(Modifier.width(width.dp).testTag("cleanup-preview"), color = colors.background) {
                        TranscriptCleanupScreen()
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun languageConfirmationIsContextualAndSavedAcrossModelChanges() {
        show()
        compose.onNodeWithText("Basic text cleanup").assertIsOff().performClick()
        awaitToggle("Basic text cleanup", true)
        compose.onNodeWithText("I dictate in English").performClick()
        awaitToggle("I dictate in English", true)
        runBlocking { assertEquals(true, compose.activity.getSetting(HARPER_EXPLICIT_ENGLISH)) }
        runBlocking { compose.activity.setSetting(SPEECH_BACKEND, "moonshine") }
        awaitText("Your selected model is set to English.")
        compose.onNodeWithText("I dictate in English").assertDoesNotExist()
        compose.onNodeWithText("Your selected model is set to English.", substring = true).assertIsDisplayed()
        runBlocking {
            compose.activity.setSetting(COHERE_LANGUAGE, "es")
            compose.activity.setSetting(SPEECH_BACKEND, "cohere")
        }
        awaitText("Your selected language is not English.")
        compose.onNodeWithText("Your selected language is not English.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("I dictate in English").assertDoesNotExist()
        runBlocking { compose.activity.setSetting(SPEECH_BACKEND, "orukeet") }
        awaitToggle("I dictate in English", true)
        compose.onNodeWithText("I dictate in English").assertIsOn()
    }

    @Test fun detailsAndRewritePreferencesAreDiscoverableWithoutDownloading() {
        show()
        compose.onNodeWithText("Keeps spelling", substring = true).assertDoesNotExist()
        compose.onNodeWithText("What changes?").performScrollTo().performClick()
        compose.onNodeWithText("Keeps spelling", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("What changes?").performScrollTo().performClick()
        compose.onNodeWithText("Rewrite preferences").performScrollTo().performClick()
        compose.onNodeWithText("Formal", substring = false).performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Formal", substring = false) and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        runBlocking {
            assertEquals("formal", compose.activity.getSetting(S1_MINI_STYLING))
            assertEquals(false, compose.activity.getSetting(S1_MINI_ENABLED))
        }
        compose.onNodeWithText("What can it rewrite?").performScrollTo().performClick()
        compose.onNodeWithText("English only. Assumes English", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun combinedOrderAppearsOnlyWhenBothOptionsAreEnabled() {
        runBlocking {
            compose.activity.setSetting(HARPER_ENABLED, true)
            compose.activity.setSetting(S1_MINI_ENABLED, true)
        }
        show()
        compose.onNodeWithText("For eligible English text", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("AI rewrite", substring = false).performScrollTo().performClick()
        awaitToggle("AI rewrite", false)
        compose.onNodeWithText("For eligible English text", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Basic text cleanup", substring = false).performScrollTo().assertIsOn()
    }

    @Test fun exampleShowsActualNativeCleanup() {
        val result = HarperTranscriptCleaner.clean("i  am here .", "", enabled = true, english = true)
        assertEquals(HarperCleanupResult.APPLIED, result.outcome)
        assertEquals("I am here.", result.text)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        File(compose.activity.filesDir, "cleanup-$name.png").outputStream().use {
            check(compose.onNodeWithTag("cleanup-preview").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun darkOverviewScreenshot() {
        show()
        compose.onNodeWithText("Basic text cleanup").assertIsDisplayed()
        capture("dark-basic")
        compose.onNodeWithText("Rewrite preferences").performScrollTo().assertIsDisplayed()
        capture("dark-rewrite")
    }

    @Test fun lightOverviewScreenshot() {
        show(theme = ClassicMaterialLight, width = 480)
        compose.onNodeWithText("Basic text cleanup").assertIsDisplayed()
        capture("light-overview")
    }

    @Test fun narrowLargeTextKeepsControlsAndDetailsAccessible() {
        show(width = 320, fontScale = 1.6f)
        compose.onNodeWithText("Basic text cleanup").assertIsDisplayed()
        capture("large-text-basic")
        compose.onNodeWithText("I dictate in English").performScrollTo().performClick()
        awaitToggle("I dictate in English", true)
        compose.onNodeWithText("AI rewrite", substring = false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rewrite preferences").performScrollTo().assertIsDisplayed()
        capture("large-text-rewrite")
        compose.onNodeWithText("Rewrite preferences").performClick()
        compose.onNodeWithText("Email", substring = false).performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Email", substring = false) and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitToggle(title: String, checked: Boolean) {
        val state = if (checked) ToggleableState.On else ToggleableState.Off
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(title) and SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, state))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun textAndDownloadActionMeetContrastInBothThemes() {
        for (theme in listOf(DevThemeYellow, ClassicMaterialLight)) {
            val colors = theme.obtainColors(compose.activity)
            val card = if (colors.surface == colors.background) lerp(colors.surface, colors.onSurface, 0.04f) else colors.surface
            val pairs = listOf(
                colors.onSurface to card,
                colors.onSurfaceVariant to card,
                colors.onSurfaceVariant to colors.background,
                colors.onPrimaryContainer to colors.primaryContainer
            )
            for ((foreground, background) in pairs) {
                val a = foreground.luminance()
                val b = background.luminance()
                check((maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f) >= 4.5f) {
                    "Insufficient cleanup text contrast in ${theme.key}"
                }
            }
        }
    }
}
