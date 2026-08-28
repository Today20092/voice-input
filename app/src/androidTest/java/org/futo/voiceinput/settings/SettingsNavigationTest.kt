package org.futo.voiceinput.settings

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.futo.voiceinput.R
import org.futo.voiceinput.theme.UixThemeAuto
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

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

        compose.onNodeWithText("Transcript Cleanup").performClick()
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
