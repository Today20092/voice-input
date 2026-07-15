package org.futo.voiceinput.parakeet

import android.content.Context

interface SpeechBackend {
    val streamsAudio: Boolean get() = false

    suspend fun load(context: Context)
    suspend fun transcribe(samples: FloatArray): String
    fun startStreaming(onPartial: (String) -> Unit) = Unit
    fun acceptAudio(samples: FloatArray) = Unit
    suspend fun finishStreaming(): String = throw UnsupportedOperationException()
    suspend fun close()
}
