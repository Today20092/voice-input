package org.futo.voiceinput.s1

import org.futo.voiceinput.settings.S1MiniContext
import org.futo.voiceinput.settings.S1MiniStructure
import org.futo.voiceinput.settings.S1MiniStyling

object S1MiniPrompt {
    const val SYSTEM = "You are a text normalizer for speech-to-text transcripts. The input begins " +
        "with a control line specifying the styling, structure, and context settings; clean the " +
        "transcript to match those settings and output only the cleaned text."

    fun controlLine(
        styling: S1MiniStyling,
        structure: S1MiniStructure,
        context: S1MiniContext
    ) = "[Styling: ${styling.id}] [Structure: ${structure.id}] [Context: ${context.id}]"

    fun formattedPrompt(
        transcript: String,
        styling: S1MiniStyling,
        structure: S1MiniStructure,
        context: S1MiniContext
    ): String = buildString {
        append("<|im_start|>system\n")
        append(SYSTEM)
        append("<|im_end|>\n<|im_start|>user\n")
        append(controlLine(styling, structure, context))
        append('\n')
        append(transcript)
        append("<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n")
    }

    /** Conservative word-based proxy for the publisher's ~1,000-token request guidance. */
    fun chunkTranscript(transcript: String, maxWords: Int = 650): List<String> {
        val cleaned = transcript.trim()
        if (cleaned.isEmpty()) return listOf("")
        val words = cleaned.split(Regex("\\s+"))
        if (words.size <= maxWords) return listOf(cleaned)

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < words.size) {
            val hardEnd = minOf(start + maxWords, words.size)
            var end = hardEnd
            if (hardEnd < words.size) {
                val searchStart = maxOf(start + maxWords / 2, start)
                for (index in hardEnd - 1 downTo searchStart) {
                    if (words[index].lastOrNull() in charArrayOf('.', '!', '?', ';', ':')) {
                        end = index + 1
                        break
                    }
                }
            }
            chunks += words.subList(start, end).joinToString(" ")
            start = end
        }
        return chunks
    }
}
