package org.futo.voiceinput.redux

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.recognition.RecognitionModelStore
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReduxNativeTest {
    @Test
    fun packagedRuntimeRejectsMissingModelWithoutCrashing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertThrows(IllegalStateException::class.java) {
            ReduxNative.load(File(context.cacheDir, "missing-redux-model.gguf").absolutePath)
        }
        ReduxNative.free(0L)
    }

    @Test
    fun installedPackedModelTranscribesSpeechFixture() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue("Download Redux in the app before this device test",
            RecognitionModelStore(context.filesDir).isInstalled(ReduxModel.model))
        val fixture = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .context.assets.open("redux-speech.wav").use { it.readBytes() }
        // The upstream fixture is PCM16 mono 16 kHz with a canonical 44-byte WAV header.
        val pcm = java.nio.ByteBuffer.wrap(fixture, 44, fixture.size - 44)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = FloatArray(pcm.remaining()) { pcm.get().toFloat() / 32768f }
        val backend = ReduxBackend()
        try {
            backend.load(context)
            val text = backend.transcribe(samples).lowercase()
            assertTrue(text, text.contains("undersurface of the clouds"))
        } finally {
            backend.close()
        }
    }
}
