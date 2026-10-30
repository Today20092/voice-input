package org.futo.voiceinput.asr4all

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.settings.*
import org.futo.voiceinput.settings.pages.S1MiniOptions
import org.futo.voiceinput.theme.UixThemeAuto
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Asr4allCleanupUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun cleanupScreenExplainsTheAutomaticBypass() {
        val context: Context = compose.activity.applicationContext
        val saved = runBlocking { context.dataStore.data.first() }
        try {
            runBlocking { context.dataStore.edit {
                it[SPEECH_BACKEND.key] = "asr4all"
                it[S1_MINI_ENABLED.key] = true
            } }
            compose.setContent { UixThemeAuto { S1MiniOptions() } }
            compose.waitUntil(5000) {
                runCatching { compose.onNodeWithText("ASR4ALL already adds punctuation, capital letters, and small corrections. S1-mini is skipped for this model.")
                    .assertIsDisplayed() }.isSuccess
            }
            compose.onNodeWithText("AI rewrite").assertDoesNotExist()
            assertTrue(runBlocking { context.dataStore.data.first()[S1_MINI_ENABLED.key] == true })
        } finally { runBlocking { context.dataStore.updateData { saved } } }
    }
}
