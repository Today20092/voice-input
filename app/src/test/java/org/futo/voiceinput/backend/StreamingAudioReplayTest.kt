package org.futo.voiceinput.backend

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingAudioReplayTest {
    @Test
    fun replaysAudioCapturedBeforeBackendStartsInOrder() {
        val replay = StreamingAudioReplay()
        val backend = FakeStreamingBackend()

        replay.reset(enabled = true)
        replay.acceptAudio(floatArrayOf(1f, 2f))
        replay.acceptAudio(floatArrayOf(3f))
        replay.start(backend, {}, {})
        replay.acceptAudio(floatArrayOf(4f))

        assertEquals(listOf(1f, 2f, 3f, 4f), backend.samples)
        assertEquals(1, backend.starts)
    }

    private class FakeStreamingBackend : StreamingSpeechBackend {
        val samples = mutableListOf<Float>()
        var starts = 0

        override fun startStreaming(
            onPartial: (String) -> Unit,
            onCatchingUp: (Boolean) -> Unit
        ) {
            starts += 1
        }

        override fun acceptAudio(samples: FloatArray) {
            this.samples.addAll(samples.toList())
        }

        override suspend fun load(context: Context) = Unit
        override suspend fun transcribe(samples: FloatArray) = ""
        override suspend fun finishStreaming() = ""
        override suspend fun close() = Unit
    }
}
