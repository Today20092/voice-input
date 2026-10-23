package org.futo.voiceinput.phonon

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.recognition.RecognitionModelStore
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class PhononNativeTest {
    @Test fun packagedRuntimeRejectsMissingModelAndInvalidHandle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repeat(2) {
            assertThrows(IllegalStateException::class.java) {
                PhononNative.load(File(context.cacheDir, "missing-phonon").absolutePath,
                    context.applicationInfo.nativeLibraryDir)
            }
        }
        assertThrows(IllegalStateException::class.java) {
            PhononNative.transcribe(0, floatArrayOf(0f))
        }
        PhononNative.free(0)
    }

    @Test fun installedModelTranscribesAndReloads() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue("Download Phonon 2 before this device test",
            RecognitionModelStore(context.filesDir).isInstalled(PhononModel.model))
        val fixture = InstrumentationRegistry.getInstrumentation().context.assets
            .open("redux-speech.wav").use { it.readBytes() }
        val pcm = ByteBuffer.wrap(fixture, 44, fixture.size - 44)
            .order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = FloatArray(pcm.remaining()) { pcm.get().toFloat() / 32768f }
        // One full-scale negative sample used to reject the entire recording before inference.
        samples[samples.lastIndex] = Short.MIN_VALUE.toFloat() / 32768f
        val backend = PhononBackend()
        repeat(2) {
            try {
                backend.load(context)
                assertEquals("en", backend.detectedLanguage)
                assertEquals("", backend.transcribe(floatArrayOf()))
                val text = backend.transcribe(samples).lowercase()
                assertTrue(text, text.contains("like the old portrait"))
                for (invalid in floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, -1.01f, 1.01f)) {
                    try {
                        backend.transcribe(floatArrayOf(invalid))
                        fail("Invalid audio must be rejected")
                    } catch (_: PhononAudioException) { }
                }
                assertTrue(backend.transcribe(samples).lowercase().contains("like the old portrait"))
            } finally {
                backend.close()
                backend.close()
            }
        }
    }
}
