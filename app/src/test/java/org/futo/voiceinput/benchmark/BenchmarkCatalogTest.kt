package org.futo.voiceinput.benchmark

import org.futo.voiceinput.ENGLISH_MODELS
import org.futo.voiceinput.MULTILINGUAL_MODELS
import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.junit.Assert.*
import org.junit.Test

class BenchmarkCatalogTest {
    @Test fun inventoryComesFromManagedCatalogAndEveryLegacySelection() {
        val models = BenchmarkModel.all()
        assertEquals(RecognitionModelCatalog.models.size + ENGLISH_MODELS.size + MULTILINGUAL_MODELS.size, models.size)
        assertEquals(models.size, models.map { it.id }.distinct().size)
        assertTrue(models.all { it.supportsLanguage("en") })
        assertFalse(models.first { it.managed?.runtimeId == "moonshine" }.supportsLanguage("ar"))
        assertTrue(models.first { it.managed?.runtimeId == "cohere" }.supportsLanguage("ar"))
        assertFalse(models.first { it.managed?.runtimeId == "cohere" }.supportsLanguage("xx"))
        val whistle = models.single { it.id == "whistle" }
        assertEquals("Whistle", whistle.name)
        for (language in listOf("en", "de", "fr", "es", "it", "nl", "pl")) assertTrue(whistle.supportsLanguage(language))
        assertFalse(whistle.supportsLanguage("ar"))
        assertFalse(whistle.supportsLanguage("xx"))
        assertEquals("Moonshine Small", models.first { it.id == "moonshine-small" }.name)
    }
}
