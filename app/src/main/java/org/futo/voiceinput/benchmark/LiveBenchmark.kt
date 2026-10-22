package org.futo.voiceinput.benchmark

import android.content.Context
import kotlinx.serialization.Serializable
import org.futo.voiceinput.recognition.RecognitionModelSelection
import java.io.File

@Serializable
data class LiveBenchmarkReading(
    val id: String = java.util.UUID.randomUUID().toString(), val modelId: String,
    val entryPoint: String, var reference: String, var status: String = "armed",
    val clock: String = "System.nanoTime; first partial relative to recordingStarted",
    val milestones: MutableMap<String, Long> = mutableMapOf(),
    var raw: String? = null, var delivered: String? = null, var observed: String? = null,
    var deliveryVerified: Boolean? = null, var deliveryAccepted: Boolean? = null, var reason: String? = null,
    var accuracy: Accuracy? = null, var identity: Map<String, String> = emptyMap(),
    var configuration: Map<String, String> = emptyMap()
)

class LiveBenchmarkSession(val model: BenchmarkModel, val reading: LiveBenchmarkReading, internal val report: BenchmarkReport, private val store: BenchmarkStore) {
    val selection = RecognitionModelSelection(model.managed?.runtimeId ?: "whisper_ggml",
        moonshineVariantId = model.managed?.variantId,
        nemotronVariantId = model.managed?.variantId, asr4allVariantId = model.managed?.variantId)

    @Synchronized fun milestone(name: String) { reading.milestones.putIfAbsent(name, System.nanoTime()); persist() }
    @Synchronized fun speech() { reading.milestones["lastVadSpeech"] = System.nanoTime() }
    @Synchronized fun raw(text: String) { reading.raw = text; milestone("finalReady") }
    @Synchronized fun delivered(text: String, accepted: Boolean) {
        reading.deliveryAccepted = accepted
        reading.delivered = text; reading.status = if (accepted) "delivered" else "failed"
        milestone("deliveryReturned")
        verify()
    }
    @Synchronized fun observed(text: String) { reading.observed = text; milestone("controlledReceiverObserved"); verify() }
    @Synchronized fun reference(text: String) {
        require(text.length <= 6000)
        reading.reference = text
        reading.accuracy = reading.raw?.let { BenchmarkScoring.score(text, it) }
        persist()
    }
    @Synchronized fun failure(reason: String) { reading.status = "interrupted"; reading.reason = reason; persist() }
    @Synchronized fun configuration(values: Map<String, String>) { reading.configuration = values; persist() }
    private fun verify() {
        val delivered = reading.delivered
        val observed = reading.observed
        if (delivered != null && observed != null) {
            reading.deliveryVerified = reading.deliveryAccepted == true && delivered.trim() == observed.trim()
            reading.status = if (reading.deliveryVerified == true) "completed" else "failed"
            reading.reason = when {
                reading.deliveryAccepted != true -> "Final delivery was rejected"
                reading.deliveryVerified != true -> "Controlled receiver differs from delivered text; review loss or duplication"
                else -> null
            }
            reading.accuracy = reading.raw?.let { BenchmarkScoring.score(reading.reference, it) }
            persist()
        }
    }
    private fun persist() { store.save(report) }
}

/** Only the private controlled caller/editor can bind an armed live session. */
object LiveBenchmark {
    @Volatile var active: LiveBenchmarkSession? = null
        private set
    suspend fun arm(context: Context, report: BenchmarkReport, model: BenchmarkModel, entryPoint: String, reference: String): LiveBenchmarkSession =
        org.futo.voiceinput.recognition.RecognitionModelLifecycle.create(context.filesDir, org.futo.voiceinput.BuildConfig.BUNDLE_PARAKEET_MODEL)
            .acquireBenchmark().use {
                synchronized(this) {
                    check(active == null) { "Finish the current live reading first" }
                    check(model.installed(context)) { "Download this model first" }
                    val reading = LiveBenchmarkReading(modelId = model.id, entryPoint = entryPoint, reference = reference,
                        identity = model.identity(context))
                    report.liveReadings.add(reading)
                    val session = LiveBenchmarkSession(model, reading, report, BenchmarkStore(File(context.filesDir, "model-benchmarks")))
                    active = session
                    session.milestone("armed")
                    session
                }
            }
    fun forEntry(entry: String, controlled: Boolean): LiveBenchmarkSession? = active?.takeIf { controlled && it.reading.entryPoint == entry }
    @Synchronized fun end() { active = null }
}
