package org.futo.voiceinput.benchmark

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.ENGLISH_MODELS
import org.futo.voiceinput.MULTILINGUAL_MODELS
import org.futo.voiceinput.selectedWhisperModelsForCurrentSettings
import org.futo.voiceinput.settings.*
import org.junit.Assert.assertEquals
import org.junit.Test

class WhisperSelectionTest {
    @Test fun explicitMultilingualDailyChoiceAlsoAppliesToEnglishWithoutFallback() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val enabled = context.getSetting(ENABLE_MULTILINGUAL)
        val languageSpecific = context.getSetting(USE_LANGUAGE_SPECIFIC_MODELS)
        val languages = context.getSetting(LANGUAGE_TOGGLES)
        val englishIndex = context.getSetting(ENGLISH_MODEL_INDEX)
        val multilingualIndex = context.getSetting(MULTILINGUAL_MODEL_INDEX)
        try {
            context.setSetting(ENABLE_MULTILINGUAL, true)
            context.setSetting(LANGUAGE_TOGGLES, setOf("en"))
            context.setSetting(USE_LANGUAGE_SPECIFIC_MODELS, false)
            assertEquals(listOf(MULTILINGUAL_MODELS[multilingualIndex]), context.selectedWhisperModelsForCurrentSettings(null))
            assertEquals(listOf(MULTILINGUAL_MODELS[multilingualIndex]), context.selectedWhisperModelsForCurrentSettings("en"))
            context.setSetting(USE_LANGUAGE_SPECIFIC_MODELS, true)
            assertEquals(listOf(ENGLISH_MODELS[englishIndex]), context.selectedWhisperModelsForCurrentSettings("en"))
        } finally {
            context.setSetting(ENABLE_MULTILINGUAL, enabled)
            context.setSetting(USE_LANGUAGE_SPECIFIC_MODELS, languageSpecific)
            context.setSetting(LANGUAGE_TOGGLES, languages)
        }
    }
}
