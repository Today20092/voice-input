package org.futo.voiceinput.backend

import android.content.Context
import java.nio.FloatBuffer

// Keep required snapshot allocation outside the caller's recognition timer.
internal fun SpeechBackend.prepareRecordingFinalization(samples: FloatBuffer): suspend () -> String {
    if (this is StreamingSpeechBackend) return { finishStreaming() }
    val snapshot = samples.array().copyOf(samples.position())
    // A capture that fails before its first read has no audio; do not hand an empty buffer to native decoders.
    return { if (snapshot.isEmpty()) "" else transcribe(snapshot) }
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
