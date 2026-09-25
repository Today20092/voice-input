package org.futo.voiceinput.history

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** App-private, unbuffered 16 kHz mono PCM. Even an interrupted file can be read. */
class AudioHistoryStore(private val root: File) {
    data class Entry(
        val id: String,
        val createdAt: Long,
        val samples: Long,
        val busy: Boolean,
        val preview: String? = null
    ) {
        val durationSeconds get() = samples / 16000.0
    }

    data class ClearResult(val deleted: Int, val inUse: Int, val failed: Int)

    fun begin(now: Long = System.currentTimeMillis()): Capture = synchronized(lock) {
        check(root.isDirectory || root.mkdirs()) { "Cannot create audio history" }
        val id = "$now-${UUID.randomUUID()}"
        val output = FileOutputStream(audio(id))
        active.add(audio(id).absolutePath)
        Capture(id, output)
    }

    inner class Capture internal constructor(val id: String, private val output: FileOutputStream) : Closeable {
        private var closed = false
        private var released = false

        @Synchronized
        fun append(samples: ShortArray, count: Int) {
            check(!closed)
            require(count in 0..samples.size)
            val bytes = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
            repeat(count) { bytes.putShort(samples[it]) }
            output.write(bytes.array())
        }

        @Synchronized
        fun finishWriting() {
            if (closed) return
            closed = true
            try { output.fd.sync() } finally { output.close() }
        }

        override fun close() {
            try {
                finishWriting()
            } finally {
                synchronized(lock) {
                    if (!released) {
                        released = true
                        active.remove(audio(id).absolutePath)
                        if (audio(id).length() == 0L) delete(id)
                    }
                }
            }
        }
    }

    fun entries(): List<Entry> = synchronized(lock) {
        root.listFiles().orEmpty().mapNotNull { file ->
            if (file.extension != "pcm") return@mapNotNull null
            val createdAt = file.name.substringBefore('-').toLongOrNull() ?: return@mapNotNull null
            if (file.length() < 2L) return@mapNotNull null
            Entry(file.nameWithoutExtension, createdAt, file.length() / 2,
                file.absolutePath in active, preview(file.nameWithoutExtension))
        }.sortedByDescending { it.createdAt }
    }

    private fun preview(id: String): String? = try {
        transcriptFile(id).takeIf { it.isFile }?.bufferedReader()?.use { reader ->
            // List rows never need to load a complete, potentially long transcript.
            val buffer = CharArray(241)
            val count = reader.read(buffer)
            when {
                count <= 0 -> null
                count > 240 -> String(buffer, 0, 240) + "…"
                else -> String(buffer, 0, count)
            }?.takeIf { it.isNotBlank() }
        }
    } catch (_: IOException) {
        // A failed preview must not hide the rest of the history. Opening the entry
        // still reports transcript read errors through the normal detail flow.
        null
    }

    /** Clear a snapshot of saved recordings, preserving active capture and replay. */
    fun clear(): ClearResult = synchronized(lock) {
        var deleted = 0
        var inUse = 0
        var failed = 0
        val files = if (root.exists()) checkNotNull(root.listFiles()) { "Cannot read audio history" }
            else emptyArray()
        files.filter { it.extension == "pcm" }.forEach { file ->
            try {
                if (delete(file.nameWithoutExtension)) deleted++ else inUse++
            } catch (_: Exception) {
                failed++
            }
        }
        ClearResult(deleted, inUse, failed)
    }

    fun purge(retentionHours: Int, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        val cutoff = now - retentionHours.coerceIn(1, 720) * 3_600_000L
        root.listFiles().orEmpty().filter { it.extension == "pcm" }.forEach { file ->
            val createdAt = file.name.substringBefore('-').toLongOrNull() ?: return@forEach
            if (createdAt <= cutoff || file.length() == 0L) delete(file.nameWithoutExtension)
        }
    }

    fun delete(id: String): Boolean = synchronized(lock) {
        val audio = audio(id)
        if (audio.absolutePath in active) return false
        // Keep the audio as the expiry anchor until all related text has been deleted.
        listOf(transcriptFile(id), File(root, "$id.txt.tmp"), audio).forEach {
            check(!it.exists() || it.delete()) { "Cannot delete recording" }
        }
        true
    }

    fun transcript(id: String): String? = synchronized(lock) {
        transcriptFile(id).takeIf { it.isFile }?.readText()
    }

    fun saveTranscript(id: String, text: String) = synchronized(lock) {
        // An expired or explicitly deleted recording must not be resurrected.
        if (!audio(id).exists()) return@synchronized
        val target = transcriptFile(id)
        val temporary = File(root, "$id.txt.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE)
    }

    private fun pin(id: String): Closeable = synchronized(lock) {
        val file = audio(id)
        check(file.isFile) { "Recording is no longer available" }
        check(active.add(file.absolutePath)) { "Recording is in use" }
        var released = false
        Closeable {
            synchronized(lock) {
                if (!released) {
                    released = true
                    active.remove(file.absolutePath)
                }
            }
        }
    }

    /** Protect audio for the entire attempt and replace text only after successful completion. */
    suspend fun retranscribe(id: String, transcribe: suspend (FloatArray) -> String): String =
        pin(id).use {
            val text = transcribe(readSamples(id))
            currentCoroutineContext().ensureActive()
            saveTranscript(id, text)
            text
        }

    private fun readSamples(id: String): FloatArray {
        val file = audio(id)
        val count = file.length() / 2
        require(count in 1..Int.MAX_VALUE.toLong()) { "Recording is empty or too large" }
        val samples = FloatArray(count.toInt())
        file.inputStream().buffered().use { input ->
            for (index in samples.indices) {
                val low = input.read()
                val high = input.read()
                check(low >= 0 && high >= 0) { "Recording was truncated" }
                samples[index] = ((high shl 8) or low).toShort().toFloat() / Short.MAX_VALUE
            }
        }
        return samples
    }

    private fun audio(id: String): File {
        require(id.matches(Regex("[0-9]+-[0-9a-f-]+"))) { "Invalid recording" }
        return File(root, "$id.pcm")
    }

    private fun transcriptFile(id: String): File {
        audio(id) // Validate before constructing any related path.
        return File(root, "$id.txt")
    }

    companion object {
        private val lock = Any()
        private val active = mutableSetOf<String>()
    }
}
