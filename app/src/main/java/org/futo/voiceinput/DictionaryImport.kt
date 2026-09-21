package org.futo.voiceinput

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer

/** Plain-text import only; existing vocabulary is never rewritten by an import. */
internal object DictionaryImport {
    const val MAX_BYTES = 1024 * 1024
    private val separators = Regex("[,\\r\\n]+")

    data class Preview(val additions: List<String>, val duplicates: Int, val invalid: List<String>) {
        fun appendTo(existing: String): String =
            if (additions.isEmpty()) existing else existing + (if (existing.isEmpty() || existing.endsWith('\n')) "" else "\n") +
                additions.joinToString("\n")
    }

    fun preview(existing: String, incoming: String): Preview {
        fun canonical(entry: String) = entry.split("=>").joinToString(" => ") { it.trim() }
        val known = existing.split(separators).filter { it.isNotBlank() }.map(::canonical).toMutableSet()
        val additions = mutableListOf<String>()
        val invalid = mutableListOf<String>()
        var duplicates = 0
        incoming.removePrefix("\uFEFF").split(separators).map(String::trim).filter(String::isNotEmpty).forEach { entry ->
            val parts = entry.split("=>")
            if (parts.size > 2 || parts.any { it.isBlank() } ||
                entry.any { it.isISOControl() && it != '\t' }) {
                invalid += entry
            } else {
                val normalized = canonical(entry)
                if (known.add(normalized)) additions += normalized else duplicates++
            }
        }
        return Preview(additions, duplicates, invalid)
    }

    fun read(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
            require(output.size() <= MAX_BYTES) { "Dictionary file exceeds 1 MiB" }
        }
        return Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(output.toByteArray())).toString()
    }
}
