package org.futo.voiceinput.nemotron

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class NemotronNativeSmokeTest {
    @Test
    fun downloadedModelTranscribesKnownAudioSample() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assumeTrue(context.isNemotronModelDownloaded(verifyHashes = true))
        val bytes = context.assets.open("audio.floats.bin").use { it.readBytes() }
        val samples = FloatArray(bytes.size / Float.SIZE_BYTES)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(samples)
        val backend = NemotronBackend()

        backend.load(context)
        val result = backend.transcribe(samples)
        backend.close()

        assertTrue(result.isNotBlank())
    }
}
