package org.futo.voiceinput.parakeet

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val JFK_WAV_DATA_OFFSET = 78

@RunWith(AndroidJUnit4::class)
class ParakeetSherpaSmokeTest {
    @Test
    fun downloadedModelTranscribesKnownAudioSample() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assumeTrue(context.isParakeetModelDownloaded(verifyHashes = true))
        val bytes = context.assets.open("jfk.wav").use { it.readBytes() }
        val pcm = ByteBuffer.wrap(bytes, JFK_WAV_DATA_OFFSET, bytes.size - JFK_WAV_DATA_OFFSET)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        val samples = FloatArray(pcm.remaining()) { pcm.get() / 32768.0f }
        val backend = ParakeetBackend()

        try {
            backend.load(context)
            val result = backend.transcribe(samples).lowercase()
            assertTrue(result.contains("ask not what your country can do for you"))
            assertTrue(result.contains("what you can do for your country"))
        } finally {
            backend.close()
        }
    }
}
