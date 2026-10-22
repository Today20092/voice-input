package org.futo.voiceinput.benchmark

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class BenchmarkClip(
    val id: String, val originalSha256: String, val canonicalSha256: String,
    val sampleCount: Int, val reference: String?, val referenceSha256: String,
    val language: String = "en", val conversion: String = "16 kHz mono float32 LE", val referenceVerified: Boolean = true
) {
    val durationSeconds: Double get() = sampleCount / 16000.0
}

@Serializable
data class DecodeObservation(
    val clipId: String, val phase: String, val repetition: Int,
    val elapsedNs: Long, val transcript: String, val accuracy: Accuracy
)

@Serializable
data class BenchmarkCaseResult(
    val modelId: String, var status: String = "running", var reason: String? = null,
    var initializationNs: Long? = null, var releaseCallNs: Long? = null,
    val observations: MutableList<DecodeObservation> = mutableListOf(),
    var before: Map<String, String> = emptyMap(), var after: Map<String, String> = emptyMap(),
    var sampledPeakPssKb: Long? = null, val memorySampleIntervalMs: Long = 250,
    var identity: Map<String, String> = emptyMap(), var executionOrder: Int? = null
)

@Serializable
data class BenchmarkReport(
    val schemaVersion: Int = 1, val runId: String = UUID.randomUUID().toString(),
    val createdAtMs: Long = System.currentTimeMillis(),
    var status: String = "prepared", val isolation: String = "model-unloaded",
    val normalization: String = BenchmarkScoring.POLICY,
    val contentNotice: String = "Contains reference text and recognized transcripts. Audio is excluded.",
    val cleanup: String = "Configurable transcript cleanup and personal vocabulary disabled; ASR4ALL built-in cleanup is inseparable.",
    val cacheNotice: String = "Filesystem caches may be warm. Memory peaks are sampled process PSS, not model allocations.",
    val device: Map<String, String> = emptyMap(), val clips: List<BenchmarkClip>,
    val selectedModelIds: List<String>, val installedBefore: List<String>,
    val cases: MutableList<BenchmarkCaseResult> = mutableListOf(),
    val liveReadings: MutableList<LiveBenchmarkReading> = mutableListOf(),
    val warmRepetitions: Int = 5
)

interface BenchmarkRecognizer {
    suspend fun load()
    suspend fun transcribe(samples: FloatArray): String
    suspend fun close()
}

internal fun List<Long>.medianNs(): Long {
    require(isNotEmpty())
    val sorted = sorted()
    val lower = sorted[(size - 1) / 2]
    return lower + (sorted[size / 2] - lower) / 2
}

fun BenchmarkReport.withVerifiedSpokenReferences(): BenchmarkReport {
    require(normalization == BenchmarkScoring.POLICY)
    require(clips.isNotEmpty() && clips.all { !it.reference.isNullOrBlank() }) { "Every recording needs a reviewed spoken reference" }
    val reviewed = clips.map { it.copy(referenceVerified = true) }
    return copy(clips = reviewed, cases = cases.map { result ->
        result.copy(observations = result.observations.map { observation ->
            val clip = reviewed.first { it.id == observation.clipId }
            observation.copy(accuracy = BenchmarkScoring.score(clip.reference, observation.transcript))
        }.toMutableList())
    }.toMutableList())
}

/** UI and instrumentation share all timing boundaries. Conversion, scoring and persistence are outside decode time. */
class BenchmarkRunner(
    private val clock: () -> Long = System::nanoTime,
    private val environment: () -> Map<String, String> = { emptyMap() },
    private val warmRepetitions: Int = 5
) {
    suspend fun runCase(
        result: BenchmarkCaseResult, clips: List<Pair<BenchmarkClip, FloatArray>>,
        factory: () -> BenchmarkRecognizer, persist: () -> Unit
    ) {
        require(clips.isNotEmpty())
        require(warmRepetitions in 1..5)
        clips.forEach { (clip, samples) -> require(samples.size == clip.sampleCount && samples.isNotEmpty()) }
        var recognizer: BenchmarkRecognizer? = null
        result.before = environment()
        persist()
        try {
            currentCoroutineContext().ensureActive()
            val start = clock()
            recognizer = factory()
            recognizer.load()
            result.initializationNs = clock() - start
            persist()
            suspend fun decode(clip: BenchmarkClip, audio: FloatArray, phase: String, repetition: Int) {
                currentCoroutineContext().ensureActive()
                val input = audio.copyOf()
                val started = clock()
                val text = recognizer.transcribe(input)
                val duration = clock() - started
                result.observations.add(DecodeObservation(clip.id, phase, repetition, duration,
                    text, if (clip.referenceVerified) BenchmarkScoring.score(clip.reference, text)
                    else Accuracy(unavailable = "Spoken reference has not been verified")))
                persist()
            }
            clips.first().let { (clip, audio) -> decode(clip, audio, "first", 0) }
            clips.forEach { (clip, audio) ->
                currentCoroutineContext().ensureActive()
                recognizer.transcribe(audio.copyOf())
                repeat(warmRepetitions) { decode(clip, audio, "warm", it + 1) }
            }
            result.status = "completed"
        } catch (cancelled: CancellationException) {
            result.status = "cancelled"
            result.reason = "Cancelled or interrupted; completed observations retained"
            throw cancelled
        } catch (_: OutOfMemoryError) {
            result.status = "failed"
            result.reason = "Insufficient memory; try a smaller model"
        } catch (failure: Exception) {
            result.status = "failed"
            result.reason = "Recognition failed (${failure.javaClass.simpleName}); installation retained"
        } finally {
            withContext(NonCancellable) {
                val start = clock()
                try { recognizer?.close() } catch (_: Exception) {
                    result.status = "failed"
                    result.reason = "Recognizer release failed"
                } finally {
                    result.releaseCallNs = clock() - start
                    result.after = environment()
                    persist()
                }
            }
        }
    }
}
