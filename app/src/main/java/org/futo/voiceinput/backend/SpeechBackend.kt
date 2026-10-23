package org.futo.voiceinput.backend

import android.content.Context
import java.nio.FloatBuffer

internal suspend fun SpeechBackend.finishRecording(samples: FloatBuffer): String {
    return (this as? StreamingSpeechBackend)?.finishStreaming()
        ?: transcribe(samples.array().copyOf(samples.position()))
}

interface SpeechBackend {
    val detectedLanguage: String? get() = null
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
