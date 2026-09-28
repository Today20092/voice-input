package org.futo.voiceinput.moonshine

import ai.moonshine.voice.JNI
import ai.moonshine.voice.Transcriber
import ai.moonshine.voice.TranscriptEvent
import ai.moonshine.voice.TranscriptEventListener
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.futo.voiceinput.backend.StreamingSpeechBackend

class MoonshineBackend internal constructor(
    private val variant: MoonshineModelVariant,
    private var engine: MoonshineEngine? = null
) : StreamingSpeechBackend {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engineLock = Any()
    private var audio: Channel<FloatArray>? = null
    private var worker: Deferred<Unit>? = null
    @Volatile private var streamFailure: Throwable? = null
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
        synchronized(engineLock) { engineOrThrow().transcribe(samples) }
    }

    override fun startStreaming(
        onPartial: (String) -> Unit,
        onCatchingUp: (Boolean) -> Unit
    ) = synchronized(engineLock) {
        check(audio == null) { "Moonshine stream is already started" }
        streamFailure = null
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
        val queue = Channel<FloatArray>(Channel.UNLIMITED)
        audio = queue
        worker = scope.async {
            try {
                for (chunk in queue) {
                    synchronized(engineLock) { engineOrThrow().addAudio(chunk) }
                }
            } catch (failure: Throwable) {
                streamFailure = failure
                queue.close(failure)
                throw failure
            }
        }
    }

    override fun acceptAudio(samples: FloatArray) {
        streamFailure?.let { throw it }
        checkNotNull(audio) { "Moonshine stream is not started" }.trySend(samples).getOrThrow()
    }

    override suspend fun finishStreaming(): String {
        audio?.close()
        worker?.await()
        withContext(Dispatchers.Default) {
            synchronized(engineLock) { engineOrThrow().stop() }
        }
        audio = null
        worker = null
        return currentTranscript()
    }

    override suspend fun close(): Unit = withContext(NonCancellable) {
        val wasStreaming = audio != null
        audio?.cancel()
        worker?.cancelAndJoin()
        audio = null
        worker = null
        try {
            withContext(Dispatchers.Default) {
                synchronized(engineLock) {
                    val engineToClose = engine
                    engine = null
                    if (wasStreaming) runCatching { engineToClose?.stop() }
                    engineToClose?.close()
                }
            }
        } finally {
            scope.cancel()
        }
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
    private val transcriber = ReleasableMoonshineTranscriber().apply {
        try {
            loadFromFiles(modelPath, architecture)
        } catch (failure: Throwable) {
            try {
                releaseNative()
            } catch (closeFailure: Throwable) {
                failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }

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
    override fun close() {
        transcriber.removeAllListeners()
        transcriber.releaseNative()
    }
}

private class ReleasableMoonshineTranscriber : Transcriber() {
    // Pinned 0.0.68 exposes release only through finalize(), which frees both native
    // handles and resets them to -1. Replace this bridge when upgrading its API.
    fun releaseNative() = super.finalize()
}
