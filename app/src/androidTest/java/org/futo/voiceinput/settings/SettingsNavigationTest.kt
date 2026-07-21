package org.futo.voiceinput.settings

import androidx.activity.BackEventCompat
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.futo.voiceinput.theme.UixThemeAuto
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<SettingsActivity>()

    @Test
    fun sharedHostPreservesBackStackWhenPredictiveBackIsCancelledOrCommitted() {
        lateinit var navController: NavHostController
        compose.setContent {
            navController = rememberNavController()
            UixThemeAuto {
                SettingsMain(navController = navController)
            }
        }
        compose.waitUntil { navController.currentDestination?.route == "home" }

        compose.runOnUiThread { navController.navigate("models") }
        compose.waitUntil { navController.currentDestination?.route == "models" }

        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(0f))
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(0.5f))
            compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
        }
        compose.waitUntil { navController.currentDestination?.route == "models" }

        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(0f))
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(0.5f))
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitUntil { navController.currentDestination?.route == "home" }

        compose.runOnUiThread { navController.navigate("advanced") }
        compose.waitUntil { navController.currentDestination?.route == "advanced" }
        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(0f))
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(0.5f))
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitUntil { navController.currentDestination?.route == "home" }

        compose.runOnUiThread { navController.navigate("help") }
        compose.waitUntil { navController.currentDestination?.route == "help" }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil { navController.currentDestination?.route == "home" }

        compose.runOnUiThread { navController.navigate("advanced") }
        compose.waitUntil { navController.currentDestination?.route == "advanced" }
        compose.onNode(hasClickAction() and hasText("Advanced")).performClick()
        compose.waitUntil { navController.currentDestination?.route == "home" }
    }

    private fun backEvent(progress: Float) = BackEventCompat(
        touchX = 0f,
        touchY = 0f,
        progress = progress,
        swipeEdge = BackEventCompat.EDGE_LEFT
    )
}
