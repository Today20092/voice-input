package org.futo.voiceinput.diagnostics

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
enum class DiagnosticEvent {
    APP_STARTED, SESSION_STARTED, SESSION_CANCELLED, SESSION_RESET, SESSION_FAILED,
    MODEL_REQUIRED, MODEL_LOAD_STARTED, MODEL_LOAD_FINISHED, MODEL_LOAD_FAILED,
    PERMISSION_REQUIRED, PERMISSION_REJECTED, RECORDER_STARTED, RECORDER_RETRY,
    RECORDER_FAILED, RECORDER_READ_FAILED, FIRST_AUDIO, RECORDING_STOPPED,
    FIRST_PARTIAL, PARTIAL, CATCHING_UP, RECOGNITION_STARTED, RECOGNITION_FINISHED,
    CLEANUP_STARTED, CLEANUP_FINISHED, HARPER_FINISHED, RESULT_READY, DELIVERY_ACCEPTED, DELIVERY_REJECTED,
    DOWNLOAD_STARTED, DOWNLOAD_FAILED, DOWNLOAD_FINISHED, DOWNLOAD_CANCELLED,
    RESOURCE_SAMPLE, MANAGED_CRASH, PROCESS_EXIT, RETRANSCRIPTION_FINISHED, WAVEFORM_FIRST_FRAME
}

@Serializable
enum class DiagnosticMetric {
    ELAPSED_MS, DURATION_MS, AUDIO_MS, CHARACTERS, ATTEMPT, ERROR_CODE, HARPER_OUTCOME,
    STOP_REASON, APPLIED, CATCHING_UP, BYTES, FILE_COUNT, PSS_KB, JAVA_BYTES,
    NATIVE_BYTES, AVAILABLE_BYTES, LOW_MEMORY, THERMAL_STATUS, PROCESS_CPU_MS,
    EXIT_REASON, EXIT_STATUS, EXIT_TIMESTAMP, RSS_KB, CLEANUP_PROCESS, RETRANSCRIPTION
}

@Serializable
data class DiagnosticFailure(val kind: String, val frames: List<String>) {
    companion object {
        // Never call Throwable.toString(), message, cause.toString(), or stackTraceToString().
        fun from(error: Throwable): DiagnosticFailure {
            val kind = when (error) {
                is OutOfMemoryError -> "out_of_memory"
                is SecurityException -> "permission"
                is java.io.IOException -> "io"
                is java.util.concurrent.CancellationException -> "cancelled"
                is IllegalStateException -> "illegal_state"
                is IllegalArgumentException -> "invalid_argument"
                else -> "runtime"
            }
            val symbol = Regex("[A-Za-z0-9_.$<>]+")
            val prefixes = listOf("org.futo.", "android.", "java.", "kotlin.", "kotlinx.", "com.k2fsa.")
            val frames = error.stackTrace.take(24).mapNotNull {
                if (prefixes.none(it.className::startsWith) || !symbol.matches(it.className) ||
                    !symbol.matches(it.methodName)) null
                else "${it.className}.${it.methodName}:${it.lineNumber}"
            }
            return DiagnosticFailure(kind, frames)
        }
    }
}

@Serializable
data class DiagnosticRecord(
    val schemaVersion: Int = 1,
    val timestampMs: Long,
    val event: DiagnosticEvent,
    val sessionId: String? = null,
    val model: String? = null,
    val metrics: Map<DiagnosticMetric, Long> = emptyMap(),
    val failure: DiagnosticFailure? = null
)

@Serializable
data class DiagnosticPolicy(
    val enabled: Boolean = true,
    val detailedUntilMs: Long = 0L,
    val collectSinceMs: Long = 0L
)

data class DiagnosticSnapshot(val records: List<DiagnosticRecord>, val unreadableRecords: Int)

