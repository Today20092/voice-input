package org.futo.voiceinput.backend

import android.content.Context

interface SpeechBackend {
    suspend fun load(context: Context)
    suspend fun transcribe(samples: FloatArray): String
    suspend fun close()
}

interface StreamingSpeechBackend : SpeechBackend {
    fun startStreaming(
        onPartial: (String) -> Unit,
        onCatchingUp: (Boolean) -> Unit = {}
    )
    fun acceptAudio(samples: FloatArray)
    suspend fun finishStreaming(): String
}
