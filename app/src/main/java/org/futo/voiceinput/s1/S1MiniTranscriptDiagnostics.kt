package org.futo.voiceinput.s1

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
enum class S1MiniTranscriptDelivery { Unknown, Prepared, Accepted, Rejected, Canceled }

@Serializable
data class S1MiniTranscriptCapture(
    val schemaVersion: Int = 1,
    val reportId: String,
    val capturedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val raw: S1MiniTranscriptStage,
    val cleaned: S1MiniTranscriptStage,
    val finalDelivered: S1MiniTranscriptStage,
    val failureOrBypassReason: String? = null,
    val prepared: S1MiniTranscriptStage = S1MiniTranscriptStage.notProduced(),
    val delivery: S1MiniTranscriptDelivery = S1MiniTranscriptDelivery.Unknown
) {
    fun withDelivery(outcome: S1MiniTranscriptDelivery) = copy(
        delivery = outcome,
        finalDelivered = if (outcome == S1MiniTranscriptDelivery.Accepted) prepared
            else S1MiniTranscriptStage.notProduced()
    )

    companion object {
        internal const val RETENTION_MS = 7L * 24L * 60L * 60L * 1_000L

        fun create(
            reportId: String,
            capturedAtEpochMs: Long,
            rawTranscript: String,
            cleanedTranscript: String?,
            finalDeliveredTranscript: String,
            failureOrBypassReason: String?,
            delivery: S1MiniTranscriptDelivery = S1MiniTranscriptDelivery.Accepted
        ) = S1MiniTranscriptCapture(
            schemaVersion = 2,
            reportId = reportId,
            capturedAtEpochMs = capturedAtEpochMs,
            expiresAtEpochMs = capturedAtEpochMs + RETENTION_MS,
            raw = S1MiniTranscriptStage.produced(rawTranscript),
            cleaned = cleanedTranscript?.let(S1MiniTranscriptStage::produced)
                ?: S1MiniTranscriptStage.notProduced(),
            finalDelivered = if (delivery == S1MiniTranscriptDelivery.Accepted)
                S1MiniTranscriptStage.produced(finalDeliveredTranscript) else S1MiniTranscriptStage.notProduced(),
            failureOrBypassReason = failureOrBypassReason,
            prepared = S1MiniTranscriptStage.produced(finalDeliveredTranscript),
            delivery = delivery
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
    private const val MAX_CAPTURE_BYTES = 2L * 1024 * 1024
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Synchronized
    fun save(directory: File, capture: S1MiniTranscriptCapture, nowEpochMs: Long) {
        directory.mkdirs()
        retained(directory, nowEpochMs)
        val safeId = capture.reportId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(directory, "${capture.capturedAtEpochMs}-$safeId.json")
        val temporary = File(directory, "${target.name}.tmp")
        try {
            temporary.writeText(json.encodeToString(capture))
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            if (temporary.exists()) deleteFile(temporary)
        }
        retained(directory, nowEpochMs)
    }

    @Synchronized
    fun load(directory: File, nowEpochMs: Long): S1MiniTranscriptCaptureLoadResult {
        val retained = retained(directory, nowEpochMs)
        return S1MiniTranscriptCaptureLoadResult(
            captures = retained.mapNotNull { it.second }.sortedByDescending { it.capturedAtEpochMs },
            unreadableCount = retained.count { it.second == null }
        )
    }

    @Synchronized
    fun delete(directory: File, reportId: String, nowEpochMs: Long): Boolean {
        val file = retained(directory, nowEpochMs).firstOrNull { it.second?.reportId == reportId }
            ?.first ?: return false
        deleteFile(file)
        return true
    }

    @Synchronized
    fun clear(directory: File) {
        if (!directory.exists()) return
        if (!directory.isDirectory || !directory.deleteRecursively()) {
            throw IOException("Unable to clear transcript captures")
        }
    }

    private fun retained(directory: File, nowEpochMs: Long): List<Pair<File, S1MiniTranscriptCapture?>> {
        val retained = mutableListOf<Pair<File, S1MiniTranscriptCapture?>>()
        captureFiles(directory).forEach { file ->
            if (file.extension == "tmp" || file.length() > MAX_CAPTURE_BYTES) {
                deleteFile(file)
                return@forEach
            }
            val capture = runCatching {
                json.decodeFromString<S1MiniTranscriptCapture>(file.readText())
            }.getOrNull()
            when {
                capture == null && file.lastModified() <= nowEpochMs - S1MiniTranscriptCapture.RETENTION_MS ->
                    deleteFile(file)
                capture != null && minOf(capture.expiresAtEpochMs,
                    capture.capturedAtEpochMs + S1MiniTranscriptCapture.RETENTION_MS) <= nowEpochMs -> deleteFile(file)
                else -> retained += file to capture
            }
        }
        val excess = retained.sortedByDescending { it.second?.capturedAtEpochMs ?: it.first.lastModified() }
            .drop(MAX_CAPTURES)
        excess.forEach { (file, _) -> deleteFile(file) }
        return retained - excess.toSet()
    }

    private fun captureFiles(directory: File): List<File> {
        if (!directory.exists()) return emptyList()
        return directory.listFiles { file -> file.isFile && file.extension in setOf("json", "tmp") }?.toList()
            ?: throw IOException("Unable to read transcript capture directory")
    }

    private fun deleteFile(file: File) {
        if (!file.delete() && file.exists()) throw IOException("Unable to delete transcript capture")
    }
}
