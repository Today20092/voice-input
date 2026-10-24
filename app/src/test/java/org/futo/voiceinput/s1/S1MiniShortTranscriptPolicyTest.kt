package org.futo.voiceinput.s1

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class S1MiniShortTranscriptPolicyTest {
    @Test fun shortRepliesCanSkipButTheTenWordBoundaryStillRewrites() {
        assertTrue(S1MiniShortTranscriptPolicy.shouldSkip("Sounds good, see you then."))
        assertTrue(S1MiniShortTranscriptPolicy.shouldSkip("one two three four five six seven eight nine"))
        assertFalse(S1MiniShortTranscriptPolicy.shouldSkip("one two three four five six seven eight nine ten"))
        assertFalse(S1MiniShortTranscriptPolicy.shouldSkip(" \n\t"))
    }

    @Test fun fillersCorrectionsAndRepetitionsRetainCleanup() {
        for (text in listOf("UM, hello", "uh yes", "erm, okay", "hmm yes", "you know, yes",
            "I mean yes", "wait, tomorrow", "no, actually tomorrow", "scratch that, tomorrow",
            "the the report", "I—I agree", "Yes. YES!")) {
            assertFalse(text, S1MiniShortTranscriptPolicy.shouldSkip(text))
        }
    }

    @Test fun tokenBoundariesPreserveApostrophesAndDoNotMatchSubstrings() {
        assertTrue(S1MiniShortTranscriptPolicy.shouldSkip("The umbrella is white."))
        assertTrue(S1MiniShortTranscriptPolicy.shouldSkip("I’m sure it's ready."))
        assertTrue(S1MiniShortTranscriptPolicy.shouldSkip("I'm sure it’s ready."))
        assertFalse(S1MiniShortTranscriptPolicy.shouldSkip("It’s it’s ready."))
        assertFalse(S1MiniShortTranscriptPolicy.shouldSkip("It's it’s ready."))
        assertFalse(S1MiniShortTranscriptPolicy.shouldSkip("You KNOW; it’s ready."))
    }
}
