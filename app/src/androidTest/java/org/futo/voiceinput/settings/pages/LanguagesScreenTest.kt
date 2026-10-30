package org.futo.voiceinput.settings.pages

import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.R
import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.futo.voiceinput.settings.*
import org.futo.voiceinput.theme.UixThemeAuto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LanguagesScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var saved: Preferences
    private lateinit var nav: NavHostController
    private val launches = mutableListOf<Intent>()

    @Before
    fun saveSettings() = runBlocking {
        saved = compose.activity.dataStore.data.first()
        compose.activity.dataStore.edit {
            it[SPEECH_BACKEND.key] = "orukeet"
            it[LANGUAGE_TOGGLES.key] = setOf("en", "fr")
            it[ENABLE_MULTILINGUAL.key] = true
            it[MULTILINGUAL_MODEL_INDEX.key] = 0
            it[MANUALLY_SELECT_LANGUAGE.key] = true
            it[USE_LANGUAGE_SPECIFIC_MODELS.key] = true
        }
        Unit
    }

    @After
    fun restoreSettings() = runBlocking {
        compose.activity.dataStore.updateData { saved }
        Unit
    }

    private fun showNavigation() {
        val context = object : ContextWrapper(compose.activity) {
            override fun startActivity(intent: Intent) { launches.add(intent) }
        }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                UixThemeAuto {
                    nav = rememberNavController()
                    NavHost(nav, startDestination = "home") {
                        composable("home") {
                            TextButton(onClick = { nav.navigate("languages") }) { Text("Open languages") }
                        }
                        composable("languages") { LanguagesScreen(navController = nav) }
                    }
                }
            }
        }
    }

    @Test
    fun everyManagedModelNavigatesWithoutWhisperControlsOrDownloadEffects() {
        showNavigation()
        RecognitionModelCatalog.models.forEach { model ->
            runBlocking {
                compose.activity.dataStore.edit {
                    it[SPEECH_BACKEND.key] = model.runtimeId
                    it[MOONSHINE_MODEL_VARIANT.key] = model.variantId ?: "small"
                    it[NEMOTRON_PROFILE.key] = model.variantId ?: "balanced"
                }
            }
            compose.onNodeWithText("Open languages").performClick()
            compose.onNodeWithText(recognitionLanguageGuidance(model)).assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.language_tip_1)).assertDoesNotExist()
            compose.onNodeWithText(compose.activity.getString(R.string.use_language_specific_models))
                .assertDoesNotExist()
            assertEquals(emptyList<Intent>(), launches)
            assertEquals(setOf("en", "fr"), read(LANGUAGE_TOGGLES))
            assertEquals(true, read(ENABLE_MULTILINGUAL))
            assertEquals(true, read(MANUALLY_SELECT_LANGUAGE))
            assertEquals(true, read(USE_LANGUAGE_SPECIFIC_MODELS))
            compose.runOnUiThread { nav.popBackStack() }
        }
    }

    private fun <T> read(setting: SettingsKey<T>): T =
        compose.activity.getSettingBlocking(setting.key, setting.default)

    @Test
    fun languageSelectorsUseRuntimeFallbacksAndSurviveVariantSwitches() {
        write(SPEECH_BACKEND, "nemotron")
        write(NEMOTRON_PROFILE, "multilingual")
        write(NEMOTRON_MULTILINGUAL_LANGUAGE, "removed-language")
        showNavigation()
        compose.onNodeWithText("Open languages").performClick()
        assertEnglishSelected()
        compose.onNodeWithText("Auto-detect").performScrollTo().performClick()
        compose.waitUntil { read(NEMOTRON_MULTILINGUAL_LANGUAGE) == "auto" }

        write(NEMOTRON_PROFILE, "accuracy")
        compose.onNodeWithText(recognitionLanguageGuidance(RecognitionModelCatalog.nemotronEnglishAccuracy))
            .assertIsDisplayed()
        compose.onNodeWithText("Auto-detect").assertDoesNotExist()
        write(NEMOTRON_PROFILE, "multilingual")
        compose.onAllNodes(isSelected() and hasAnyAncestor(hasText("Auto-detect")))
            .assertCountEquals(1)

        write(COHERE_LANGUAGE, "auto")
        write(SPEECH_BACKEND, "cohere")
        assertEnglishSelected()
        compose.onNodeWithText("Auto-detect").assertDoesNotExist()
        compose.onNodeWithText("Arabic").performScrollTo().performClick()
        compose.waitUntil { read(COHERE_LANGUAGE) == "ar" }
        assertEquals("auto", read(NEMOTRON_MULTILINGUAL_LANGUAGE))
        assertEquals(setOf("en", "fr"), read(LANGUAGE_TOGGLES))
        assertEquals(emptyList<Intent>(), launches)
    }

    @Test
    fun whisperPreferencesSurviveNavigationAndOnlyWhisperStartsItsDownload() {
        write(SPEECH_BACKEND, "whisper_ggml")
        showNavigation()
        compose.onNodeWithText("Open languages").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.use_language_specific_models))
            .assertIsDisplayed()
        compose.waitUntil { launches.isNotEmpty() }
        compose.runOnUiThread { nav.popBackStack() }
        launches.clear()
        write(SPEECH_BACKEND, "moonshine")
        write(MOONSHINE_MODEL_VARIANT, "medium")
        compose.onNodeWithText("Open languages").performClick()
        compose.onNodeWithText("Moonshine Medium").assertIsDisplayed()
        assertEquals(emptyList<Intent>(), launches)
        compose.runOnUiThread { nav.popBackStack() }
        write(SPEECH_BACKEND, "whisper_ggml")
        compose.onNodeWithText("Open languages").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.use_language_specific_models))
            .assertIsDisplayed()
        assertEquals(setOf("en", "fr"), read(LANGUAGE_TOGGLES))
        assertTrue(read(ENABLE_MULTILINGUAL))
        assertTrue(read(MANUALLY_SELECT_LANGUAGE))
        assertTrue(read(USE_LANGUAGE_SPECIFIC_MODELS))
    }

    @Test
    fun staleRuntimeAndVariantSelectionsUseTheirRuntimeDefaultsWithoutWhisperEffects() {
        write(SPEECH_BACKEND, "removed-runtime")
        showNavigation()
        compose.onNodeWithText("Open languages").performClick()
        compose.onNodeWithText("Orukeet").assertIsDisplayed()
        write(NEMOTRON_PROFILE, "removed-variant")
        write(SPEECH_BACKEND, "nemotron")
        compose.onNodeWithText("Balanced").assertIsDisplayed()
        compose.onNodeWithText("Auto-detect").assertDoesNotExist()
        assertEquals(emptyList<Intent>(), launches)
    }

    private fun assertEnglishSelected() {
        compose.onAllNodes(isSelected() and hasAnyAncestor(hasText("English")))
            .assertCountEquals(1)
    }

    @Test
    fun staleWhisperLanguagesFallBackToEnglishWithoutDownloadingAMultilingualModel() {
        write(SPEECH_BACKEND, "whisper_ggml")
        write(LANGUAGE_TOGGLES, setOf("removed-language"))
        write(MULTILINGUAL_MODEL_INDEX, Int.MAX_VALUE)
        showNavigation()
        compose.onNodeWithText("Open languages").performClick()
        compose.waitUntil { read(LANGUAGE_TOGGLES) == setOf("en") && !read(ENABLE_MULTILINGUAL) }
        compose.onNodeWithText(compose.activity.getString(R.string.language_tip_1)).assertIsDisplayed()
        assertEquals(emptyList<Intent>(), launches)
    }

    private fun <T> write(setting: SettingsKey<T>, value: T) =
        compose.activity.setSettingBlocking(setting.key, value)
}
