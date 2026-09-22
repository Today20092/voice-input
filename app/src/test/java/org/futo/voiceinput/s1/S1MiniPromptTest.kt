package org.futo.voiceinput.s1

import org.futo.voiceinput.settings.S1MiniContext
import org.futo.voiceinput.settings.S1MiniStructure
import org.futo.voiceinput.settings.S1MiniStyling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class S1MiniPromptTest {
    @Test
    fun promptMatchesPublisherTemplateAndDisablesThinking() {
        val prompt = S1MiniPrompt.formattedPrompt(
            "hello comma world",
            S1MiniStyling.SemiFormal,
            S1MiniStructure.Prose,
            S1MiniContext.General
        )

        assertTrue(prompt.startsWith("<|im_start|>system\n${S1MiniPrompt.SYSTEM}<|im_end|>"))
        assertTrue(prompt.contains("[Styling: semi-formal] [Structure: prose] [Context: general]\n"))
        assertTrue(prompt.endsWith("<|im_start|>assistant\n<think>\n\n</think>\n\n"))
    }

    @Test
    fun shortTranscriptRemainsOneChunk() {
        assertEquals(listOf("one two three"), S1MiniPrompt.chunkTranscript("  one two three  ", 10))
    }

    @Test
    fun longTranscriptPrefersPunctuationBoundaryWithoutLosingWords() {
        val source = "one two three four. five six seven eight nine ten eleven"
        val chunks = S1MiniPrompt.chunkTranscript(source, maxWords = 8)

        assertEquals(listOf("one two three four.", "five six seven eight nine ten eleven"), chunks)
        assertEquals(source, chunks.joinToString(" "))
    }
}
