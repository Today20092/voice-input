package org.futo.voiceinput.nemotron

import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NemotronProfileTest {
    @Test
    fun persistedIdsSelectTheExpectedProfile() {
        assertEquals(NemotronProfile.LowLatency, "low_latency".toNemotronProfile())
        assertEquals(NemotronProfile.Balanced, "balanced".toNemotronProfile())
        assertEquals(NemotronProfile.Accuracy, "accuracy".toNemotronProfile())
        assertEquals(NemotronProfile.Multilingual, "multilingual".toNemotronProfile())
    }

    @Test
    fun unknownIdsFallBackToBalanced() {
        assertEquals(NemotronProfile.Balanced, "future-profile".toNemotronProfile())
    }

    @Test
    fun profilesUseSeparatePackages() {
        val models = NemotronProfile.entries.map { it.recognitionModel() }

        assertEquals(RecognitionModelCatalog.nemotronEnglishBalanced, NemotronProfile.Balanced.recognitionModel())
        assertEquals(models.size, models.map { it.directoryName }.distinct().size)
        assertEquals(models.size, models.map { it.archive?.url }.distinct().size)
        assertNotEquals(models.first().id, models.last().id)
    }
}
