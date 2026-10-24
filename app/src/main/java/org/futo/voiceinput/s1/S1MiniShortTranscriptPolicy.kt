package org.futo.voiceinput.s1

/** Optional English rewrite bypass; eligibility and the user's opt-in remain the caller's decision. */
object S1MiniShortTranscriptPolicy {
    private val word = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*")
    private val fillers = setOf("um", "uh", "erm", "hmm", "wait")
    private val corrections = setOf("you know", "i mean", "no actually", "scratch that")

    fun shouldSkip(transcript: String): Boolean {
        val words = word.findAll(transcript).take(10)
            .map { it.value.lowercase().replace('’', '\'') }.toList()
        return words.isNotEmpty() && words.size < 10 && words.none(fillers::contains) &&
            words.zipWithNext().none { (first, second) -> first == second || "$first $second" in corrections }
    }
}
