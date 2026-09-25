package org.futo.voiceinput.cohere

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineCohereTranscribeModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.futo.voiceinput.backend.SpeechBackend
import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.futo.voiceinput.recognition.RecognitionModelStore
import org.futo.voiceinput.settings.COHERE_LANGUAGE
import org.futo.voiceinput.settings.getSetting
import java.io.File

private const val SAMPLE_RATE = 16_000
private const val MAX_CHUNK_SAMPLES = 35 * SAMPLE_RATE

internal interface CohereDecoder {
    fun transcribe(samples: FloatArray): String
    fun close()
}

class CohereBackend internal constructor(
    private val decoderFactory: (File, String) -> CohereDecoder = { directory, language ->
        SherpaCohereDecoder(directory, language)
    }
) : SpeechBackend {
    private val mutex = Mutex()
    private var decoder: CohereDecoder? = null

    // Cohere does not detect language. Report the explicit language used for this run
    // so English-only transcript cleanup also behaves correctly for saved recordings.
    override var detectedLanguage: String? = null
        private set

    override suspend fun load(context: Context) {
        val store = RecognitionModelStore(context.filesDir)
        load(store.modelDirectory(RecognitionModelCatalog.cohereTranscribe),
            context.getSetting(COHERE_LANGUAGE))
    }

    internal suspend fun load(directory: File, language: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val languageCode = language.toCohereLanguage().id
            if (decoder != null && detectedLanguage != languageCode) {
                decoder?.close()
                decoder = null
                detectedLanguage = null
            }
            if (decoder == null) decoder = decoderFactory(directory, languageCode)
            detectedLanguage = languageCode
        }
    }

    override suspend fun transcribe(samples: FloatArray): String = withContext(Dispatchers.Default) {
        mutex.withLock {
            val activeDecoder = checkNotNull(decoder) { "Cohere Transcribe is not loaded" }
            val text = StringBuilder()
            var start = 0
            while (start < samples.size) {
                currentCoroutineContext().ensureActive()
                val end = cohereChunkEnd(samples, start)
                val chunk = if (start == 0 && end == samples.size) samples else samples.copyOfRange(start, end)
                val result = activeDecoder.transcribe(chunk).trim()
                if (result.isNotEmpty()) {
                    if (text.isNotEmpty()) text.append(' ')
                    text.append(result)
                }
                start = end
            }
            text.toString()
        }
    }

    override suspend fun close(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val previous = decoder
            decoder = null
            detectedLanguage = null
            previous?.close()
        }
    }
}

// Match the reference processor's bounded input length, preferring a quiet 100 ms
// window in the last five seconds. Adjacent chunks preserve every sample exactly once.
internal fun cohereChunkEnd(samples: FloatArray, start: Int): Int {
    if (samples.size - start <= MAX_CHUNK_SAMPLES) return samples.size
    val limit = start + MAX_CHUNK_SAMPLES
    val window = SAMPLE_RATE / 10
    var quietest = Double.POSITIVE_INFINITY
    var split = limit
    for (offset in limit - 5 * SAMPLE_RATE until limit step window) {
        var energy = 0.0
        for (index in offset until offset + window) {
            energy += samples[index].toDouble() * samples[index]
        }
        if (energy < quietest) {
            quietest = energy
            split = offset + window / 2
        }
    }
    return split
}

private class SherpaCohereDecoder(directory: File, language: String) : CohereDecoder {
    private val recognizer = OfflineRecognizer(
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 128, dither = 0.0f),
            modelConfig = OfflineModelConfig(
                cohereTranscribe = OfflineCohereTranscribeModelConfig(
                    encoder = File(directory, "encoder.int8.onnx").absolutePath,
                    decoder = File(directory, "decoder.int8.onnx").absolutePath,
                    language = language,
                    usePunct = true,
                    useItn = true
                ),
                tokens = File(directory, "tokens.txt").absolutePath,
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                provider = "cpu"
            )
        )
    )

    override fun transcribe(samples: FloatArray): String {
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text
        } finally {
            stream.release()
        }
    }

    override fun close() = recognizer.release()
}
