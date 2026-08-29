package org.futo.voiceinput.s1

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class S1MiniDiagnosticRunTest {
    @Test
    fun serializedRunRecordsWhenItRanAndTheConfiguredWarmDuration() {
        val run = S1MiniDiagnosticRun(
            recordedAtEpochMs = 1_788_029_419_475L,
            runtimeRequested = "auto",
            runtimeSelected = "cpu",
            threads = 4,
            styling = "semi-formal",
            structure = "prose",
            context = "general",
            warmDurationId = "2m",
            warmTimeoutMs = 120_000L,
            warm = true,
            inputApproxWords = 12,
            outputCharacters = 54,
            chunkCount = 1,
            totalMs = 900L,
            nativeMetricsJson = "[]",
            pssKb = 1L,
            nativeHeapBytes = 2L,
            javaUsedBytes = 3L,
            thermalStatus = 0,
            outcome = "success"
        )

        val encoded = Json { encodeDefaults = true }.encodeToString(run)
        val decoded = Json.parseToJsonElement(encoded).jsonObject

        assertEquals(4, decoded.getValue("schemaVersion").toString().toInt())
        assertEquals(1_788_029_419_475L, decoded.getValue("recordedAtEpochMs").toString().toLong())
        assertEquals("\"2m\"", decoded.getValue("warmDurationId").toString())
        assertEquals(120_000L, decoded.getValue("warmTimeoutMs").toString().toLong())
    }
}
