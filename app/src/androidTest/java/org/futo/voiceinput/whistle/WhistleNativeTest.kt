package org.futo.voiceinput.whistle

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.recognition.RecognitionModelStore
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WhistleNativeTest {
    @Test fun packagedRunnerStarts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val process = ProcessBuilder(File(context.applicationInfo.nativeLibraryDir,
            "libwhistle_runner.so").absolutePath, "--help").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(0, process.waitFor())
        assertTrue(output.contains("--audio"))
    }

    @Test fun installedModelTranscribesAndReloads() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue("Install Whistle before running this test",
            RecognitionModelStore(context.filesDir).isInstalled(WhistleModel.model))
        val wav = InstrumentationRegistry.getInstrumentation().context.assets.open("redux-speech.wav").use { it.readBytes() }
        val pcm = ByteBuffer.wrap(wav, 44, wav.size - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = FloatArray(pcm.remaining()) { pcm.get().toFloat() / 32768f }
        val backend = WhistleBackend()
        repeat(2) {
            try {
                backend.load(context)
                val text = backend.transcribe(samples)
                assertTrue(text, text.contains("like the old portrait"))
                assertEquals("en", backend.detectedLanguage)
                assertEquals("", backend.transcribe(FloatArray(16000)))
                assertNull(backend.detectedLanguage)
                assertFalse(File(context.cacheDir, "whistle-work").exists())
            } finally { backend.close() }
        }
    }
}
