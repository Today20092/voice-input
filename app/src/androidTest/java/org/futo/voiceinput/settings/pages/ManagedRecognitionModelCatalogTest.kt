package org.futo.voiceinput.settings.pages

import android.content.ContextWrapper
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
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
import java.io.File

class ManagedRecognitionModelCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var saved: Preferences
    private lateinit var appContext: Context
    private lateinit var modelFiles: File
    private val launches = mutableListOf<Intent>()

    @Before
    fun saveSettings() = runBlocking {
        appContext = compose.activity.applicationContext
        modelFiles = File(appContext.cacheDir, "catalog-models-${System.nanoTime()}")
        check(modelFiles.mkdirs())
        saved = appContext.dataStore.data.first()
        appContext.dataStore.edit {
            it[SPEECH_BACKEND.key] = "orukeet"
            it[ENGLISH_MODEL_INDEX.key] = 0
            it[MULTILINGUAL_MODEL_INDEX.key] = 1
            it[ENABLE_MULTILINGUAL.key] = false
        }
        Unit
    }

    @After
    fun restoreSettings() = runBlocking {
        appContext.dataStore.updateData { saved }
        modelFiles.deleteRecursively()
        Unit
    }

    private fun showCatalog(fontScale: Float = 1f) {
        val context = object : ContextWrapper(compose.activity) {
            override fun startActivity(intent: Intent) { launches.add(intent) }
            override fun getFilesDir(): File = modelFiles
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)
            ) {
                UixThemeAuto { ScrollableList { ManagedRecognitionModelCatalog() } }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openDetails(title: String) {
        compose.onNode(hasText("Details") and hasAnyAncestor(hasText(title)))
            .performScrollTo().performClick()
    }

    private fun toggleFamily(family: String) {
        compose.onNode(hasText(family) and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
            .performScrollTo().performClick()
    }

    @Test
    fun asr4allFamilyShowsAllThreeChoicesWithoutChangingSelection() {
        showCatalog()
        toggleFamily("ASR4ALL")
        org.futo.voiceinput.asr4all.Asr4allModels.models.forEach {
            compose.onNodeWithText(it.displayName).assertExists()
        }
        assertEquals("orukeet", runBlocking { appContext.dataStore.data.first()[SPEECH_BACKEND.key] })
        openDetails("ASR4ALL Medium")
        compose.onNodeWithText("Shows English text as you speak. Adds punctuation, capital letters, and small corrections.")
            .assertIsDisplayed()
        File(appContext.filesDir, "asr4all-details.png").outputStream().use {
            compose.onNode(isRoot() and hasAnyDescendant(hasText("Shows English text as you speak. Adds punctuation, capital letters, and small corrections.")))
                .captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun selectedFamilyStartsOpenAndCanCollapseWithoutChangingSelection() {
        showCatalog()
        compose.onNodeWithText("Selected: Orukeet").assertIsDisplayed()
        compose.onNodeWithText("Model size:", substring = true).assertExists()
        compose.onNodeWithText("Low latency").assertDoesNotExist()
        compose.onNodeWithText("Moonshine Small").assertDoesNotExist()
        toggleFamily("Orukeet")
        compose.onNodeWithText("Selected: Orukeet")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        compose.onNodeWithText("Model size:", substring = true).assertDoesNotExist()
        toggleFamily("Nemotron")
        toggleFamily("Moonshine")
        compose.onNodeWithText("Low latency").assertExists()
        compose.onNodeWithText("Moonshine Small").assertExists()
        assertEquals("orukeet", runBlocking { compose.activity.dataStore.data.first()[SPEECH_BACKEND.key] })
        assertEquals(emptyList<Intent>(), launches)
    }

    @Test
    fun savedVariantStartsOpenAndStaysIdentifiableWhenCollapsed() {
        runBlocking {
            compose.activity.dataStore.edit {
                it[SPEECH_BACKEND.key] = "moonshine"
                it[MOONSHINE_MODEL_VARIANT.key] = "medium"
            }
        }
        showCatalog()
        compose.onNodeWithText("Moonshine Medium").assertExists()
        compose.onNodeWithText("Selected: Moonshine Medium").assertIsDisplayed()
        compose.onNodeWithText("Low latency").assertDoesNotExist()
        toggleFamily("Moonshine")
        compose.onNodeWithText("Moonshine Medium").assertDoesNotExist()
        compose.onNodeWithText("Selected: Moonshine Medium").assertIsDisplayed()
        assertEquals("medium", runBlocking {
            compose.activity.dataStore.data.first()[MOONSHINE_MODEL_VARIANT.key]
        })
    }

    @Test
    fun downloadRemainsAvailableAfterOpeningAFamily() {
        showCatalog()
        toggleFamily("Moonshine")
        compose.onNodeWithText("Moonshine Small").performScrollTo().performClick()
        assertEquals(1, launches.size)
        assertEquals("orukeet", runBlocking { compose.activity.dataStore.data.first()[SPEECH_BACKEND.key] })
    }

    @Test
    fun reduxShowsFinalOnlyBehaviorAndAttribution() {
        showCatalog()
        toggleFamily("Parakeet")
        compose.onNode(hasText("Parakeet Redux") and
            hasText("25 European languages", substring = true) and
            hasText("213.3 MB", substring = true))
            .performScrollTo().assertIsDisplayed()
        openDetails("Parakeet Redux")
        compose.onNodeWithText("CC BY 4.0", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        assertEquals(emptyList<Intent>(), launches)
    }

    @Test
    fun nemotronProfilesKeepTechnicalInformationBehindDetails() {
        showCatalog()
        toggleFamily("Nemotron")
        compose.onAllNodesWithText("Nemotron").assertCountEquals(1)
        listOf("Low latency" to "80 ms", "Balanced" to "160 ms", "Accuracy" to "560 ms")
            .forEach { (title, latency) ->
                compose.onNode(hasText(title) and hasText("English", substring = true))
                    .performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Source:", substring = true).assertDoesNotExist()
                openDetails(title)
                compose.onNodeWithText(latency, substring = true).assertIsDisplayed()
                compose.onNodeWithText("NVIDIA Open Model License")
                    .performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("2026-04-25").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Done").performClick()
                compose.onNodeWithText("Source:", substring = true).assertDoesNotExist()
            }
        assertEquals(emptyList<Intent>(), launches)
        assertEquals("orukeet", runBlocking { compose.activity.dataStore.data.first()[SPEECH_BACKEND.key] })
    }

    @Test
    fun parakeetUnifiedIsADistinctBufferedModel() {
        showCatalog()
        toggleFamily("Parakeet")
        compose.onNodeWithText("Parakeet TDT").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Parakeet Unified").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Parakeet Unified EN 0.6B").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Download: 663.0 MB", substring = true).assertIsDisplayed()
        openDetails("Parakeet Unified EN 0.6B")
        compose.onNodeWithText("Buffered live transcription").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
    }

    @Test
    fun nemotronMultilingualHasItsOwnCard() {
        showCatalog()
        toggleFamily("Nemotron")
        compose.onNodeWithText("Nemotron 3.5 Multilingual").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("28 languages and Auto-detect", substring = true).assertIsDisplayed()
        compose.onNodeWithText("OpenMDW 1.1", substring = true).assertDoesNotExist()
    }

    @Test
    fun whisperVariantsAppearAfterExpandingWithoutSelectingWhisper() {
        showCatalog()
        toggleFamily("Whisper (legacy)")
        listOf(ENGLISH_MODELS to "English", MULTILINGUAL_MODELS to "Multilingual").forEach { (models, language) ->
            models.forEach { model ->
                compose.onNode(hasText(model.name) and hasText(language, substring = true))
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
        toggleFamily("Cohere Transcribe")
        toggleFamily("Whisper (legacy)")
        listOf("Cohere Transcribe", "English-39 (default)").forEach { title ->
            openDetails(title)
            val details = compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("Source")))
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
