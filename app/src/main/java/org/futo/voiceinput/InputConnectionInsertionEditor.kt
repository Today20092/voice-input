package org.futo.voiceinput

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/** Never follows a newly focused editor. Each instance belongs to one connection. */
internal class InputConnectionInsertionEditor(
    private val connection: InputConnection,
    private val isCurrent: () -> Boolean
) : InsertionEditor {
    override fun snapshot(): InsertionSnapshot? {
        if (!isCurrent()) return null
        return try {
            val extracted = connection.getExtractedText(ExtractedTextRequest().apply {
                hintMaxChars = 4096
            }, 0)
            val text = extracted?.text
            val snapshot = if (extracted != null && text != null && extracted.startOffset >= 0 &&
                extracted.partialStartOffset < 0 && extracted.selectionStart in 0..text.length &&
                extracted.selectionEnd in 0..text.length) {
                InsertionSnapshot(text.toString(), extracted.selectionStart, extracted.selectionEnd, extracted.startOffset)
            } else {
                // These reads exclude the selection. Native commitText still replaces it normally.
                // Unknown absolute positions disable composing updates, not final-only dictation.
                val before = connection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
                val after = connection.getTextAfterCursor(2, 0)?.toString().orEmpty()
                InsertionSnapshot(before + after, before.length, before.length, offset = -1)
            }
            snapshot.takeIf { isCurrent() }
        } catch (_: RuntimeException) {
            // An editor failure must not put its text or exception message in diagnostics.
            null
        }
    }

    override fun replace(text: String, composing: Boolean): Boolean {
        if (!isCurrent()) return false
        return try {
            if (composing) connection.setComposingText(text, 1) else connection.commitText(text, 1)
        } catch (_: RuntimeException) {
            false
        }
    }

    override fun finishComposition(): Boolean {
        if (!isCurrent()) return false
        return try {
            connection.finishComposingText()
        } catch (_: RuntimeException) {
            // The connection may disappear while finishing input. Do not expose editor contents.
            false
        }
    }
}
