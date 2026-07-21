package org.futo.voiceinput.moonshine

import ai.moonshine.voice.JNI
import ai.moonshine.voice.Transcriber
import ai.moonshine.voice.TranscriptEvent
import ai.moonshine.voice.TranscriptEventListener
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.voiceinput.backend.StreamingSpeechBackend

class MoonshineBackend internal constructor(
    private val variant: MoonshineModelVariant,
    private var engine: MoonshineEngine? = null
) : StreamingSpeechBackend {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var audio: Channel<FloatArray>? = null
    private var worker: Job? = null
    private var completedText = ""
    private var currentText = ""

    override suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        engine = MoonshineTranscriberEngine(
            context.applicationContext.moonshineModelDir(variant).absolutePath,
            when (variant) {
                MoonshineModelVariant.Small -> JNI.MOONSHINE_MODEL_ARCH_SMALL_STREAMING
                MoonshineModelVariant.Medium -> JNI.MOONSHINE_MODEL_ARCH_MEDIUM_STREAMING
            }
        )
    }

    override suspend fun transcribe(samples: FloatArray): String = withContext(Dispatchers.Default) {
        engineOrThrow().transcribe(samples)
    }

    override fun startStreaming(
        onPartial: (String) -> Unit,
        onCatchingUp: (Boolean) -> Unit
    ) {
        completedText = ""
        currentText = ""
        val engine = engineOrThrow()
        engine.start { text, completed ->
            if (completed) {
                completedText = listOf(completedText, text)
                    .filter(String::isNotBlank)
                    .joinToString(" ")
                currentText = ""
            } else {
                currentText = text
            }
            onPartial(currentTranscript())
        }
        audio = Channel(Channel.UNLIMITED)
        worker = scope.launch {
            for (chunk in audio!!) engine.addAudio(chunk)
        }
    }

    override fun acceptAudio(samples: FloatArray) {
        audio?.trySend(samples)
    }

    override suspend fun finishStreaming(): String {
        audio?.close()
        worker?.join()
        withContext(Dispatchers.Default) { engineOrThrow().stop() }
        audio = null
        worker = null
        return currentTranscript()
    }

    override suspend fun close() {
        val wasStreaming = audio != null
        audio?.close()
        worker?.cancelAndJoin()
        if (wasStreaming) {
            runCatching { withContext(Dispatchers.Default) { engine?.stop() } }
        }
        audio = null
        worker = null
        engine?.close()
        engine = null
        scope.cancel()
    }

    private fun currentTranscript() = listOf(completedText, currentText)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .trim()

    private fun engineOrThrow() =
        engine ?: throw IllegalStateException("Moonshine backend is not loaded")
}

internal interface MoonshineEngine {
    fun transcribe(samples: FloatArray): String
    fun start(onTranscript: (text: String, completed: Boolean) -> Unit)
    fun addAudio(samples: FloatArray)
    fun stop()
    fun close()
}

private class MoonshineTranscriberEngine(modelPath: String, architecture: Int) : MoonshineEngine {
    private val transcriber = Transcriber().apply { loadFromFiles(modelPath, architecture) }

    override fun transcribe(samples: FloatArray) =
        transcriber.transcribeWithoutStreaming(samples, 16_000).lines
            .mapNotNull { it.text }
            .joinToString(" ")
            .trim()

    override fun start(onTranscript: (String, Boolean) -> Unit) {
        transcriber.removeAllListeners()
        transcriber.addListener { event ->
            event.accept(object : TranscriptEventListener() {
                override fun onLineTextChanged(event: TranscriptEvent.LineTextChanged) =
                    onTranscript(event.line.text.orEmpty(), false)

                override fun onLineCompleted(event: TranscriptEvent.LineCompleted) =
                    onTranscript(event.line.text.orEmpty(), true)
            })
        }
        transcriber.start()
    }

    override fun addAudio(samples: FloatArray) = transcriber.addAudio(samples, 16_000)
    override fun stop() = transcriber.stop()
    override fun close() = transcriber.removeAllListeners()
}
