package org.futo.voiceinput.nemotron

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.futo.voiceinput.settings.NEMOTRON_MULTILINGUAL_LANGUAGE

class NemotronLanguageTest {
    @Test
    fun exposesOnlyReadyLanguagesAndAutoDetection() {
        val ids = NEMOTRON_MULTILINGUAL_LANGUAGES.map { it.id }

        assertEquals("auto", ids.first())
        assertTrue(ids.containsAll(listOf("en", "ja", "hi", "zh", "et")))
        assertFalse(ids.any { it in setOf("el", "lt", "lv", "mt", "sl", "he", "th", "nn") })
    }

    @Test
    fun defaultsUnknownPromptsToEnglish() {
        assertEquals("en", NEMOTRON_MULTILINGUAL_LANGUAGE.default)
        assertEquals("en", "unknown".toNemotronLanguageCode())
        assertEquals("auto", "auto".toNemotronLanguageCode())
        assertEquals("ja", "ja".toNemotronLanguageCode())
    }
}
