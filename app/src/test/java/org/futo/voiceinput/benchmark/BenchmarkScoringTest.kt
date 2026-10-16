package org.futo.voiceinput.benchmark

import org.junit.Assert.*
import org.junit.Test

class BenchmarkScoringTest {
    @Test fun exactAndNormalization() {
        val score = BenchmarkScoring.score("Ｈｅｌｌｏ, WORLD! I'm here.", "hello world im here")
        assertEquals(0.0, score.words!!.rate, 0.0)
        assertEquals(0.0, score.characters!!.rate, 0.0)
    }
    @Test fun countsAndEmptyOutput() {
        assertEquals(EditScore(1, 0, 0, 3), BenchmarkScoring.score("one two three", "one four three").words)
        assertEquals(EditScore(0, 1, 0, 3), BenchmarkScoring.score("one two three", "one three").words)
        assertEquals(EditScore(0, 0, 1, 3), BenchmarkScoring.score("one two three", "one two four three").words)
        assertEquals(EditScore(0, 2, 0, 2), BenchmarkScoring.score("hello world", "").words)
    }
    @Test fun unavailableReferenceAndInsertionsOverOneHundredPercent() {
        listOf(null, "", "?!").forEach { assertNotNull(BenchmarkScoring.score(it, "hello").unavailable) }
        assertTrue(BenchmarkScoring.score("hello", "hello one two three").words!!.rate > 1)
    }
    @Test fun numbersAndFillersAreNotSilentlyEquivalent() {
        assertTrue(BenchmarkScoring.score("twenty four", "24").words!!.rate > 0)
        assertEquals(1, BenchmarkScoring.score("um hello", "hello").words!!.deletions)
        assertEquals("twenty four", BenchmarkScoring.normalize("twenty-four"))
    }
}
