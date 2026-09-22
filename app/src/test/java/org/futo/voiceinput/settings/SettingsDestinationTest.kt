package org.futo.voiceinput.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SettingsDestinationTest {
    @Test
    fun recognitionModelsAndTranscriptCleanupAreSeparateTopLevelDestinations() {
        assertEquals("models", SettingsDestination.Models.route)
        assertEquals("transcriptCleanup", SettingsDestination.TranscriptCleanup.route)
        assertNotEquals(SettingsDestination.Models.route, SettingsDestination.TranscriptCleanup.route)
    }
}
