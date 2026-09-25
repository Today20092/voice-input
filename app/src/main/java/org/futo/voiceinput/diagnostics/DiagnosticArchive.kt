package org.futo.voiceinput.diagnostics

import kotlinx.serialization.encodeToString
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DiagnosticArchive {
    const val CACHE_BYTES = 2L * 1024 * 1024

    fun purgePrepared(directory: File, nowMs: Long = System.currentTimeMillis()) {
        val files = directory.listFiles().orEmpty().filter { it.isFile }.sortedByDescending { it.name }
        var bytes = 0L
        var count = 0
        for (file in files) {
            // lastModified is the oldest evidence date, set at creation below, not the ZIP creation time.
            if (file.lastModified() < nowMs - DiagnosticStore.RETENTION_MS ||
                count >= 3 || bytes + file.length() > CACHE_BYTES) file.delete()
            else { bytes += file.length(); count++ }
        }
    }

    fun write(
        output: File,
        snapshot: DiagnosticSnapshot,
        environmentJson: String,
        settingsJson: String,
        happened: String,
        expected: String,
        s1Reports: Map<String, String>,
        droppedEvents: Long
    ) {
        var retained = snapshot
        var omitted = 0L
        do {
            writeArchive(output, retained, environmentJson, settingsJson, happened, expected, s1Reports, droppedEvents + omitted)
            if (output.length() <= CACHE_BYTES) break
            check(retained.records.isNotEmpty()) { "Diagnostic metadata exceeds export budget" }
            val remove = maxOf(1, retained.records.size / 4)
            omitted += remove
            retained = retained.copy(records = retained.records.drop(remove))
        } while (true)
        val s1Times = s1Reports.values.flatMap { contents -> contents.lineSequence().mapNotNull { line ->
            runCatching {
                val obj = DiagnosticStore.json.parseToJsonElement(line) as? kotlinx.serialization.json.JsonObject
                listOf("capturedAtEpochMs", "recordedAtEpochMs").mapNotNull { key ->
                    (obj?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull()
                }.minOrNull()
            }.getOrNull()
        }.toList() }
        val oldest = (retained.records.map { it.timestampMs } + s1Times).minOrNull() ?: System.currentTimeMillis()
        check(output.setLastModified(oldest)) { "Unable to set report expiry" }
    }

    private fun writeArchive(output: File, snapshot: DiagnosticSnapshot, environmentJson: String,
        settingsJson: String, happened: String, expected: String, s1Reports: Map<String, String>, droppedEvents: Long) {
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("README.txt", "Voice Input standard bug report (schema 1).\n" +
                "Technical records exclude audio, transcripts, vocabulary, clipboard/surrounding text, " +
                "receiving-app names, raw Logcat, and exception messages.\n" +
                "notes.txt contains the reporter's own text, if supplied. Review it before posting publicly.\n" +
                "Crash evidence is best effort. An unfinished session does not prove a crash or hang.\n" +
                "DELIVERY_ACCEPTED means Android accepted the handoff, not that text was displayed.\n" +
                "Durations use a monotonic clock; timestampMs is wall-clock time. Memory is sampled, not a peak guarantee.\n" +
                "S1-mini reports are included when available, with free-form native messages omitted.\n" +
                "Dropped events counts writer drops and oldest events omitted to fit the export budget.\n")
            entry("summary.txt", summary(snapshot, droppedEvents))
            entry("environment.json", environmentJson)
            entry("settings.json", settingsJson)
            entry("events.jsonl", snapshot.records.joinToString("\n", postfix = "\n") {
                DiagnosticStore.json.encodeToString(it)
            })
            if (happened.isNotBlank() || expected.isNotBlank()) {
                entry("notes.txt", "What happened:\n${happened.take(4000)}\n\nExpected behavior:\n${expected.take(4000)}\n")
            }
            // An explicit allowlist prevents transcript archives or arbitrary app files joining this ZIP.
            for (name in listOf("runs.jsonl", "benchmark.json")) {
                s1Reports[name]?.let { contents ->
                    val safe = if (name == "runs.jsonl") {
                        contents.lineSequence().mapNotNull(StandardS1Reports::project).joinToString("\n")
                    } else StandardS1Reports.project(contents)
                    safe?.let { entry("s1/$name", it) }
                }
            }
        }
    }

    private fun summary(snapshot: DiagnosticSnapshot, dropped: Long) = buildString {
        appendLine("Voice Input diagnostic summary")
        appendLine("Records: ${snapshot.records.size}")
        appendLine("Unreadable records skipped: ${snapshot.unreadableRecords}")
        appendLine("Dropped events: $dropped")
        appendLine("No dictated text or audio is included.")
        appendLine()
        appendLine("Event counts:")
        snapshot.records.groupingBy { it.event }.eachCount().forEach { (event, count) ->
            appendLine("  $event: $count")
        }
        val sessions = snapshot.records.filter { it.sessionId != null }.groupBy { it.sessionId }
        val terminal = setOf(DiagnosticEvent.SESSION_CANCELLED, DiagnosticEvent.SESSION_RESET,
            DiagnosticEvent.SESSION_FAILED, DiagnosticEvent.DELIVERY_ACCEPTED, DiagnosticEvent.DELIVERY_REJECTED,
            DiagnosticEvent.DOWNLOAD_FINISHED, DiagnosticEvent.DOWNLOAD_CANCELLED)
        appendLine()
        appendLine("Recent sessions (up to 25; incomplete history can reflect retention or interruption):")
        sessions.entries.toList().takeLast(25).forEach { (id, records) ->
            appendLine("  $id model=${records.firstNotNullOfOrNull { it.model } ?: "unknown"} " +
                "last=${records.last().event} terminal=${records.any { it.event in terminal }}")
            records.filter { it.metrics.containsKey(DiagnosticMetric.DURATION_MS) }.forEach {
                appendLine("    ${it.event}: ${it.metrics[DiagnosticMetric.DURATION_MS]} ms")
            }
            val stop = records.firstOrNull { it.event == DiagnosticEvent.RECORDING_STOPPED }
                ?.metrics?.get(DiagnosticMetric.ELAPSED_MS)
            val delivered = records.lastOrNull { it.event == DiagnosticEvent.DELIVERY_ACCEPTED }
                ?.metrics?.get(DiagnosticMetric.ELAPSED_MS)
            if (stop != null && delivered != null) appendLine("    Stop to delivery: ${delivered - stop} ms")
        }
        appendLine()
        appendLine("Recent failures and exits (up to 20):")
        snapshot.records.filter { it.failure != null || it.event.name.endsWith("FAILED") ||
            it.event == DiagnosticEvent.PROCESS_EXIT || it.event == DiagnosticEvent.DELIVERY_REJECTED
        }.takeLast(20).forEach {
            appendLine("  ${it.timestampMs} ${it.event} ${it.failure?.kind.orEmpty()} ${it.metrics}")
            it.failure?.frames?.forEach { frame -> appendLine("    $frame") }
        }
    }
}
