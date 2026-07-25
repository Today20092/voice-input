package org.futo.voiceinput

import org.futo.voiceinput.settings.SpeechBackendType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingSessionPolicyTest {
    @Test
    fun recorderInitializationRetriesAreBounded() {
        assertTrue(RecordingSessionPolicy.shouldRetryRecorderInitialization(32))
        assertFalse(RecordingSessionPolicy.shouldRetryRecorderInitialization(33))
    }

    @Test
    fun cancelledSessionRejectsLateRecorderSamples() {
        assertTrue(RecordingSessionPolicy.shouldAcceptSamples(null, 1L, 1L))
        assertTrue(RecordingSessionPolicy.shouldAcceptSamples(StopReason.Manual, 1L, 1L))
        assertFalse(RecordingSessionPolicy.shouldAcceptSamples(StopReason.Cancel, 1L, 1L))
        assertFalse(RecordingSessionPolicy.shouldAcceptSamples(null, 1L, 2L))
    }

    @Test
    fun stopReasonsPreserveTheirBufferedTailPolicy() {
        assertEquals(275L, RecordingSessionPolicy.tailDrainMs(StopReason.Manual, SpeechBackendType.Moonshine, 275L))
        assertEquals(100L, RecordingSessionPolicy.tailDrainMs(StopReason.Vad, SpeechBackendType.Moonshine, 0L))
        assertEquals(300L, RecordingSessionPolicy.tailDrainMs(StopReason.Vad, SpeechBackendType.Parakeet, 0L))
        assertEquals(100L, RecordingSessionPolicy.tailDrainMs(StopReason.DurationLimit, SpeechBackendType.Moonshine, 0L))
        assertEquals(0L, RecordingSessionPolicy.tailDrainMs(StopReason.Cancel, SpeechBackendType.Moonshine, 275L))
    }

    @Test
    fun manualTailDrainIsClampedToSafeRange() {
        assertEquals(0L, RecordingSessionPolicy.tailDrainMs(StopReason.Manual, SpeechBackendType.Moonshine, -1L))
        assertEquals(1_500L, RecordingSessionPolicy.tailDrainMs(StopReason.Manual, SpeechBackendType.Moonshine, 2_000L))
    }
}
