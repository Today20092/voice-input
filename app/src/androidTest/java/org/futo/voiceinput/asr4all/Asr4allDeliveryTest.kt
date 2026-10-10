package org.futo.voiceinput.asr4all

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.text.Selection
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.ImeInsertionSession
import org.futo.voiceinput.InputConnectionInsertionEditor
import org.futo.voiceinput.RecognizeActivity
import org.futo.voiceinput.settings.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Asr4allDeliveryTest {
    @Test fun nativeTranscriptReplacesImePartialsAndReturnsOneActivityResult() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val saved = context.dataStore.data.first()
        var scenario: ActivityScenario<RecognizeActivity>? = null
        val backend = asr4allBackend(context, "m")
        try {
            context.dataStore.edit {
                it[SPEECH_BACKEND.key] = "asr4all"
                it[ASR4ALL_VARIANT.key] = "m"
                it[AUDIO_HISTORY_ENABLED.key] = false
                it[ENABLE_SOUND.key] = false
                it[IS_VAD_ENABLED.key] = false
                it[S1_MINI_ENABLED.key] = true
                it[HARPER_ENABLED.key] = false
            }
            lateinit var editor: EditText
            lateinit var insertion: ImeInsertionSession
            instrumentation.runOnMainSync {
                editor = EditText(context).apply { setText("old selection"); Selection.setSelection(text, 0, text.length) }
                val connection = requireNotNull(editor.onCreateInputConnection(EditorInfo()))
                insertion = ImeInsertionSession(InputConnectionInsertionEditor(connection) { true })
            }
            backend.load(context)
            backend.startStreaming({ partial -> instrumentation.runOnMainSync { assertTrue(insertion.partial(partial)) } })
            val bytes = instrumentation.context.assets.open("jfk.wav").use { it.readBytes() }
            val pcm = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            backend.acceptAudio(FloatArray(pcm.remaining()) { pcm.get() / 32768f })
            val transcript = backend.finishStreaming()
            assertTrue(transcript.lowercase().contains("country"))
            instrumentation.runOnMainSync {
                assertTrue(insertion.finish(transcript, "en"))
                assertEquals(transcript, editor.text.toString())
                assertEquals(-1, BaseInputConnection.getComposingSpanStart(editor.text))
                assertFalse(insertion.finish(transcript, "en"))
            }
            val activeScenario = ActivityScenario.launchActivityForResult<RecognizeActivity>(
                Intent(context, RecognizeActivity::class.java).setAction(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            )
            scenario = activeScenario
            activeScenario.onActivity { activity ->
                RecognizeActivity::class.java.getDeclaredMethod("sendResult", String::class.java, String::class.java)
                    .apply { isAccessible = true }.invoke(activity, transcript, "en")
            }
            val result = activeScenario.result
            assertEquals(Activity.RESULT_OK, result.resultCode)
            assertEquals(listOf(transcript), result.resultData.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS))
        } finally {
            scenario?.close()
            backend.close()
            context.dataStore.updateData { saved }
        }
    }
}
