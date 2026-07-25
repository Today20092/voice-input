package org.futo.voiceinput.backend

import android.content.Context

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

internal class StreamingAudioReplay {
    private val pendingAudio = mutableListOf<FloatArray>()
    private var activeBackend: StreamingSpeechBackend? = null
    private var enabled = false

    @Synchronized
    fun reset(enabled: Boolean = false) {
        pendingAudio.clear()
        activeBackend = null
        this.enabled = enabled
    }

    @Synchronized
    fun isEnabled() = enabled

    @Synchronized
    fun acceptAudio(samples: FloatArray) {
        if (!enabled) return

        val backend = activeBackend
        if (backend == null) {
            pendingAudio.add(samples)
        } else {
            backend.acceptAudio(samples)
        }
    }

    @Synchronized
    fun start(
        backend: StreamingSpeechBackend,
        onPartial: (String) -> Unit,
        onCatchingUp: (Boolean) -> Unit
    ) {
        if (!enabled || activeBackend === backend) return
        check(activeBackend == null)

        backend.startStreaming(onPartial, onCatchingUp)
        pendingAudio.forEach(backend::acceptAudio)
        pendingAudio.clear()
        activeBackend = backend
    }
}
