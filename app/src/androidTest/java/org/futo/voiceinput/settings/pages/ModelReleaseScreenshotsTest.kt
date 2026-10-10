package org.futo.voiceinput.settings.pages

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.settings.*
import org.futo.voiceinput.theme.UixThemeAuto
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Captures actual Compose pixels, excluding the phone's notification/navigation bars. */
class ModelReleaseScreenshotsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var saved: Preferences

    @Before
    fun showFinalModelsScreen() = runBlocking {
        saved = compose.activity.dataStore.data.first()
        compose.activity.dataStore.edit {
            it[SPEECH_BACKEND.key] = "orukeet"
            it[MODELS_MIGRATED.key] = true
            it[DISMISS_MIGRATION_TIP.key] = true
        }
        compose.setContent {
            UixThemeAuto {
                Surface(color = MaterialTheme.colorScheme.background) { ModelsScreen() }
            }
        }
        compose.waitForIdle()
    }

    @After
    fun restorePreferences() = runBlocking {
        compose.activity.dataStore.updateData { saved }
        Unit
    }

    private fun capture(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        val root = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
        File(compose.activity.filesDir, "release-$name.png").outputStream().use {
            check(root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test
    fun finalUiHasClearGroupsSizesAndSourceActions() {
        compose.onNodeWithText("Selected model").assertIsDisplayed()
        compose.onNodeWithText("Download Orukeet").assertIsDisplayed()
        compose.onNodeWithText("Selected: Orukeet").assertIsDisplayed()
        capture("models-overview")

        compose.onNodeWithText("Parakeet").performScrollTo().performClick()
        compose.onNode(hasText("Parakeet Redux") and hasText("Download:", substring = true))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Model size: 213.3 MB", substring = true).assertIsDisplayed()
        capture("models-final-only")

        compose.onNode(hasText("Details") and hasAnyAncestor(hasText("Parakeet Redux")))
            .performScrollTo().performClick()
        compose.onNodeWithText("Model info").assertIsDisplayed()
        compose.onNodeWithText("App model package").assertIsDisplayed()
        capture("model-details", dialog = true)
        compose.onNodeWithText("Done").performClick()
    }
}
