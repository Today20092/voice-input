package org.futo.voiceinput.settings

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.R
import org.futo.voiceinput.theme.UixThemeAuto
import org.junit.Rule
import org.junit.Test
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var saved: Preferences

    @Before
    fun saveSettings() = runBlocking {
        saved = compose.activity.dataStore.data.first()
        compose.activity.dataStore.edit {
            it[SPEECH_BACKEND.key] = "orukeet"
            it[IS_ALREADY_PAID.key] = false
            it[HAS_SEEN_PAID_NOTICE.key] = false
        }
        Unit
    }

    @After
    fun restoreSettings() = runBlocking {
        compose.activity.dataStore.updateData { saved }
        Unit
    }

    @Test
    fun selectedVariantsAppearOnHomeAndCleanupStaysOutsideModelOptions() {
        runBlocking {
            compose.activity.dataStore.edit {
                it[SPEECH_BACKEND.key] = "nemotron"
                it[NEMOTRON_PROFILE.key] = "balanced"
            }
        }
        lateinit var navController: NavHostController
        compose.setContent {
            navController = rememberNavController()
            UixThemeAuto { SettingsMain(navController = navController) }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Nemotron • Balanced", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Nemotron • Balanced", substring = true)
            .performScrollTo().assertIsDisplayed()

        runBlocking {
            compose.activity.dataStore.edit {
                it[SPEECH_BACKEND.key] = "whisper_ggml"
                it[ENGLISH_MODEL_INDEX.key] = 1
                it[MULTILINGUAL_MODEL_INDEX.key] = 1
                it[ENABLE_MULTILINGUAL.key] = true
            }
        }
        val whisperSummary = "Whisper • English-74 (slower, more accurate) + Multilingual-74 (default)"
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(whisperSummary, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(whisperSummary, substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.model)).performClick()
        compose.waitUntil(5_000) { navController.currentDestination?.route == SettingsDestination.Models.route }
        compose.onNodeWithText("Transcript Cleanup").assertDoesNotExist()
        compose.onNodeWithText("S1-mini", substring = true).assertDoesNotExist()
        compose.onNodeWithText("English-74 (slower, more accurate)").performScrollTo().assertIsDisplayed()
        compose.onNode(isSelected() and hasAnyAncestor(hasText("English-74 (slower, more accurate)")))
            .assertExists()
        compose.onNodeWithText("Multilingual-74 (default)").performScrollTo().assertIsDisplayed()
        compose.onNode(isSelected() and hasAnyAncestor(hasText("Multilingual-74 (default)")))
            .assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { navController.currentDestination?.route == SettingsDestination.Home.route }
        compose.onNodeWithText("Transcript Cleanup").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            navController.currentDestination?.route == SettingsDestination.TranscriptCleanup.route
        }
        compose.onNodeWithText("Transcript Cleanup").assertIsDisplayed()
    }

    @Test
    fun sharedHostPreservesBackStackWhenPredictiveBackIsCancelledOrCommitted() {
        lateinit var navController: NavHostController
        compose.setContent {
            navController = rememberNavController()
            UixThemeAuto {
                SettingsMain(navController = navController)
            }
        }
        compose.waitUntil(5_000) { navController.currentDestination?.route == "home" }

        compose.runOnUiThread { navController.navigate(SettingsDestination.Models.route) }
        compose.waitUntil(5_000) {
            navController.currentDestination?.route == SettingsDestination.Models.route
        }

        dispatchPredictiveBack(cancel = true)
        compose.waitUntil(5_000) {
            navController.currentDestination?.route == SettingsDestination.Models.route
        }

        dispatchPredictiveBack(cancel = false)
        compose.waitUntil(5_000) { navController.currentDestination?.route == "home" }

        compose.onNodeWithText("Transcript Cleanup").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            navController.currentDestination?.route == SettingsDestination.TranscriptCleanup.route
        }
        compose.onNodeWithText("Transcript Cleanup").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { navController.currentDestination?.route == "home" }

        compose.runOnUiThread { navController.navigate("advanced") }
        compose.waitUntil(5_000) { navController.currentDestination?.route == "advanced" }
        dispatchPredictiveBack(cancel = false)
        compose.waitUntil(5_000) { navController.currentDestination?.route == "home" }

        compose.runOnUiThread { navController.navigate("help") }
        compose.waitUntil(5_000) { navController.currentDestination?.route == "help" }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { navController.currentDestination?.route == "home" }

        compose.runOnUiThread { navController.navigate("advanced") }
        compose.waitUntil(5_000) { navController.currentDestination?.route == "advanced" }
        compose.onNode(
            hasClickAction() and hasText(compose.activity.getString(R.string.advanced_settings))
        ).performClick()
        compose.waitUntil(5_000) { navController.currentDestination?.route == "home" }
    }

    private fun dispatchPredictiveBack(cancel: Boolean) {
        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(0f))
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(0.5f))
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            if (cancel) {
                compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
            } else {
                compose.activity.onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    private fun backEvent(progress: Float) = BackEventCompat(
        touchX = 0f,
        touchY = 0f,
        progress = progress,
        swipeEdge = BackEventCompat.EDGE_LEFT
    )
}
