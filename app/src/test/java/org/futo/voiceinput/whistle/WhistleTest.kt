package org.futo.voiceinput.whistle

import kotlinx.coroutines.*
import org.futo.voiceinput.recognition.*
import org.futo.voiceinput.settings.SpeechBackendType
import org.futo.voiceinput.settings.toSpeechBackendType
import org.futo.voiceinput.settings.pages.presentRecognitionModel
import org.futo.voiceinput.s1.S1MiniEnglishGate
import org.futo.voiceinput.harper.HarperEnglishGate
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WhistleTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun catalogSelectionPresentationAndEnglishCleanup() {
        val model = WhistleModel.model
        assertEquals(SpeechBackendType.Whistle, model.runtimeId.toSpeechBackendType())
        assertEquals("orukeet", RecognitionModelCatalog.defaultModel.runtimeId)
        assertEquals(listOf(model), RecognitionModelCatalog.cards.single { it.id == "whistle" }.models)
        val lifecycle = RecognitionModelLifecycle(RecognitionModelStore(temporary.root))
        assertEquals(model, lifecycle.readiness(lifecycle.selectionFor(model))?.model)
        assertEquals(TranscriptionBehavior.FINAL_ONLY, model.transcription)
        assertEquals(16_919_407L, model.transferBytes)
        val presentation = presentRecognitionModel(model, false, false)
        assertTrue(presentation.compactSummary.contains("16.9 MB download"))
        assertTrue(presentation.fields.toMap().getValue("Language selection").contains("automatically"))
        assertEquals(2, presentation.informationLinks.size)
        for (language in listOf(null, "en", "de", "fr", "es", "it", "nl", "pl")) {
            assertEquals(language == "en", S1MiniEnglishGate.isEstablishedEnglish(
                SpeechBackendType.Whistle, language, null, "", "auto", emptySet()))
            assertEquals(language == "en", HarperEnglishGate.isEstablishedEnglish(
                SpeechBackendType.Whistle, language, null, "", "auto", emptySet()))
        }
    }

    @Test fun parsesUnicodeAndSilenceWithoutLeakingMalformedContent() {
        assertEquals(WhistleResult("Grüße à Łódź", "de"),
            parseWhistleResult("""{"text":"Grüße à Łódź","language":"de"}"""))
        assertEquals(WhistleResult("", null), parseWhistleResult("""{"text":"","language":""}"""))
        for (output in listOf("private dictated content", "{}", """{"text":123,"language":"en"}""",
            """{"text":"private dictated content","language":"unknown"}""")) {
            val failure = assertThrows(IllegalStateException::class.java) { parseWhistleResult(output) }
            assertEquals("Whistle returned an invalid result", failure.message)
            assertNull(failure.cause)
        }
    }

    @Test fun rejectsInvalidAudioBeforeStartingProcess() = runBlocking {
        for (value in floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, 1.01f, -1.01f)) {
            try {
                runWhistle(File("missing"), File("missing"), temporary.root, floatArrayOf(value))
                fail("Invalid audio must be rejected")
            } catch (_: IllegalArgumentException) { }
        }
        assertEquals(WhistleResult("", null),
            runWhistle(File("missing"), File("missing"), temporary.root, floatArrayOf()))
    }

    @Test fun publishedRuntimeTranscribesSilenceReloadsAndCancels() = runBlocking {
        val runner = System.getenv("WHISTLE_TEST_RUNNER")
        val model = System.getenv("WHISTLE_TEST_MODEL")
        assumeNotNull(runner, model)
        val wav = File("src/androidTest/assets/redux-speech.wav").readBytes()
        val pcm = ByteBuffer.wrap(wav, 44, wav.size - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = FloatArray(pcm.remaining()) { pcm.get().toFloat() / 32768f }
        val work = File(temporary.root, "work")
        repeat(2) {
            val result = runWhistle(File(runner!!), File(model!!), work, samples)
            assertEquals("en", result.language)
            assertTrue(result.text, result.text.contains("like the old portrait"))
            assertFalse(work.exists())
        }
        assertEquals(WhistleResult("", null), runWhistle(File(runner!!), File(model!!), work, FloatArray(16000)))
        val pending = launch(Dispatchers.IO) { runWhistle(File(runner), File(model), work, FloatArray(16000 * 300) { samples[it % samples.size] }) }
        withTimeout(10000) { while (!File(work, "result.json").exists()) delay(10) }
        pending.cancelAndJoin()
        assertFalse(work.exists())
    }
}
