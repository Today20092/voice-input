package org.futo.voiceinput.settings

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.R
import org.futo.voiceinput.history.audioHistory
import org.futo.voiceinput.settings.pages.AudioHistoryScreen
import org.futo.voiceinput.theme.UixThemeWrapper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class AudioHistoryUiTest(private val dark: Boolean, private val large: Boolean) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val ids = mutableListOf<String>()

    @After fun removeFixtures() {
        ids.forEach { compose.activity.audioHistory().delete(it) }
    }

    private fun show(text: String?) {
        val store = compose.activity.audioHistory()
        store.begin().use { capture ->
            ids.add(capture.id)
            capture.append(ShortArray(16_000), 16_000)
            if (text != null) store.saveTranscript(capture.id, text)
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.5f else 1f)) {
                UixThemeWrapper(if (dark) darkColorScheme() else lightColorScheme()) {
                    Surface(Modifier.width(if (large) 320.dp else 400.dp)) {
                        AudioHistoryScreen(rememberNavController())
                    }
                }
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(label(R.string.audio_history_clear)).fetchSemanticsNodes().isNotEmpty()
        }
        scrollTo(R.string.audio_history_retranscribe).assertIsEnabled()
    }

    private fun label(id: Int) = compose.activity.getString(id)

    private fun scrollTo(id: Int): SemanticsNodeInteraction {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label(id)))
        return compose.onNodeWithText(label(id)).assertIsDisplayed()
    }

    @Test fun shortTranscriptHasDirectActionsWithoutExpansion() {
        show("Please call me tomorrow.")
        compose.onNodeWithText(label(R.string.audio_history_open)).assertDoesNotExist()
        scrollTo(R.string.audio_history_copy).assertIsEnabled().performClick()
        assertClipboard("Please call me tomorrow.")
        scrollTo(R.string.audio_history_delete).performClick()
        compose.onNodeWithText(label(R.string.audio_history_delete_question)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(android.R.string.cancel)).performClick()
        assertEquals(1, compose.activity.audioHistory().entries().count { it.id in ids })
        screenshot("short")
    }

    @Test fun longTranscriptCopiesFullTextAndExpandsWithoutChangingActions() {
        val text = "Please keep this entire sentence in the saved recording. ".repeat(8) + "Final words."
        show(text)
        scrollTo(R.string.audio_history_copy).performClick()
        assertClipboard(text)
        scrollTo(R.string.audio_history_open).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        screenshot("expanded")
        scrollTo(R.string.audio_history_hide).performClick()
        scrollTo(R.string.audio_history_open).assertIsEnabled()
        scrollTo(R.string.audio_history_delete).assertIsEnabled()
        screenshot("collapsed")
    }

    @Test fun layoutOverflowOffersExpansionEvenBelowPreviewCharacterLimit() {
        show("First line.\nSecond line.\nThird line.\nFourth line.")
        scrollTo(R.string.audio_history_open).performClick()
        scrollTo(R.string.audio_history_hide).assertIsEnabled()
    }

    @Test fun missingTranscriptStillOffersRecoveryAndDelete() {
        show(null)
        scrollTo(R.string.audio_history_no_text)
        compose.onNodeWithText(label(R.string.audio_history_open)).assertDoesNotExist()
        scrollTo(R.string.audio_history_copy).assertIsNotEnabled()
        scrollTo(R.string.audio_history_delete).assertIsEnabled()
        screenshot("no-transcript")
    }

    private fun assertClipboard(expected: String) {
        val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        compose.waitUntil(5_000) { clipboard.primaryClip?.getItemAt(0)?.text?.toString() == expected }
    }

    private fun screenshot(state: String) {
        compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap().let { bitmap ->
            File(compose.activity.getExternalFilesDir(null), "history-$state-$dark-$large.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "dark={0},large={1}")
        fun configurations() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
}
