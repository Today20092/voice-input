package org.futo.voiceinput.benchmark

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class BenchmarkStore(val root: File) {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    fun directory(id: String): File {
        require(id.matches(Regex("[0-9a-f-]{36}"))) { "Invalid benchmark run ID" }
        return File(root, id).also { check(it.isDirectory || it.mkdirs()) }
    }

    fun save(report: BenchmarkReport) = synchronized(writeLock) {
        atomic(File(directory(report.runId), "report.json"), json.encodeToString(report).toByteArray())
    }

    fun load(id: String): BenchmarkReport = json.decodeFromString(File(directory(id), "report.json").readText())

    fun reports(): List<BenchmarkReport> = root.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull {
        runCatching { load(it.name) }.getOrNull()
    }.sortedByDescending { it.createdAtMs }

    fun recoverInterrupted(): List<BenchmarkReport> = reports().onEach { report ->
        var changed = false
        if (report.status in setOf("running", "downloading")) {
            report.status = "interrupted"
            report.cases.filter { it.status == "running" }.forEach {
                it.status = "interrupted"; it.reason = "Process ended before completion; observations retained"
            }
            changed = true
        }
        report.liveReadings.filter { it.status in setOf("armed", "delivered") }.forEach {
            it.status = "interrupted"; it.reason = "Process ended before verified live delivery"; changed = true
        }
        if (changed) save(report)
    }

    fun saveAudio(runId: String, clipId: String, samples: FloatArray): String {
        require(samples.size in 1..16000 * BENCHMARK_MAX_SECONDS && samples.all { it.isFinite() })
        val bytes = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { bytes.putFloat(it) }
        atomic(audioFile(runId, clipId), bytes.array())
        return hash(bytes.array())
    }

    fun audioFile(runId: String, clipId: String): File {
        require(clipId.matches(Regex("[a-zA-Z0-9_-]{1,80}")))
        return File(directory(runId), "$clipId.f32")
    }

    fun readAudio(runId: String, clip: BenchmarkClip): FloatArray {
        val file = audioFile(runId, clip.id)
        require(file.length() == clip.sampleCount * 4L && clip.sampleCount in 1..16000 * BENCHMARK_MAX_SECONDS)
        val bytes = file.readBytes()
        check(hash(bytes) == clip.canonicalSha256) { "Canonical audio changed; import it again" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(clip.sampleCount) { buffer.float }
    }

    fun delete(id: String) { check(directory(id).deleteRecursively()) { "Cannot delete benchmark" } }
    fun json(report: BenchmarkReport): String = json.encodeToString(report)

    fun markdown(report: BenchmarkReport): String = buildString {
        appendLine("# Personal device benchmark")
        appendLine(report.contentNotice)
        appendLine("Run ${report.runId}, schema ${report.schemaVersion}, status ${report.status}.")
        appendLine("Isolation: ${report.isolation}. ${report.cacheNotice}")
        appendLine(report.cleanup)
        appendLine("Scoring: ${report.normalization}. WER/CER = edits / reference length; WER can exceed 100%. Numbers remain literal. Missing reference has no score.")
        appendLine("One first decode, one untimed warm-up per clip, five measured warm decodes. RTF = decode seconds / audio seconds. Release time measures the API call, not memory reclamation.")
        report.device.forEach { (key, value) -> appendLine("- $key: $value") }
        report.clips.forEach { clip ->
            appendLine("\n## Clip ${clip.id}")
            appendLine("Duration ${clip.durationSeconds}s, samples ${clip.sampleCount}, language ${clip.language}.")
            appendLine("Original SHA-256 ${clip.originalSha256}; canonical ${clip.canonicalSha256}; reference ${clip.referenceSha256}.")
            appendLine("Conversion: ${clip.conversion}")
            appendLine("Spoken reference verified: ${clip.referenceVerified}")
            appendLine("Reference:")
            appendLine(quoted(clip.reference ?: "Unavailable"))
        }
        report.cases.forEachIndexed { order, result ->
            appendLine("\n## ${order + 1}. ${result.modelId}")
            appendLine("Status ${result.status}. ${result.reason.orEmpty()}")
            appendLine("Initialization ${milliseconds(result.initializationNs)} ms; release call ${milliseconds(result.releaseCallNs)} ms.")
            appendLine("Sampled peak process PSS ${result.sampledPeakPssKb ?: "unavailable"} KiB, interval ${result.memorySampleIntervalMs} ms.")
            result.identity.forEach { (k, v) -> appendLine("- $k: $v") }
            appendLine("Before: ${result.before}; after: ${result.after}")
            report.clips.forEach { clip ->
                val warm = result.observations.filter { it.clipId == clip.id && it.phase == "warm" }.map { it.elapsedNs }.sorted()
                if (warm.isNotEmpty()) appendLine("${clip.id} warm min/median/max: ${milliseconds(warm.first())}/${milliseconds(warm.medianNs())}/${milliseconds(warm.last())} ms; ${warm.size}/5 completed.")
            }
            result.observations.forEach { observation ->
                val clip = report.clips.first { it.id == observation.clipId }
                appendLine("\n${observation.clipId} ${observation.phase} ${observation.repetition}: ${milliseconds(observation.elapsedNs)} ms, RTF ${observation.elapsedNs / 1e9 / clip.durationSeconds}.")
                if (observation.phase == "first") appendLine("Startup inclusive: ${milliseconds(result.initializationNs?.plus(observation.elapsedNs))} ms.")
                appendLine("WER: ${observation.accuracy.words?.let { "${it.rate * 100}% $it" } ?: observation.accuracy.unavailable}; CER: ${observation.accuracy.characters?.let { "${it.rate * 100}% $it" } ?: "unavailable"}.")
                appendLine(quoted(observation.transcript))
            }
        }
        report.liveReadings.forEach { reading ->
            appendLine("\n## Live ${reading.entryPoint}: ${reading.modelId}, ${reading.id}")
            appendLine("Status ${reading.status}; delivery verified ${reading.deliveryVerified ?: "unavailable"}. ${reading.reason.orEmpty()}")
            appendLine("Clock: ${reading.clock}; settings: ${reading.configuration}")
            appendLine("Speech end is the last VAD-positive frame observed by the capture loop, not an independently measured acoustic endpoint.")
            fun latency(start: String, end: String): String = reading.milestones[start]?.let { first ->
                reading.milestones[end]?.let { last -> milliseconds(last - first) }
            } ?: "unavailable"
            appendLine("Speech-end-to-final ${latency("lastVadSpeech", "finalReady")} ms; Stop-to-final ${latency("stopPressed", "finalReady")} ms; final-to-delivery-return ${latency("finalReady", "deliveryReturned")} ms.")
            appendLine("First partial from capture start ${latency("recordingStarted", "firstPartial")} ms. Final-only models do not emit partials.")
            appendLine("Milestones ${reading.milestones}; identity ${reading.identity}")
            appendLine("Reference:"); appendLine(quoted(reading.reference))
            appendLine("Raw:"); appendLine(quoted(reading.raw ?: "Not produced"))
            appendLine("Delivered:"); appendLine(quoted(reading.delivered ?: "Not produced"))
            appendLine("Observed by controlled receiver:"); appendLine(quoted(reading.observed ?: "Not observed"))
            appendLine("Accuracy for this individual reading: ${reading.accuracy ?: "unavailable; review spoken reference"}")
        }
        appendLine("\nThese results describe this speaker, recordings, device and conditions. Live readings differ and cannot provide an identical-input ranking.")
    }

    private fun quoted(text: String): String {
        val fence = "`".repeat(maxOf(3, (Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0) + 1))
        return "$fence\n$text\n$fence"
    }
    private fun milliseconds(ns: Long?) = ns?.let { "%.3f".format(java.util.Locale.ROOT, it / 1e6) } ?: "unavailable"
    private fun atomic(file: File, bytes: ByteArray) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        try {
            FileOutputStream(temporary).use { output -> output.write(bytes); output.fd.sync() }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (failure: Throwable) {
            temporary.delete(); throw failure
        }
    }

    companion object {
        private val writeLock = Any()
        fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        fun hashFile(file: File): String = file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
