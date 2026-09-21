package org.futo.voiceinput.settings

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.InnerRecognize
import org.futo.voiceinput.MagnitudeState
import org.futo.voiceinput.R
import org.futo.voiceinput.settings.pages.AudioHistoryScreen
import org.futo.voiceinput.settings.pages.PersonalDictionaryEditor
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.theme.UixThemeWrapper
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class Beta18UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun iconlessRowAlignsAndWaveformFitsWithLargeText() {
        var dark by mutableStateOf(false)
        var large by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.5f else 1f)) {
                UixThemeWrapper(if (dark) darkColorScheme() else lightColorScheme()) {
                    Surface {
                        Column(Modifier.width(if (large) 320.dp else 400.dp)) {
                            Text("Recording", Modifier.padding(horizontal = 16.dp))
                            InnerRecognize(List(200) { index ->
                                val height = (index % 25) / 30f
                                -height to height
                            }, MagnitudeState.TALKING)
                            SettingToggleRaw("Back up recordings", true, {},
                                subtitle = "Save every recording on this device, including canceled attempts. Audio stays in private app storage.")
                            NavigationItem("Iconless navigation", NavigationItemStyle.Misc, {})
                        }
                    }
                }
            }
        }
        for (isDark in listOf(false, true)) for (isLarge in listOf(false, true)) {
            compose.runOnIdle { dark = isDark; large = isLarge }
            compose.onNodeWithText("Back up recordings").assertIsDisplayed()
            val heading = compose.onNodeWithText("Recording").fetchSemanticsNode().boundsInRoot
            val toggle = compose.onNodeWithText("Back up recordings", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val navigation = compose.onNodeWithText("Iconless navigation", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(heading.left, toggle.left, 1f)
            assertEquals(heading.left, navigation.left, 1f)
            screenshot("waveform-row-$isDark-$isLarge")
        }
    }

    @Test fun bulkPreviewCancelAndSavePreserveExistingEntries() {
        val context = compose.activity
        val original = runBlocking { context.getSetting(PERSONAL_DICTIONARY) }
        try {
            runBlocking { context.setSetting(PERSONAL_DICTIONARY, "existing") }
            compose.setContent { UixThemeWrapper(lightColorScheme()) { Surface { Column { PersonalDictionaryEditor(false) } } } }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("existing").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(context.getString(R.string.dictionary_add_multiple)).performClick()
            compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("inshallah => inshaAllah\nalhamdulillah")
            compose.onNodeWithText(context.getString(R.string.dictionary_preview)).performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText(context.getString(R.string.dictionary_add_entries)).fetchSemanticsNodes().isNotEmpty()
            }
            screenshot("dictionary-preview")
            compose.onNodeWithText(context.getString(android.R.string.cancel)).performClick()
            assertEquals("existing", runBlocking { context.getSetting(PERSONAL_DICTIONARY) })
            compose.onNodeWithText(context.getString(R.string.dictionary_add_multiple)).performClick()
            compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("inshallah => inshaAllah\nalhamdulillah")
            compose.onNodeWithText(context.getString(R.string.dictionary_preview)).performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText(context.getString(R.string.dictionary_add_entries)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(context.getString(R.string.dictionary_add_entries)).performClick()
            compose.waitUntil(5_000) {
                runBlocking { context.getSetting(PERSONAL_DICTIONARY) }.contains("alhamdulillah")
            }
            assertEquals("existing\ninshallah => inshaAllah\nalhamdulillah", runBlocking { context.getSetting(PERSONAL_DICTIONARY) })
        } finally {
            runBlocking { context.setSetting(PERSONAL_DICTIONARY, original) }
        }
    }

    @Test fun audioHistoryLayout() {
        compose.setContent { UixThemeWrapper(darkColorScheme()) { Surface { AudioHistoryScreen(rememberNavController()) } } }
        compose.onNodeWithText(compose.activity.getString(R.string.audio_history_save)).assertIsDisplayed()
        screenshot("audio-history")
    }

    private fun screenshot(name: String) {
        compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap().let { bitmap ->
            File(compose.activity.getExternalFilesDir(null), "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
