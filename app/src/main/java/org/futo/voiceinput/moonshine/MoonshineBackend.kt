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

class MoonshineBackend(
    private val variant: MoonshineModelVariant
) : StreamingSpeechBackend {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var transcriber: Transcriber? = null
    private var audio: Channel<FloatArray>? = null
    private var worker: Job? = null
    private var completedText = ""
    private var currentText = ""

    override suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        transcriber = Transcriber().apply {
            loadFromFiles(
                context.applicationContext.moonshineModelDir(variant).absolutePath,
                when (variant) {
                    MoonshineModelVariant.Small -> JNI.MOONSHINE_MODEL_ARCH_SMALL_STREAMING
                    MoonshineModelVariant.Medium -> JNI.MOONSHINE_MODEL_ARCH_MEDIUM_STREAMING
                }
            )
        }
    }

    override suspend fun transcribe(samples: FloatArray): String = withContext(Dispatchers.Default) {
        transcriberOrThrow().transcribeWithoutStreaming(samples, 16_000).lines
            .mapNotNull { it.text }
            .joinToString(" ")
            .trim()
    }

    override fun startStreaming(onPartial: (String) -> Unit) {
        completedText = ""
        currentText = ""
        val transcriber = transcriberOrThrow()
        transcriber.removeAllListeners()
        transcriber.addListener { event ->
            event.accept(object : TranscriptEventListener() {
                override fun onLineTextChanged(event: TranscriptEvent.LineTextChanged) {
                    currentText = event.line.text.orEmpty()
                    onPartial(currentTranscript())
                }

                override fun onLineCompleted(event: TranscriptEvent.LineCompleted) {
                    completedText = listOf(completedText, event.line.text.orEmpty())
                        .filter(String::isNotBlank)
                        .joinToString(" ")
                    currentText = ""
                    onPartial(currentTranscript())
                }
            })
        }
        transcriber.start()
        audio = Channel(Channel.UNLIMITED)
        worker = scope.launch {
            for (chunk in audio!!) transcriber.addAudio(chunk, 16_000)
        }
    }

    override fun acceptAudio(samples: FloatArray) {
        audio?.trySend(samples)
    }

    override suspend fun finishStreaming(): String {
        audio?.close()
        worker?.join()
        withContext(Dispatchers.Default) { transcriberOrThrow().stop() }
        audio = null
        worker = null
        return currentTranscript()
    }

    override suspend fun close() {
        val wasStreaming = audio != null
        audio?.close()
        worker?.cancelAndJoin()
        if (wasStreaming) {
            runCatching { withContext(Dispatchers.Default) { transcriber?.stop() } }
        }
        audio = null
        worker = null
        transcriber?.removeAllListeners()
        transcriber = null
        scope.cancel()
    }

    private fun currentTranscript() = listOf(completedText, currentText)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .trim()

    private fun transcriberOrThrow() =
        transcriber ?: throw IllegalStateException("Moonshine backend is not loaded")
}
