package org.futo.voiceinput.settings.pages

import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.ENGLISH_MODELS
import org.futo.voiceinput.MULTILINGUAL_MODELS
import org.futo.voiceinput.settings.*
import org.futo.voiceinput.theme.UixThemeAuto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ManagedRecognitionModelCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var saved: Preferences
    private val launches = mutableListOf<Intent>()

    @Before
    fun saveSettings() = runBlocking {
        saved = compose.activity.dataStore.data.first()
        compose.activity.dataStore.edit {
            it[SPEECH_BACKEND.key] = "orukeet"
            it[ENGLISH_MODEL_INDEX.key] = 0
            it[MULTILINGUAL_MODEL_INDEX.key] = 1
            it[ENABLE_MULTILINGUAL.key] = false
        }
        Unit
    }

    @After
    fun restoreSettings() = runBlocking {
        compose.activity.dataStore.updateData { saved }
        Unit
    }

    private fun showCatalog(fontScale: Float = 1f) {
        val context = object : ContextWrapper(compose.activity) {
            override fun startActivity(intent: Intent) { launches.add(intent) }
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)
            ) {
                UixThemeAuto { ScrollableList { ManagedRecognitionModelCatalog() } }
            }
        }
    }

    private fun openDetails(title: String) {
        compose.onNode(hasText("Details") and hasAnyAncestor(hasText(title)))
            .performScrollTo().performClick()
    }

    @Test
    fun reduxShowsFinalOnlyBehaviorAndAttribution() {
        showCatalog()
        compose.onNode(hasText("Parakeet Redux (Beta)") and
            hasText("Final-only transcription", substring = true) and
            hasText("213.3 MB", substring = true))
            .performScrollTo().assertIsDisplayed()
        openDetails("Parakeet Redux (Beta)")
        compose.onNodeWithText("CC BY 4.0", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        assertEquals(emptyList<Intent>(), launches)
    }

    @Test
    fun nemotronProfilesKeepTechnicalInformationBehindDetails() {
        showCatalog()
        compose.onAllNodesWithText("Nemotron").assertCountEquals(1)
        listOf("Low latency" to "80 ms", "Balanced" to "160 ms", "Accuracy" to "560 ms")
            .forEach { (title, latency) ->
                compose.onNode(hasText(title) and hasText("Live transcription • English", substring = true))
                    .performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Source:", substring = true).assertDoesNotExist()
                openDetails(title)
                compose.onNodeWithText(latency, substring = true).assertIsDisplayed()
                compose.onNodeWithText("License/attribution: NVIDIA Open Model License", substring = true)
                    .assertIsDisplayed()
                compose.onNodeWithText("Version: 2026-04-25", substring = true).assertIsDisplayed()
                compose.onNodeWithText("Done").performClick()
                compose.onNodeWithText("Source:", substring = true).assertDoesNotExist()
            }
        assertEquals(emptyList<Intent>(), launches)
        assertEquals("orukeet", runBlocking { compose.activity.dataStore.data.first()[SPEECH_BACKEND.key] })
    }

    @Test
    fun parakeetUnifiedIsADistinctBufferedModel() {
        showCatalog()
        compose.onNodeWithText("Parakeet TDT").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Parakeet Unified").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Parakeet Unified EN 0.6B").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Buffered live transcription • English", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Download 663.0 MB • Installed 663.0 MB", substring = true).assertIsDisplayed()
    }

    @Test
    fun nemotronMultilingualHasItsOwnCard() {
        showCatalog()
        compose.onNodeWithText("Nemotron 3.5 Multilingual").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Live transcription • 28 languages and Auto-detect", substring = true).assertIsDisplayed()
        compose.onNodeWithText("OpenMDW 1.1", substring = true).assertDoesNotExist()
    }

    @Test
    fun whisperVariantsAppearDirectlyWithoutSelectingWhisper() {
        showCatalog()
        compose.onNodeWithText("Whisper (legacy)").performScrollTo().assertIsDisplayed()
        listOf(ENGLISH_MODELS to "English", MULTILINGUAL_MODELS to "Multilingual").forEach { (models, language) ->
            models.forEach { model ->
                compose.onNode(hasText(model.name) and hasText("Final-only transcription • $language", substring = true))
                    .performScrollTo().assertIsDisplayed()
            }
        }
        compose.onNodeWithText("Transcript Cleanup").assertDoesNotExist()
        compose.onNodeWithText("S1-mini", substring = true).assertDoesNotExist()
        assertEquals(emptyList<Intent>(), launches)
    }

    @Test
    fun managedAndWhisperDetailsCanScrollAtLargeFontSizes() {
        showCatalog(fontScale = 2f)
        listOf("Cohere Transcribe (Beta)", "English-39 (default)").forEach { title ->
            openDetails(title)
            val details = compose.onNode(hasText("Source:", substring = true) and hasScrollAction())
            val range = details.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            details.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
            compose.waitForIdle()
            assertEquals(range.maxValue(), range.value(), 0.5f)
            compose.onNodeWithText("Done").assertIsDisplayed().performClick()
            compose.onNodeWithText("Source:", substring = true).assertDoesNotExist()
        }
        assertEquals(emptyList<Intent>(), launches)
    }
}
