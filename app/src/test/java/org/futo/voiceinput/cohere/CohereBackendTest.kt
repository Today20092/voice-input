package org.futo.voiceinput.cohere

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CohereBackendTest {
    @Test
    fun languageSelectionRecreatesDecoderAndClosesItAfterUse() = runBlocking {
        val decoders = mutableListOf<FakeDecoder>()
        val languages = mutableListOf<String>()
        val backend = CohereBackend { _, language ->
            languages += language
            FakeDecoder().also { decoders += it }
        }
        backend.load(File("unused"), "ar")
        assertEquals("ar", backend.detectedLanguage)
        assertEquals("part 1", backend.transcribe(floatArrayOf(0.5f)))
        backend.load(File("unused"), "ar")
        assertEquals(1, decoders.size)
        backend.load(File("unused"), "en")
        assertEquals(listOf("ar", "en"), languages)
        assertEquals(1, decoders[0].closeCount)
        backend.close()
        backend.close()
        assertEquals(1, decoders[1].closeCount)
        assertNull(backend.detectedLanguage)
    }

    @Test
    fun longDictationKeepsEverySampleInBoundedChunks() = runBlocking {
        val decoder = FakeDecoder()
        val backend = CohereBackend { _, _ -> decoder }
        backend.load(File("unused"), "en")
        val samples = FloatArray(80 * 16_000) { it.toFloat() }
        assertEquals("part 1 part 2 part 3", backend.transcribe(samples))
        assertEquals(samples.size, decoder.chunks.sumOf { it.size })
        var offset = 0
        decoder.chunks.forEach { chunk ->
            assertTrue(chunk.size <= 35 * 16_000)
            assertEquals(offset.toFloat(), chunk.first())
            offset += chunk.size
            assertEquals((offset - 1).toFloat(), chunk.last())
        }
        backend.close()
    }

    @Test
    fun chunkBoundaryPrefersQuietAudio() {
        val samples = FloatArray(40 * 16_000) { 0.5f }
        samples.fill(0f, 32 * 16_000, 32 * 16_000 + 1600)
        assertEquals(32 * 16_000 + 800, cohereChunkEnd(samples, 0))
    }

    @Test
    fun emptyAudioDoesNotInvokeNativeDecoder() = runBlocking {
        val decoder = FakeDecoder()
        val backend = CohereBackend { _, _ -> decoder }
        backend.load(File("unused"), "auto")
        assertEquals("en", backend.detectedLanguage)
        assertEquals("", backend.transcribe(floatArrayOf()))
        assertTrue(decoder.chunks.isEmpty())
        backend.close()
    }

    @Test
    fun languagesMatchTheRuntimeAndDoNotOfferAutoDetection() {
        assertEquals(
            setOf("ar", "de", "el", "en", "es", "fr", "it", "ja", "ko", "nl", "pl", "pt", "vi", "zh"),
            CohereLanguage.entries.map { it.id }.toSet()
        )
        assertEquals(CohereLanguage.English, "unsupported".toCohereLanguage())
    }

    private class FakeDecoder : CohereDecoder {
        val chunks = mutableListOf<FloatArray>()
        var closeCount = 0
        override fun transcribe(samples: FloatArray): String {
            chunks += samples
            return " part ${chunks.size} "
        }
        override fun close() { closeCount++ }
    }
}
