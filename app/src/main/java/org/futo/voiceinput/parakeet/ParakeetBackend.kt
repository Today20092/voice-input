package org.futo.voiceinput.parakeet

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.backend.SpeechBackend
import org.futo.voiceinput.settings.PARAKEET_ENGINE_DIAGNOSTICS
import org.futo.voiceinput.settings.getSetting
import java.io.File
import java.util.concurrent.atomic.AtomicLong

private const val SAMPLE_RATE = 16_000

internal interface ParakeetDecoder {
    fun transcribe(samples: FloatArray): String
    fun close()
}

class ParakeetBackend internal constructor(
    private val decoderFactory: (Context) -> ParakeetDecoder = ::SherpaParakeetDecoder,
    private var decoder: ParakeetDecoder? = null
) : SpeechBackend {
    private companion object {
        const val DIAGNOSTICS_TAG = "ParakeetDiagnostics"
        val requestSequence = AtomicLong()
    }

    private val mutex = Mutex()

    @Volatile
    private var diagnosticsEnabled = false

    internal suspend fun configureDiagnostics(context: Context) {
        diagnosticsEnabled = context.getSetting(PARAKEET_ENGINE_DIAGNOSTICS)
    }

    internal fun logEngineAcquired(warm: Boolean, elapsedMs: Long) {
        if (diagnosticsEnabled) {
            Log.i(DIAGNOSTICS_TAG, "engine_acquired warm=$warm elapsedMs=$elapsedMs")
        }
    }

    override suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        configureDiagnostics(context)
        mutex.withLock {
            if (decoder == null) decoder = decoderFactory(context.applicationContext)
        }
    }

    override suspend fun transcribe(samples: FloatArray): String = withContext(Dispatchers.Default) {
        val requestId = requestSequence.incrementAndGet()
        val startedAt = System.nanoTime()
        if (diagnosticsEnabled) {
            Log.i(DIAGNOSTICS_TAG, "request=$requestId decode_start samples=${samples.size}")
        }
        try {
            mutex.withLock {
                decoderOrThrow().transcribe(samples).also { result ->
                    if (diagnosticsEnabled) {
                        val outcome = if (result.isBlank()) "no_speech" else "text"
                        Log.i(
                            DIAGNOSTICS_TAG,
                            "request=$requestId decode_complete elapsedMs=${(System.nanoTime() - startedAt) / 1_000_000} outputChars=${result.length} outcome=$outcome"
                        )
                    }
                }
            }
        } catch (error: Exception) {
            if (diagnosticsEnabled) {
                Log.e(DIAGNOSTICS_TAG, "request=$requestId decode_failed", error)
            }
            throw error
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            decoder?.close()
            decoder = null
        }
    }

    private fun decoderOrThrow() =
        decoder ?: throw IllegalStateException("Parakeet backend is not loaded")
}

private class SherpaParakeetDecoder(context: Context) : ParakeetDecoder {
    private val recognizer: OfflineRecognizer

    init {
        val assetManager = if (BuildConfig.BUNDLE_PARAKEET_MODEL) context.assets else null
        fun model(name: String) = if (assetManager != null) {
            "${ParakeetModel.directoryName}/$name"
        } else {
            File(context.parakeetModelDir(), name).absolutePath
        }
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80, dither = 0.0f),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = model("encoder.int8.onnx"),
                    decoder = model("decoder.int8.onnx"),
                    joiner = model("joiner.int8.onnx")
                ),
                tokens = model("tokens.txt"),
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                provider = "cpu",
                modelType = "nemo_transducer"
            ),
            decodingMethod = "greedy_search",
            maxActivePaths = 4
        )
        recognizer = OfflineRecognizer(assetManager = assetManager, config = config)
    }

    override fun transcribe(samples: FloatArray): String {
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    override fun close() = recognizer.release()
}
