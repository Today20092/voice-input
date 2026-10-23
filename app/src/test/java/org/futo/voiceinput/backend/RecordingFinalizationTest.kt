package org.futo.voiceinput.backend

import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.FloatBuffer

class RecordingFinalizationTest {
    @Test fun streamingFlushNeedsNoSnapshotOrAccessToRecordedSamples() = runBlocking {
        val samples = FloatBuffer.wrap(floatArrayOf(-1f, 0f, 32767f / 32768f))
            .asReadOnlyBuffer().apply { position(3) }
        val backend = object : StreamingSpeechBackend {
            override fun startStreaming(onPartial: (String) -> Unit, onCatchingUp: (Boolean) -> Unit) = Unit
            override fun acceptAudio(samples: FloatArray) = Unit
            override suspend fun load(context: Context) = Unit
            override suspend fun transcribe(samples: FloatArray): String = error("Streaming must only flush")
            override suspend fun finishStreaming() = "Raw final transcript"
            override suspend fun close() = Unit
        }
        assertEquals("Raw final transcript", backend.finishRecording(samples))
        assertEquals(3, samples.position())
    }

    @Test fun finalOnlyReceivesExactlyTheCompleteNormalizedRecordingWithoutMutatingRecoveryAudio() = runBlocking {
        val samples = FloatBuffer.allocate(8).apply { put(floatArrayOf(-1f, 0f, 32767f / 32768f)) }
        val backend = object : SpeechBackend {
            override suspend fun load(context: Context) = Unit
            override suspend fun close() = Unit
            override suspend fun transcribe(samples: FloatArray): String {
                assertArrayEquals(floatArrayOf(-1f, 0f, 32767f / 32768f), samples, 0f)
                samples.fill(42f)
                return "Raw final transcript"
            }
        }
        assertEquals("Raw final transcript", backend.finishRecording(samples))
        assertEquals(3, samples.position())
        assertArrayEquals(floatArrayOf(-1f, 0f, 32767f / 32768f), samples.array().copyOf(3), 0f)
    }

    @Test fun streamingCancellationAndFailurePropagateWithoutReplayingAudio() {
        for (failure in listOf(CancellationException("cancelled"), IllegalStateException("failed"))) {
            val backend = object : StreamingSpeechBackend {
                override fun startStreaming(onPartial: (String) -> Unit, onCatchingUp: (Boolean) -> Unit) = Unit
                override fun acceptAudio(samples: FloatArray) = Unit
                override suspend fun load(context: Context) = Unit
                override suspend fun close() = Unit
                override suspend fun transcribe(samples: FloatArray): String = error("Must not replay failed stream")
                override suspend fun finishStreaming(): String = throw failure
            }
            val samples = FloatBuffer.allocate(1).asReadOnlyBuffer().apply { position(1) }
            val thrown = assertThrows(failure.javaClass) { runBlocking { backend.finishRecording(samples) } }
            assertSame(failure, thrown)
        }
    }
}
