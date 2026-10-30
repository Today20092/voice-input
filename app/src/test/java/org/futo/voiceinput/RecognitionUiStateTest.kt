package org.futo.voiceinput

import org.junit.Assert.*
import org.junit.Test

class RecognitionUiStateTest {
    @Test fun terminalStateClearsTextAndIgnoresQueuedUpdatesUntilANewSession() {
        val speaking = RecognitionUiState().start("Orukeet").recording().partial("Old text")
        val ended = speaking.end()
            .partial("Late partial")
            .status("Catching up", streaming = true)
            .waveform(listOf(-1f to 1f), MagnitudeState.TALKING)
            .processing("Late processing")
            .cleaning("Late cleanup")
        assertEquals(RecognitionUiState(), ended)
        val next = ended.start("Moonshine").recording()
        assertEquals("", next.partialText)
        assertEquals("Moonshine", next.modelName)
        assertTrue(next.bars.isEmpty())
    }

    @Test fun cleanupRetainsLatestRevisionAndFinalOnlySessionsHaveNoProvisionalText() {
        val initial = RecognitionUiState().start("Whisper").recording()
        assertEquals("", initial.processing("Processing").partialText)
        val revised = initial.partial("wrong").partial("").partial("revised")
        val cleaning = revised.processing("Processing").cleaning("Cleaning")
            .partial("Late text").status("Late status")
        assertEquals("revised", cleaning.partialText)
        assertEquals("Cleaning", cleaning.statusText)
        assertFalse(cleaning.isRecording)
    }

    @Test fun waveformAndCatchUpDoNotReplaceTheProvisionalTranscript() {
        val bars = listOf(-0.4f to 0.5f)
        var state = RecognitionUiState().start("Orukeet")
        state = state.recording().partial("A provisional sentence")
        state = state.waveform(bars, MagnitudeState.TALKING)
        state = state.status("Catching up", streaming = true)

        assertEquals("A provisional sentence", state.partialText)
        assertEquals(bars, state.bars)
        assertEquals("Orukeet", state.modelName)
        assertEquals("Catching up", state.statusText)
        assertTrue(state.isRecording)

        state = state.processing("Processing")
        state = state.status("Listening", streaming = true)
        assertEquals("Processing", state.statusText)
        assertEquals("A provisional sentence", state.partialText)
        assertFalse(state.isRecording)
    }
}
