package org.futo.voiceinput.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class EndOfSpeechProfileTest {
    @Test
    fun profilesProvideDistinctSilenceThresholds() {
        assertEquals(33, EndOfSpeechProfile.Fast.silenceFrames)
        assertEquals(66, EndOfSpeechProfile.Balanced.silenceFrames)
        assertEquals(100, EndOfSpeechProfile.Patient.silenceFrames)
    }

    @Test
    fun unknownStoredProfileFallsBackToBalanced() {
        assertEquals(EndOfSpeechProfile.Balanced, "unknown".toEndOfSpeechProfile())
    }
}
