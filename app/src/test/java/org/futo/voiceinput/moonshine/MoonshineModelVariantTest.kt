package org.futo.voiceinput.moonshine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MoonshineModelVariantTest {
    @Test
    fun persistedIdsSelectTheExpectedQuality() {
        assertEquals(MoonshineModelVariant.Small, "small".toMoonshineModelVariant())
        assertEquals(MoonshineModelVariant.Medium, "medium".toMoonshineModelVariant())
    }

    @Test
    fun unknownIdsFallBackToBalanced() {
        assertEquals(MoonshineModelVariant.Small, "future-model".toMoonshineModelVariant())
    }

    @Test
    fun variantsUseSeparateDownloads() {
        assertNotEquals(
            MoonshineModelVariant.Small.directoryName,
            MoonshineModelVariant.Medium.directoryName
        )
        assertNotEquals(
            MoonshineModelVariant.Small.baseUrl,
            MoonshineModelVariant.Medium.baseUrl
        )
    }
}
