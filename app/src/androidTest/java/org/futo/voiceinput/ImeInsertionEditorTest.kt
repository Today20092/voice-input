package org.futo.voiceinput

import android.text.Selection
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImeInsertionEditorTest {
    private fun withEditor(text: String, start: Int, end: Int = start,
        test: (EditText, InputConnection) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = EditText(instrumentation.targetContext)
            view.setText(text)
            Selection.setSelection(view.text, start, end)
            test(view, checkNotNull(view.onCreateInputConnection(EditorInfo())))
        }
    }

    @Test fun nativeEditorReplacesReversedSelectionAndFinalReplacesComposition() = withEditor("hello old world", 9, 6) { view, connection ->
        val session = ImeInsertionSession(InputConnectionInsertionEditor(connection) { true })
        assertTrue(session.partial("new"))
        assertTrue(session.partial("new"))
        assertEquals("hello new world", view.text.toString())
        assertEquals(6, BaseInputConnection.getComposingSpanStart(view.text))
        assertTrue(session.finish("better", "en"))
        assertEquals("hello better world", view.text.toString())
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(view.text))
        assertFalse(session.finish("better", "en"))
    }

    @Test fun cursorMovementPreservesTheUsersTextAndCursor() = withEditor("", 0) { view, connection ->
        val session = ImeInsertionSession(InputConnectionInsertionEditor(connection) { true })
        assertTrue(session.partial("hello"))
        Selection.setSelection(view.text, 0)
        assertFalse(session.finish("hello world", "en"))
        assertEquals("hello", view.text.toString())
        assertEquals(0, view.selectionStart)
        session.cancel()
        assertTrue(ImeInsertionSession(InputConnectionInsertionEditor(connection) { true }).finish("next", "en"))
        assertEquals("next hello", view.text.toString())
    }

    @Test fun rejectedWritesAreRetriedAndReplacedConnectionCannotReceiveFinal() = withEditor("", 0) { view, connection ->
        var reject = true
        var current = true
        val rejecting = object : InputConnectionWrapper(connection, false) {
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean =
                if (reject) false else super.setComposingText(text, newCursorPosition)
        }
        val session = ImeInsertionSession(InputConnectionInsertionEditor(rejecting) { current })
        assertFalse(session.partial("hello"))
        reject = false
        assertTrue(session.partial("hello"))
        current = false
        assertFalse(session.finish("changed", "en"))
        session.cancel()
        assertEquals("hello", view.text.toString())
    }

    @Test fun editorWithoutExtractedTextUsesNativeFinalSelectionReplacement() = withEditor("hello old world", 6, 9) { view, connection ->
        val withoutExtraction = object : InputConnectionWrapper(connection, false) {
            override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? = null
        }
        val session = ImeInsertionSession(InputConnectionInsertionEditor(withoutExtraction) { true })
        assertFalse(session.partial("new"))
        assertTrue(session.finish("new", "en"))
        assertEquals("hello new world", view.text.toString())
    }

    @Test fun cancelThenIdenticalUtteranceCreatesANewComposition() = withEditor("", 0) { view, connection ->
        val first = ImeInsertionSession(InputConnectionInsertionEditor(connection) { true })
        assertTrue(first.partial("hello"))
        first.cancel()
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(view.text))
        val second = ImeInsertionSession(InputConnectionInsertionEditor(connection) { true })
        assertTrue(second.partial("hello"))
        assertTrue(second.finish("hello", "en"))
        assertEquals("hello hello", view.text.toString())
    }
}
