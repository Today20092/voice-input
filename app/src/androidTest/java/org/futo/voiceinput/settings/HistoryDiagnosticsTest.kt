package org.futo.voiceinput.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.futo.voiceinput.diagnostics.AppDiagnostics
import org.futo.voiceinput.diagnostics.DiagnosticEvent
import org.futo.voiceinput.diagnostics.DiagnosticMetric
import org.futo.voiceinput.diagnostics.DiagnosticRecord
import org.futo.voiceinput.diagnostics.DiagnosticStore
import org.futo.voiceinput.history.retranscribeAudio
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.zip.ZipFile

class HistoryDiagnosticsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun missingRecordingProducesContentFreeFailedHistorySession() {
        // The test app starts with standard diagnostics enabled.
        compose.waitUntil(5_000) { AppDiagnostics.collectionEnabled() }
        val since = System.currentTimeMillis()
        val context = compose.activity
        runBlocking {
            val failure = runCatching {
                context.retranscribeAudio("0-00000000-0000-0000-0000-000000000000", context.lifecycleScope)
            }.exceptionOrNull()
            assertNotNull(failure)
            val file = AppDiagnostics.export(context, "", "")
            try {
                ZipFile(file).use { zip ->
                    val text = zip.getInputStream(zip.getEntry("events.jsonl")).bufferedReader().readText()
                    val records = text.lineSequence().filter { it.isNotBlank() }
                        .map { DiagnosticStore.json.decodeFromString<DiagnosticRecord>(it) }
                        .filter { it.timestampMs >= since }.toList()
                    val start = records.last { it.event == DiagnosticEvent.SESSION_STARTED &&
                        it.metrics[DiagnosticMetric.RETRANSCRIPTION] == 1L }
                    assertTrue(records.any { it.sessionId == start.sessionId && it.event == DiagnosticEvent.SESSION_FAILED })
                    assertFalse(records.any { it.sessionId == start.sessionId && it.event == DiagnosticEvent.RETRANSCRIPTION_FINISHED })
                    assertFalse(text.contains("Recording is no longer available"))
                    assertFalse(text.contains("00000000-0000-0000-0000-000000000000"))
                }
            } finally { file.delete() }
        }
    }
}