/** Single-process store. Callers serialize IO off the recording/UI threads. */
class DiagnosticStore(
    private val directory: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxBytes: Long = MAX_BYTES,
    private val retentionMs: Long = RETENTION_MS,
    private val knownModels: Set<String> = emptySet()
) {
    companion object {
        const val MAX_BYTES = 10L * 1024 * 1024
        const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
        const val DETAILED_MS = 30L * 60 * 1000
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    }

    private val policyFile get() = File(directory, "policy.json")
    @Volatile private var policy = runCatching {
        if (policyFile.isFile) json.decodeFromString<DiagnosticPolicy>(policyFile.readText())
        else DiagnosticPolicy(collectSinceMs = now())
    }.getOrElse { DiagnosticPolicy(enabled = false, collectSinceMs = now()) }

    init {
        directory.mkdirs()
        savePolicy(policy)
    }

    fun policy(): DiagnosticPolicy = policy

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        savePolicy(policy.copy(enabled = enabled, detailedUntilMs = 0,
            collectSinceMs = if (enabled && !policy.enabled) now() else policy.collectSinceMs))
    }

    @Synchronized
    fun startDetailed() {
        if (policy.enabled) savePolicy(policy.copy(detailedUntilMs = now() + DETAILED_MS))
    }

    @Synchronized
    fun stopDetailed() = savePolicy(policy.copy(detailedUntilMs = 0))

    fun detailedRemainingMs(): Long = if (policy.enabled) {
        (policy.detailedUntilMs - now()).takeIf { it in 1..DETAILED_MS } ?: 0L
    } else 0L

    @Synchronized
    fun record(
        event: DiagnosticEvent,
        sessionId: String? = null,
        model: String? = null,
        metrics: Map<DiagnosticMetric, Long> = emptyMap(),
        error: Throwable? = null,
        detailed: Boolean = false
    ) {
        if (!policy.enabled || (detailed && detailedRemainingMs() == 0L)) return
        val safeSession = sessionId?.takeIf {
            it.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
        }
        val record = DiagnosticRecord(timestampMs = now(), event = event, sessionId = safeSession,
            model = model?.takeIf(knownModels::contains), metrics = metrics,
            failure = error?.let(DiagnosticFailure::from))
        val line = json.encodeToString(record) + "\n"
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (bytes.size > 32 * 1024 || bytes.size > maxBytes) return
        val files = prune().toMutableList()
        var target = files.lastOrNull()?.takeIf {
            it.length() + bytes.size <= minOf(64 * 1024L, maxBytes) &&
                now() - segmentTime(it) < 60 * 60 * 1000L
        }
        if (target == null) {
            target = File(directory, "${now()}-${UUID.randomUUID()}.jsonl")
            files.add(target)
        }
        var total = files.sumOf { it.length() }
        for (old in files) {
            if (total + bytes.size <= maxBytes) break
            total -= old.length()
            old.delete()
        }
        target.appendBytes(bytes)
    }

    @Synchronized
    fun snapshot(): DiagnosticSnapshot {
        var unreadable = 0
        val records = prune().flatMap { file ->
            file.useLines { lines -> lines.mapNotNull { line ->
                runCatching { json.decodeFromString<DiagnosticRecord>(line) }.getOrElse {
                    unreadable++
                    null
                }?.takeIf { it.timestampMs in (now() - retentionMs)..now() }
            }.toList() }
        }
        return DiagnosticSnapshot(records, unreadable)
    }

    @Synchronized
    fun clear() {
        segments().forEach { check(it.delete()) { "Unable to clear diagnostics" } }
        savePolicy(policy.copy(detailedUntilMs = 0, collectSinceMs = now()))
    }

    private fun segments() = directory.listFiles().orEmpty()
        .filter { it.isFile && it.extension == "jsonl" }.sortedBy(::segmentTime)

    private fun segmentTime(file: File) = file.name.substringBefore('-').toLongOrNull() ?: 0L

    private fun prune(): List<File> {
        val retained = segments().filter {
            val valid = segmentTime(it) in (now() - retentionMs)..now()
            if (!valid) it.delete()
            valid
        }.toMutableList()
        var total = retained.sumOf { it.length() }
        while (total > maxBytes && retained.isNotEmpty()) {
            val oldest = retained.removeAt(0)
            total -= oldest.length()
            oldest.delete()
        }
        return retained
    }

    private fun savePolicy(next: DiagnosticPolicy) {
        directory.mkdirs()
        val temporary = File(directory, "policy.tmp")
        temporary.writeText(json.encodeToString(next))
        check(temporary.renameTo(policyFile)) { "Unable to save diagnostic preferences" }
        policy = next
    }
}
