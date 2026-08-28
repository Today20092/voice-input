package org.futo.voiceinput.s1

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
enum class S1MiniTranscriptStageStatus {
    @SerialName("produced")
    Produced,

    @SerialName("not_produced")
    NotProduced
}

@Serializable
data class S1MiniTranscriptStage(
    val status: S1MiniTranscriptStageStatus,
    val text: String? = null,
    val originalCharacters: Int = 0,
    val truncated: Boolean = false
) {
    companion object {
        private const val MAX_CHARACTERS = 100_000

        fun produced(text: String): S1MiniTranscriptStage = S1MiniTranscriptStage(
            status = S1MiniTranscriptStageStatus.Produced,
            text = text.take(MAX_CHARACTERS),
            originalCharacters = text.length,
            truncated = text.length > MAX_CHARACTERS
        )

        fun notProduced(): S1MiniTranscriptStage =
            S1MiniTranscriptStage(status = S1MiniTranscriptStageStatus.NotProduced)
    }
}

@Serializable
data class S1MiniTranscriptCapture(
    val schemaVersion: Int = 1,
    val reportId: String,
    val capturedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val raw: S1MiniTranscriptStage,
    val cleaned: S1MiniTranscriptStage,
    val finalDelivered: S1MiniTranscriptStage,
    val failureOrBypassReason: String? = null
) {
    companion object {
        internal const val RETENTION_MS = 7L * 24L * 60L * 60L * 1_000L

        fun create(
            reportId: String,
            capturedAtEpochMs: Long,
            rawTranscript: String,
            cleanedTranscript: String?,
            finalDeliveredTranscript: String,
            failureOrBypassReason: String?
        ) = S1MiniTranscriptCapture(
            reportId = reportId,
            capturedAtEpochMs = capturedAtEpochMs,
            expiresAtEpochMs = capturedAtEpochMs + RETENTION_MS,
            raw = S1MiniTranscriptStage.produced(rawTranscript),
            cleaned = cleanedTranscript?.let(S1MiniTranscriptStage::produced)
                ?: S1MiniTranscriptStage.notProduced(),
            finalDelivered = S1MiniTranscriptStage.produced(finalDeliveredTranscript),
            failureOrBypassReason = failureOrBypassReason
        )
    }
}

data class S1MiniTranscriptCaptureLoadResult(
    val captures: List<S1MiniTranscriptCapture>,
    val unreadableCount: Int
) {
    val hasExportableEvidence: Boolean
        get() = captures.isNotEmpty() || unreadableCount > 0
}

object S1MiniTranscriptCaptureStore {
    private const val MAX_CAPTURES = 10
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Synchronized
    fun save(directory: File, capture: S1MiniTranscriptCapture, nowEpochMs: Long) {
        directory.mkdirs()
        val safeId = capture.reportId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(directory, "${capture.capturedAtEpochMs}-$safeId.json")
        val temporary = File(directory, "${target.name}.tmp")
        temporary.writeText(json.encodeToString(capture))
        if (!temporary.renameTo(target)) {
            temporary.delete()
            error("Unable to save S1-mini transcript capture")
        }
        purge(directory, nowEpochMs)
    }

    @Synchronized
    fun load(directory: File, nowEpochMs: Long): S1MiniTranscriptCaptureLoadResult {
        purge(directory, nowEpochMs)
        var unreadable = 0
        val captures = captureFiles(directory).mapNotNull { file ->
            runCatching { json.decodeFromString<S1MiniTranscriptCapture>(file.readText()) }
                .getOrElse {
                    unreadable += 1
                    null
                }
        }.sortedByDescending { it.capturedAtEpochMs }
        return S1MiniTranscriptCaptureLoadResult(captures, unreadable)
    }

    @Synchronized
    fun delete(directory: File, reportId: String, nowEpochMs: Long): Boolean {
        purge(directory, nowEpochMs)
        val file = captureFiles(directory).firstOrNull { candidate ->
            runCatching {
                json.decodeFromString<S1MiniTranscriptCapture>(candidate.readText()).reportId == reportId
            }.getOrDefault(false)
        } ?: return false
        return file.delete()
    }

    @Synchronized
    fun clear(directory: File) {
        directory.deleteRecursively()
    }

    private fun purge(directory: File, nowEpochMs: Long) {
        if (!directory.isDirectory) return
        val valid = mutableListOf<Pair<File, S1MiniTranscriptCapture>>()
        captureFiles(directory).forEach { file ->
            val capture = runCatching {
                json.decodeFromString<S1MiniTranscriptCapture>(file.readText())
            }.getOrNull()
            when {
                capture == null && file.lastModified() <= nowEpochMs - S1MiniTranscriptCapture.RETENTION_MS ->
                    file.delete()
                capture != null && capture.expiresAtEpochMs <= nowEpochMs -> file.delete()
                capture != null -> valid += file to capture
            }
        }
        valid.sortedByDescending { it.second.capturedAtEpochMs }
            .drop(MAX_CAPTURES)
            .forEach { (file, _) -> file.delete() }
    }

    private fun captureFiles(directory: File): List<File> =
        directory.listFiles { file -> file.isFile && file.extension == "json" }.orEmpty().toList()
}
