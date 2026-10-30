package org.futo.voiceinput

/** The editor boundary. Snapshots are transient and must never be logged or persisted. */
internal interface InsertionEditor {
    fun snapshot(): InsertionSnapshot?
    fun replace(text: String, composing: Boolean): Boolean
    fun finishComposition(): Boolean
}

internal class InsertionSnapshot(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val offset: Int = 0
)

internal data class ImeEditorKey(val packageName: String?, val fieldId: Int, val inputType: Int)

/** Owns dictation across Android input callbacks, including view-only recreation. */
internal class ImeInputSessionLifecycle(private val resetRecognizer: () -> Unit) {
    var insertion: ImeInsertionSession? = null
        private set
    private var editorKey: ImeEditorKey? = null
    private var connection: Any? = null
    private var needsInitialization = true

    fun startInput(key: ImeEditorKey, connection: Any?, restarting: Boolean) {
        if (!restarting || editorKey != key || this.connection !== connection) cancel()
        editorKey = key
        this.connection = connection
    }

    fun startView(connection: Any?, editor: InsertionEditor?, automaticSpacing: Boolean): Boolean {
        if (this.connection !== connection) cancel()
        this.connection = connection
        if (!needsInitialization) return false
        insertion = editor?.let { ImeInsertionSession(it, automaticSpacing) }
        needsInitialization = false
        return true
    }

    fun finishView(recreating: Boolean) {
        if (!recreating) cancel()
    }

    fun finishInput() {
        cancel()
        editorKey = null
    }

    fun cancel() {
        insertion?.cancel()
        insertion = null
        connection = null
        needsInitialization = true
        resetRecognizer()
    }
}

/** One utterance in one input connection, independent of the input view's lifetime. */
internal class ImeInsertionSession(
    private val editor: InsertionEditor,
    private val automaticSpacing: Boolean = true
) {
    private var active = true
    private var started = false
    private var composingStart = -1
    private var composingText: String? = null
    private val pendingEnds = mutableListOf<Int>()

    fun partial(text: String): Boolean = deliver(text, composing = true, language = null)

    fun finish(text: String, language: String?): Boolean = deliver(text, composing = false, language)

    fun cancel() {
        // Keep already accepted text. Removing it could undo a selection replacement or user edit.
        // Finishing composition does not change text or selection, even after ownership was lost.
        if (composingText != null) editor.finishComposition()
        active = false
        composingText = null
        pendingEnds.clear()
    }

    fun selectionChanged(start: Int, end: Int, candidatesStart: Int, candidatesEnd: Int) {
        val previous = composingText ?: return
        if (!active) return
        val pending = pendingEnds.indexOf(end)
        if (start == end && candidatesStart == composingStart && candidatesEnd == end &&
            (end == composingStart + previous.length || pending >= 0)) {
            // Editor notifications may acknowledge earlier successful writes after a newer one.
            if (pending >= 0) pendingEnds.subList(0, pending + 1).clear()
        } else {
            active = false
        }
    }

    private fun deliver(text: String, composing: Boolean, language: String?): Boolean {
        if (!active) return false
        if (!started) {
            // An earlier IME/connection may have left a composing span around unrelated text.
            if (!editor.finishComposition()) return false
            started = true
        }
        val snapshot = editor.snapshot() ?: return false
        val previous = composingText
        // A blank interim hypothesis must not erase the user's selection or the last partial.
        if (composing && text.isBlank()) return true
        // Without absolute selection offsets we cannot safely replace a later partial.
        if (composing && snapshot.offset < 0) return false
        val from = if (previous == null) minOf(snapshot.selectionStart, snapshot.selectionEnd)
            else composingStart - snapshot.offset
        val to = if (previous == null) maxOf(snapshot.selectionStart, snapshot.selectionEnd)
            else from + previous.length
        if (from !in 0..snapshot.text.length || to !in from..snapshot.text.length) return false
        if (previous != null && !ownsComposition(snapshot)) {
            active = false
            return false
        }
        if (text.isEmpty() && previous == null) {
            active = false
            return true
        }
        val spaced = if (text.isNotEmpty() && automaticSpacing &&
            language?.substringBefore('-') !in setOf("zh", "ja", "th", "lo", "km", "my")) {
            val before = if (from > 0) snapshot.text.codePointBefore(from) else -1
            val after = if (to < snapshot.text.length) snapshot.text.codePointAt(to) else -1
            (if (needsSpace(before, text.codePointAt(0))) " " else "") + text +
                (if (needsSpace(text.codePointBefore(text.length), after)) " " else "")
        } else text
        if (composing && spaced == previous) return true
        if (!editor.replace(spaced, composing)) return false
        if (composing) {
            composingStart = snapshot.offset + from
            composingText = spaced
            pendingEnds.add(composingStart + spaced.length)
        } else {
            active = false
            composingText = null
        }
        return true
    }

    private fun ownsComposition(snapshot: InsertionSnapshot): Boolean {
        val previous = composingText ?: return false
        if (snapshot.offset < 0) return false
        val from = composingStart - snapshot.offset
        val to = from + previous.length
        return from >= 0 && to <= snapshot.text.length && snapshot.selectionStart == to &&
            snapshot.selectionEnd == to && snapshot.text.substring(from, to) == previous
    }

    private fun needsSpace(left: Int, right: Int): Boolean {
        if (left < 0 || right < 0) return false
        val unspaced = setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA, Character.UnicodeScript.THAI,
            Character.UnicodeScript.LAO, Character.UnicodeScript.KHMER, Character.UnicodeScript.MYANMAR)
        if (Character.UnicodeScript.of(left) in unspaced || Character.UnicodeScript.of(right) in unspaced) return false
        val leftWord = Character.isLetterOrDigit(left) || left.toChar() in ".,!?;:" ||
            Character.getType(left) in setOf(Character.END_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt())
        val rightWord = Character.isLetterOrDigit(right) ||
            Character.getType(right) in setOf(Character.START_PUNCTUATION.toInt(), Character.INITIAL_QUOTE_PUNCTUATION.toInt())
        return leftWord && rightWord
    }
}
