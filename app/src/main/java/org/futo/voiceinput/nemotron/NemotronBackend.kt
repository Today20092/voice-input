package org.futo.voiceinput.nemotron

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.futo.voiceinput.backend.StreamingSpeechBackend
import java.io.File
import java.util.concurrent.atomic.AtomicLong

private const val SAMPLE_RATE = 16_000

internal interface SherpaStreamingDecoder {
    fun setLanguage(language: String) {}
    fun acceptAudio(samples: FloatArray): String
    fun finish(): String
    fun close()
}

class SherpaStreamingBackend internal constructor(
    private val modelDirectory: (Context) -> File = { it.nemotronModelDirectory() },
    private val backendName: String = "Nemotron",
    private val decoderFactory: (File) -> SherpaStreamingDecoder = { SherpaOnlineDecoder(it) },
    private val catchingUpSamples: Int = SAMPLE_RATE,
    private var decoder: SherpaStreamingDecoder? = null
) : StreamingSpeechBackend {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queuedSamples = AtomicLong()
    private var audio: Channel<FloatArray>? = null
    private var worker: Deferred<Unit>? = null
    private var onPartial: (String) -> Unit = {}
    private var onCatchingUp: (Boolean) -> Unit = {}
    private var catchingUp = false
    private var lastText = ""

    override suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        load(modelDirectory(context), context.selectedNemotronLanguageCode())
    }

    internal fun load(modelDirectory: File, languageCode: String?) {
        if (decoder == null) {
            decoder = decoderFactory(modelDirectory).also { decoder ->
                languageCode?.let(decoder::setLanguage)
            }
        }
    }

    override fun startStreaming(
        onPartial: (String) -> Unit,
        onCatchingUp: (Boolean) -> Unit
    ) {
        check(worker == null) { "$backendName streaming has already started" }
        val decoder = decoderOrThrow()
        this.onPartial = onPartial
        this.onCatchingUp = onCatchingUp
        queuedSamples.set(0)
        catchingUp = false
        lastText = ""
        // ponytail: preserve all audio in memory; add disk spooling if sustained backlog becomes a real limit.
        audio = Channel(Channel.UNLIMITED)
        worker = scope.async {
            for (chunk in requireNotNull(audio)) {
                currentCoroutineContext().ensureActive()
                val text = decoder.acceptAudio(chunk).trim()
                queuedSamples.addAndGet(-chunk.size.toLong())
                updateCatchingUp(queuedSamples.get() > catchingUpSamples)
                if (!catchingUp && text.isNotBlank() && text != lastText) {
                    lastText = text
                    this@SherpaStreamingBackend.onPartial(text)
                }
            }
        }
    }

    override fun acceptAudio(samples: FloatArray) {
        queuedSamples.addAndGet(samples.size.toLong())
        updateCatchingUp(queuedSamples.get() > catchingUpSamples)
        requireNotNull(audio) { "$backendName streaming has not started" }
            .trySend(samples)
            .getOrThrow()
    }

    override suspend fun finishStreaming(): String {
        audio?.close()
        worker?.await()
        audio = null
        worker = null
        updateCatchingUp(false)
        return decoderOrThrow().finish().trim()
    }

    override suspend fun transcribe(samples: FloatArray): String {
        startStreaming({}, {})
        acceptAudio(samples)
        return finishStreaming()
    }

    override suspend fun close() {
        audio?.close()
        worker?.cancelAndJoin()
        worker = null
        audio = null
        decoder?.close()
        decoder = null
        scope.cancel()
    }

    private fun updateCatchingUp(value: Boolean) {
        val changed = synchronized(this) {
            if (catchingUp == value) false else {
                catchingUp = value
                true
            }
        }
        if (changed) onCatchingUp(value)
    }

    private fun decoderOrThrow() =
        decoder ?: throw IllegalStateException("$backendName backend is not loaded")
}

internal class SherpaOnlineDecoder(modelDirectory: File, featureDim: Int = 80) : SherpaStreamingDecoder {
    private val recognizer: OnlineRecognizer
    private val stream: OnlineStream

    init {
        fun model(name: String) = File(modelDirectory, name).absolutePath
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = featureDim, dither = 0.0f),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = model("encoder.int8.onnx"),
                    decoder = model("decoder.int8.onnx"),
                    joiner = model("joiner.int8.onnx")
                ),
                tokens = model("tokens.txt"),
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                provider = "cpu"
            ),
            enableEndpoint = false,
            decodingMethod = "greedy_search",
            maxActivePaths = 4
        )
        recognizer = OnlineRecognizer(assetManager = null, config = config)
        stream = recognizer.createStream()
    }

    override fun setLanguage(language: String) {
        stream.setOption("language", language)
    }

    override fun acceptAudio(samples: FloatArray): String {
        stream.acceptWaveform(samples, SAMPLE_RATE)
        decodeReady()
        return recognizer.getResult(stream).text
    }

    override fun finish(): String {
        stream.inputFinished()
        decodeReady()
        return recognizer.getResult(stream).text
    }

    override fun close() {
        stream.release()
        recognizer.release()
    }

    private fun decodeReady() {
        while (recognizer.isReady(stream)) recognizer.decode(stream)
    }
}
