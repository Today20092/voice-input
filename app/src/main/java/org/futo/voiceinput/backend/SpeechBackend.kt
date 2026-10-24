package org.futo.voiceinput.backend

import android.content.Context
import java.nio.FloatBuffer

// Keep required snapshot allocation outside the caller's recognition timer.
internal fun SpeechBackend.prepareRecordingFinalization(samples: FloatBuffer): suspend () -> String {
    if (this is StreamingSpeechBackend) return { finishStreaming() }
    val snapshot = samples.array().copyOf(samples.position())
    return { transcribe(snapshot) }
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
